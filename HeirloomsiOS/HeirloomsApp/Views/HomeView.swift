import SwiftUI
import PhotosUI
import AVFoundation
import LocalAuthentication
import UniformTypeIdentifiers
import HeirloomsCore

// MARK: - MovieTransferable

/// Transferable wrapper that surfaces the file URL of a video picked from the
/// Photos library, enabling AVAssetExportSession to stream it from disk without
/// loading the full video bytes into memory.
struct MovieTransferable: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let copy = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension("mov")
            try FileManager.default.copyItem(at: received.file, to: copy)
            return MovieTransferable(url: copy)
        }
    }
}

/// Main screen — two-tab grid of shared plot items.
///
/// "Shared with you" = items uploaded by other users.
/// "You shared"      = items uploaded by the current user (stored in Keychain as userId).
struct HomeView: View {

    let plotId: String

    @EnvironmentObject private var appState: AppState
    @State private var items: [PlotItem] = []
    @State private var selectedTab = 0
    @State private var isLoading = false
    @State private var selectedItem: PlotItem?
    @State private var photosPickerItems: [PhotosPickerItem] = []
    @State private var isUploading = false

    private let api = HeirloomsAPI()

    // The current user's ID (needed to split "Shared with you" vs "You shared").
    private var myUserId: String? {
        try? KeychainManager.getUserId()
    }

    var sharedWithYou: [PlotItem] {
        items.filter { $0.uploaderUserId != nil && $0.uploaderUserId != myUserId }
    }

    var youShared: [PlotItem] {
        items.filter { $0.uploaderUserId == myUserId }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab picker.
                Picker("View", selection: $selectedTab) {
                    Text("Shared with you").tag(0)
                    Text("You shared").tag(1)
                }
                .pickerStyle(.segmented)
                .padding()

                // Grid.
                if isLoading && items.isEmpty {
                    Spacer()
                    ProgressView()
                    Spacer()
                } else {
                    let displayItems = selectedTab == 0 ? sharedWithYou : youShared
                    if displayItems.isEmpty {
                        Spacer()
                        Text(emptyStateText)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding()
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVGrid(
                                columns: [
                                    GridItem(.flexible(), spacing: 2),
                                    GridItem(.flexible(), spacing: 2),
                                    GridItem(.flexible(), spacing: 2),
                                ],
                                spacing: 2
                            ) {
                                ForEach(displayItems) { item in
                                    ThumbnailCell(item: item, plotId: plotId)
                                        .onTapGesture { selectedItem = item }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Heirlooms")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(destination: SettingsView()) {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                // Plant button (FAB).
                PhotosPicker(
                    selection: $photosPickerItems,
                    maxSelectionCount: 50,
                    matching: .any(of: [.images, .videos])
                ) {
                    Image(systemName: "plus")
                        .font(.title2.bold())
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(.blue)
                        .clipShape(Circle())
                        .shadow(radius: 4)
                        .padding()
                }
                .onChange(of: photosPickerItems) { _, newItems in
                    guard !newItems.isEmpty else { return }
                    Task { await uploadItems(newItems) }
                }
                .disabled(isUploading)
            }
        }
        .sheet(item: $selectedItem) { item in
            FullScreenMediaView(item: item, plotId: plotId)
        }
        .task { await loadItems() }
        .refreshable { await loadItems() }
    }

    // MARK: - Load

    private func loadItems() async {
        isLoading = true
        defer { isLoading = false }
        do {
            items = try await api.listPlotItems(plotId: plotId)
        } catch {
            // In production, log to Diagnostics.
            print("[HomeView] Failed to load items: \(error.localizedDescription)")
        }
    }

    // MARK: - Upload

    private func uploadItems(_ pickerItems: [PhotosPickerItem]) async {
        isUploading = true
        defer { isUploading = false }

        guard let plotKeyData = try? KeychainManager.getPlotKey(),
              plotKeyData.count == 32 else {
            print("[HomeView] Plot key not found")
            return
        }
        let plotKey = SymmetricKey(data: plotKeyData)

        for pickerItem in pickerItems {
            await uploadSingleItem(pickerItem, plotKey: plotKey)
        }

        // Reload after all uploads complete.
        await loadItems()
        photosPickerItems = []
    }

    private func uploadSingleItem(_ pickerItem: PhotosPickerItem, plotKey: SymmetricKey) async {
        let contentType = pickerItem.supportedContentTypes.first?.preferredMIMEType ?? "application/octet-stream"
        let isVideo = contentType.hasPrefix("video/")

        do {
            // --- Acquire plaintext bytes (stream video from disk; load images in memory) ---
            let plaintextURL: URL
            if isVideo {
                // Load video as a file URL using AVAsset export to avoid loading full bytes into RAM.
                guard let avAsset = try? await loadVideoAsset(from: pickerItem) else {
                    print("[HomeView] Failed to load video asset")
                    return
                }
                let exportURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString)
                    .appendingPathExtension("mp4")
                guard let exportedURL = try? await exportVideo(asset: avAsset, to: exportURL) else {
                    print("[HomeView] Video export failed")
                    return
                }
                plaintextURL = exportedURL
            } else {
                guard let data = try? await pickerItem.loadTransferable(type: Data.self) else {
                    print("[HomeView] Failed to load image data")
                    return
                }
                let tempURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString)
                try data.write(to: tempURL)
                plaintextURL = tempURL
            }
            defer { try? FileManager.default.removeItem(at: plaintextURL) }

            let plaintextData = try Data(contentsOf: plaintextURL)

            // --- Generate DEKs ---
            let contentDek = EnvelopeCrypto.generateDEK()
            let thumbDek   = EnvelopeCrypto.generateDEK()

            // --- Encrypt content ---
            let encryptedContent = try EnvelopeCrypto.encryptContent(plaintext: plaintextData, dek: contentDek)

            // --- Generate & encrypt thumbnail (images only) ---
            var encryptedThumb: Data?
            if !isVideo, let uiImage = UIImage(data: plaintextData),
               let resized = resizeThumbnail(uiImage, maxSide: 320),
               let jpegData = resized.jpegData(compressionQuality: 0.7) {
                encryptedThumb = try EnvelopeCrypto.encryptContent(plaintext: jpegData, dek: thumbDek)
            }

            // --- Wrap DEKs under plot key ---
            let plotAlgID = "plot-aes256gcm-v1"
            let contentDekBytes = contentDek.withUnsafeBytes { Data($0) }
            let wrappedContentDek = try EnvelopeCrypto.wrapSymmetric(
                plaintext: contentDekBytes,
                wrappingKey: plotKey,
                algorithmID: plotAlgID
            )
            let thumbDekBytes = thumbDek.withUnsafeBytes { Data($0) }
            let wrappedThumbDek = try EnvelopeCrypto.wrapSymmetric(
                plaintext: thumbDekBytes,
                wrappingKey: plotKey,
                algorithmID: plotAlgID
            )

            // --- Initiate upload ---
            let ticket = try await api.initiateUpload(
                filename: "upload",
                contentType: contentType,
                plotId: plotId
            )

            // --- Write encrypted content to temp file and PUT to presigned URL ---
            let encryptedContentURL = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
            try encryptedContent.write(to: encryptedContentURL)
            defer { try? FileManager.default.removeItem(at: encryptedContentURL) }

            try await putToPresignedURL(localURL: encryptedContentURL, uploadURL: ticket.uploadUrl)

            // --- PUT thumbnail if available ---
            if let thumb = encryptedThumb,
               let thumbUploadURL = ticket.thumbnailUploadUrl {
                let encryptedThumbURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString)
                try thumb.write(to: encryptedThumbURL)
                defer { try? FileManager.default.removeItem(at: encryptedThumbURL) }
                try await putToPresignedURL(localURL: encryptedThumbURL, uploadURL: thumbUploadURL)
            }

            // --- Confirm upload ---
            try await api.confirmUpload(
                storageKey: ticket.storageKey,
                thumbnailStorageKey: encryptedThumb != nil ? ticket.thumbnailStorageKey : nil,
                mimeType: contentType,
                fileSize: Int64(encryptedContent.count),
                wrappedDEK: wrappedContentDek,
                wrappedThumbDEK: encryptedThumb != nil ? wrappedThumbDek : nil,
                dekFormat: plotAlgID
            )
        } catch {
            print("[HomeView] Upload failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Upload helpers

    /// Streams encrypted bytes from a local file directly to a presigned GCS URL via PUT.
    private func putToPresignedURL(localURL: URL, uploadURL: String) async throws {
        guard let url = URL(string: uploadURL) else {
            throw HeirloomsError.networkError(0, "Invalid presigned URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        let (_, response) = try await URLSession.shared.upload(for: request, fromFile: localURL)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw HeirloomsError.networkError(code, "PUT to presigned URL failed")
        }
    }

    /// Loads a video asset from a PhotosPickerItem using AVFoundation.
    private func loadVideoAsset(from pickerItem: PhotosPickerItem) async throws -> AVAsset? {
        guard let movieTransferable = try? await pickerItem.loadTransferable(type: MovieTransferable.self) else {
            return nil
        }
        return AVURLAsset(url: movieTransferable.url)
    }

    /// Exports a video asset to a temp file at `destination` in MP4 format.
    private func exportVideo(asset: AVAsset, to destination: URL) async throws -> URL? {
        guard let exportSession = AVAssetExportSession(
            asset: asset,
            presetName: AVAssetExportPresetPassthrough
        ) else { return nil }

        exportSession.outputURL = destination
        exportSession.outputFileType = .mp4
        exportSession.shouldOptimizeForNetworkUse = true

        await exportSession.export()

        guard exportSession.status == .completed else {
            print("[HomeView] Export failed: \(exportSession.error?.localizedDescription ?? "unknown")")
            return nil
        }
        return destination
    }

    /// Scales a UIImage so the longer side does not exceed `maxSide`.
    private func resizeThumbnail(_ image: UIImage, maxSide: CGFloat) -> UIImage? {
        let size = image.size
        let scale = min(maxSide / size.width, maxSide / size.height, 1.0)
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    private var emptyStateText: String {
        selectedTab == 0
            ? "Nothing shared with you yet.\nYour family member will share photos here."
            : "You haven't shared anything yet.\nTap + to share a photo or video."
    }
}

// MARK: - ThumbnailCell

/// Lazy-loading encrypted thumbnail cell.
struct ThumbnailCell: View {

    let item: PlotItem
    let plotId: String

    @State private var thumbnail: UIImage?

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.gray.opacity(0.15)
                if let thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                        .clipped()
                } else {
                    Image(systemName: item.contentType.hasPrefix("video") ? "film" : "photo")
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: geo.size.width, height: geo.size.width)
        }
        .aspectRatio(1, contentMode: .fill)
        .task { await loadThumbnail() }
    }

    private func loadThumbnail() async {
        guard let thumbStorageKey = item.thumbnailStorageKey,
              !thumbStorageKey.isEmpty,
              let wrappedDekB64 = item.wrappedThumbnailDek,
              let wrappedDekData = Data(base64Encoded: wrappedDekB64),
              let plotKeyData = try? KeychainManager.getPlotKey(),
              plotKeyData.count == 32
        else { return }

        let plotKey = SymmetricKey(data: plotKeyData)
        let api = HeirloomsAPI()

        do {
            let encryptedThumb = try await api.fetchThumbnail(uploadId: item.id)

            // Unwrap the thumbnail DEK using the format declared on the item.
            let dekFormat = item.thumbnailDekFormat ?? EnvelopeCrypto.algMasterSymmetric
            let thumbDek: SymmetricKey

            switch dekFormat {
            case "plot-aes256gcm-v1", EnvelopeCrypto.algSymmetric, EnvelopeCrypto.algMasterSymmetric:
                // Symmetric wrap: DEK is wrapped under the plot key.
                let dekBytes = try EnvelopeCrypto.unwrapSymmetric(
                    envelope: wrappedDekData,
                    unwrappingKey: plotKey
                )
                thumbDek = SymmetricKey(data: dekBytes)

            case EnvelopeCrypto.algAsymmetric:
                // Asymmetric wrap: DEK is wrapped to our sharing keypair.
                let privateKey = try KeychainManager.getSharingPrivateKey()
                thumbDek = try EnvelopeCrypto.unwrapDEK(
                    wrappedKey: wrappedDekData,
                    recipientPrivateKey: privateKey
                )

            default:
                // Unknown format — try symmetric as a fallback.
                let dekBytes = try EnvelopeCrypto.unwrapSymmetric(
                    envelope: wrappedDekData,
                    unwrappingKey: plotKey
                )
                thumbDek = SymmetricKey(data: dekBytes)
            }

            let plaintextThumb = try EnvelopeCrypto.decryptContent(envelope: encryptedThumb, dek: thumbDek)
            thumbnail = UIImage(data: plaintextThumb)
        } catch {
            // Silently fail — thumbnail stays as placeholder.
        }
    }
}

// MARK: - BiometricGateView (SEC-015)

/// Full-screen biometric prompt. Uses LAContext to evaluate biometric or device credential.
/// If no credential is enrolled, bypasses the gate by calling `onAuthenticated` immediately.
struct BiometricGateView: View {
    let onAuthenticated: () -> Void

    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "faceid")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)
            Text("Biometric required")
                .font(.title.bold())
            Text("Authenticate to open your vault.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                Button("Try again") {
                    authenticate()
                }
                .buttonStyle(.borderedProminent)
            }
            Spacer()
        }
        .task { authenticate() }
    }

    private func authenticate() {
        let context = LAContext()
        var policyError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &policyError) else {
            // No biometric and no passcode — bypass gate.
            onAuthenticated()
            return
        }
        context.evaluatePolicy(
            .deviceOwnerAuthentication,
            localizedReason: "Open your Heirlooms vault"
        ) { success, error in
            DispatchQueue.main.async {
                if success {
                    onAuthenticated()
                } else {
                    errorMessage = error?.localizedDescription ?? "Authentication failed."
                }
            }
        }
    }
}

// MARK: - SettingsView stub

struct SettingsView: View {
    @EnvironmentObject private var appState: AppState
    @State private var requireBiometric: Bool = false
    @State private var biometricWorking: Bool = false

    var body: some View {
        List {
            Section("Account") {
                LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "–")
                Button("API Key Reset") {
                    // TODO: API key reset semantics TBD (see brief).
                }
                // SEC-015: biometric gate toggle.
                Toggle("Require biometric to open vault", isOn: Binding(
                    get: { requireBiometric },
                    set: { newValue in
                        guard !biometricWorking else { return }
                        biometricWorking = true
                        Task {
                            do {
                                let api = HeirloomsAPI()
                                let updated = try await api.patchAccount(requireBiometric: newValue)
                                await MainActor.run {
                                    requireBiometric = updated.requireBiometric
                                    appState.requireBiometric = updated.requireBiometric
                                    UserDefaults.standard.set(updated.requireBiometric, forKey: "require_biometric")
                                    biometricWorking = false
                                }
                            } catch {
                                await MainActor.run { biometricWorking = false }
                            }
                        }
                    }
                ))
                .disabled(biometricWorking)
            }
            Section("Devices") {
                NavigationLink("Devices & Access") {
                    PairingView()
                }
            }
            Section("Diagnostics") {
                NavigationLink("Diagnostics Log") {
                    DiagnosticsView()
                }
            }
            Section {
                Button("Reset shared album", role: .destructive) {
                    KeychainManager.deletePlotKey()
                    KeychainManager.deletePlotId()
                    appState.refreshPhase()
                }
            }
        }
        .navigationTitle("Settings")
        .onAppear {
            requireBiometric = appState.requireBiometric
        }
    }
}

// MARK: - Stub views

struct PairingView: View {
    @State private var isPairing = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "laptopcomputer.and.iphone")
                .font(.system(size: 56))
                .foregroundStyle(.secondary)
            Text("Open Heirlooms on the web and tap \"Link device\" to show a pairing QR code.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 32)
            Button("Scan pairing code") {
                isPairing = true
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            Spacer()
        }
        .navigationTitle("Devices & Access")
        .sheet(isPresented: $isPairing) {
            ActivateView(scanMode: .webPairing)
        }
    }
}

struct DiagnosticsView: View {
    var body: some View {
        Text("Diagnostics log — TODO")
            .navigationTitle("Diagnostics")
    }
}
