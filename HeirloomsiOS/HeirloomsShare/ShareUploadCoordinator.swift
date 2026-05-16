import Foundation
import CryptoKit
import UIKit
import HeirloomsCore

/// Orchestrates the encrypt-and-upload pipeline inside the Share Extension.
///
/// Matches the upload logic in the main app's `HomeView.uploadSingleItem` but
/// works entirely with `Data` (images) or file `URL` (movies) already retrieved
/// from the extension item, rather than `PhotosPickerItem`.
///
/// Progress is reported via `@Published` properties that the SwiftUI
/// `PlotPickerView` observes.  All mutations happen on the main actor.
@MainActor
final class ShareUploadCoordinator: ObservableObject {

    // MARK: - Published state (drives the SwiftUI progress view)

    @Published var progress: Double = 0.0
    @Published var isUploading = false
    @Published var uploadError: String?
    @Published var isComplete = false

    // MARK: - Private

    private let api = HeirloomsAPI()

    // MARK: - Entry points

    /// Encrypts and uploads a single image.
    ///
    /// - Parameters:
    ///   - imageData: Raw image bytes (JPEG, PNG, HEIC, etc.).
    ///   - mimeType: MIME type of the data, e.g. `"image/jpeg"`.
    ///   - plotId: The target shared plot UUID.
    ///   - plotKey: 32-byte AES-256 plot key retrieved from the Keychain.
    func uploadImage(
        imageData: Data,
        mimeType: String,
        plotId: String,
        plotKey: SymmetricKey
    ) async {
        await performUpload {
            try await self.encryptAndUploadImage(
                imageData: imageData,
                mimeType: mimeType,
                plotId: plotId,
                plotKey: plotKey
            )
        }
    }

    /// Encrypts and uploads a video from a local file URL.
    ///
    /// The file is read into memory for encryption. For very large videos this
    /// may be memory-intensive; a future improvement could stream-encrypt chunk
    /// by chunk, but within the Share Extension's memory limit (120 MB) this is
    /// acceptable for typical short clips.
    ///
    /// - Parameters:
    ///   - fileURL: Path to the video on disk (a temp file provided by the system).
    ///   - mimeType: MIME type, e.g. `"video/mov"` or `"video/mp4"`.
    ///   - plotId: The target shared plot UUID.
    ///   - plotKey: 32-byte AES-256 plot key retrieved from the Keychain.
    func uploadVideo(
        fileURL: URL,
        mimeType: String,
        plotId: String,
        plotKey: SymmetricKey
    ) async {
        defer { try? FileManager.default.removeItem(at: fileURL) }
        await performUpload {
            try await self.encryptAndUploadVideo(
                fileURL: fileURL,
                mimeType: mimeType,
                plotId: plotId,
                plotKey: plotKey
            )
        }
    }

    // MARK: - Private helpers

    private func performUpload(work: @escaping () async throws -> Void) async {
        isUploading = true
        uploadError = nil
        progress = 0.05      // show immediate feedback
        defer { isUploading = false }

        do {
            try await work()
            progress = 1.0
            isComplete = true
        } catch {
            uploadError = error.localizedDescription
        }
    }

    private func encryptAndUploadImage(
        imageData: Data,
        mimeType: String,
        plotId: String,
        plotKey: SymmetricKey
    ) async throws {
        // --- Generate DEKs ---
        let contentDek = EnvelopeCrypto.generateDEK()
        let thumbDek   = EnvelopeCrypto.generateDEK()
        progress = 0.10

        // --- Encrypt content ---
        let encryptedContent = try EnvelopeCrypto.encryptContent(plaintext: imageData, dek: contentDek)
        progress = 0.25

        // --- Generate thumbnail and encrypt ---
        var encryptedThumb: Data?
        if let uiImage = UIImage(data: imageData),
           let resized = resizeThumbnail(uiImage, maxSide: 320),
           let jpegData = resized.jpegData(compressionQuality: 0.7) {
            encryptedThumb = try EnvelopeCrypto.encryptContent(plaintext: jpegData, dek: thumbDek)
        }
        progress = 0.35

        // --- Wrap DEKs under plot key ---
        let plotAlgID = EnvelopeCrypto.algPlotSymmetric
        var contentDekBytes = contentDek.withUnsafeBytes { Data($0) }
        defer { contentDekBytes.resetBytes(in: 0..<contentDekBytes.count) }
        let wrappedContentDek = try EnvelopeCrypto.wrapSymmetric(
            plaintext: contentDekBytes,
            wrappingKey: plotKey,
            algorithmID: plotAlgID
        )
        var thumbDekBytes = thumbDek.withUnsafeBytes { Data($0) }
        defer { thumbDekBytes.resetBytes(in: 0..<thumbDekBytes.count) }
        let wrappedThumbDek = try EnvelopeCrypto.wrapSymmetric(
            plaintext: thumbDekBytes,
            wrappingKey: plotKey,
            algorithmID: plotAlgID
        )
        progress = 0.40

        // --- Initiate upload ---
        let ticket = try await api.initiateUpload(
            filename: "share-image",
            contentType: mimeType,
            plotId: plotId
        )
        progress = 0.50

        // --- PUT encrypted content blob ---
        let encryptedContentURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        try encryptedContent.write(to: encryptedContentURL)
        defer { try? FileManager.default.removeItem(at: encryptedContentURL) }
        try await putToPresignedURL(localURL: encryptedContentURL, uploadURL: ticket.uploadUrl)
        progress = 0.70

        // --- PUT thumbnail if available ---
        if let thumb = encryptedThumb, let thumbUploadURL = ticket.thumbnailUploadUrl {
            let encryptedThumbURL = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
            try thumb.write(to: encryptedThumbURL)
            defer { try? FileManager.default.removeItem(at: encryptedThumbURL) }
            try await putToPresignedURL(localURL: encryptedThumbURL, uploadURL: thumbUploadURL)
        }
        progress = 0.85

        // --- Confirm ---
        try await api.confirmUpload(
            storageKey: ticket.storageKey,
            thumbnailStorageKey: encryptedThumb != nil ? ticket.thumbnailStorageKey : nil,
            mimeType: mimeType,
            fileSize: Int64(encryptedContent.count),
            wrappedDEK: wrappedContentDek,
            wrappedThumbDEK: encryptedThumb != nil ? wrappedThumbDek : nil,
            dekFormat: plotAlgID
        )
        progress = 1.0
    }

    private func encryptAndUploadVideo(
        fileURL: URL,
        mimeType: String,
        plotId: String,
        plotKey: SymmetricKey
    ) async throws {
        // --- Read video bytes ---
        let videoData = try Data(contentsOf: fileURL)
        progress = 0.10

        // --- Generate DEK ---
        let contentDek = EnvelopeCrypto.generateDEK()

        // --- Encrypt ---
        let encryptedContent = try EnvelopeCrypto.encryptContent(plaintext: videoData, dek: contentDek)
        progress = 0.40

        // --- Wrap DEK under plot key ---
        let plotAlgID = EnvelopeCrypto.algPlotSymmetric
        var contentDekBytes = contentDek.withUnsafeBytes { Data($0) }
        defer { contentDekBytes.resetBytes(in: 0..<contentDekBytes.count) }
        let wrappedContentDek = try EnvelopeCrypto.wrapSymmetric(
            plaintext: contentDekBytes,
            wrappingKey: plotKey,
            algorithmID: plotAlgID
        )
        progress = 0.45

        // --- Initiate ---
        let ticket = try await api.initiateUpload(
            filename: "share-video",
            contentType: mimeType,
            plotId: plotId
        )
        progress = 0.55

        // --- PUT encrypted content blob ---
        let encryptedContentURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        try encryptedContent.write(to: encryptedContentURL)
        defer { try? FileManager.default.removeItem(at: encryptedContentURL) }
        try await putToPresignedURL(localURL: encryptedContentURL, uploadURL: ticket.uploadUrl)
        progress = 0.85

        // --- Confirm ---
        try await api.confirmUpload(
            storageKey: ticket.storageKey,
            thumbnailStorageKey: nil,
            mimeType: mimeType,
            fileSize: Int64(encryptedContent.count),
            wrappedDEK: wrappedContentDek,
            wrappedThumbDEK: nil,
            dekFormat: plotAlgID
        )
        progress = 1.0
    }

    // MARK: - Networking

    /// PUTs encrypted bytes from a local file to a presigned GCS URL.
    private func putToPresignedURL(localURL: URL, uploadURL: String) async throws {
        guard let url = URL(string: uploadURL) else {
            throw HeirloomsError.networkError(0, "Invalid presigned URL: \(uploadURL)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        let (_, response) = try await URLSession.shared.upload(for: request, fromFile: localURL)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw HeirloomsError.networkError(code, "PUT to presigned URL failed (status \(code))")
        }
    }

    // MARK: - Thumbnail generation

    private func resizeThumbnail(_ image: UIImage, maxSide: CGFloat) -> UIImage? {
        let size = image.size
        guard size.width > 0, size.height > 0 else { return nil }
        let scale = min(maxSide / size.width, maxSide / size.height, 1.0)
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
