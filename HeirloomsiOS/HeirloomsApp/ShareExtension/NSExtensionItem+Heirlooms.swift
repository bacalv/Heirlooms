import Foundation
import UniformTypeIdentifiers
import UIKit

/// Helpers for extracting image and movie data from an `NSExtensionItem` attachment.
///
/// The system share sheet delivers items as `NSItemProvider` attachments. This
/// extension provides async helpers that load either a `Data` (for images) or a
/// file `URL` (for movies) so the rest of the Share Extension can process them
/// without dealing with the completion-handler API of `NSItemProvider`.
extension NSItemProvider {

    // MARK: - Image loading

    /// Loads image bytes if this provider conforms to `public.image`.
    ///
    /// Returns the raw data (JPEG, PNG, HEIC, etc.) without any transcoding so
    /// the E2EE envelope wraps the original bytes byte-for-byte.
    ///
    /// - Returns: The image `Data` and a MIME type string on success, `nil` otherwise.
    func loadImageData() async -> (data: Data, mimeType: String)? {
        guard hasItemConformingToTypeIdentifier(UTType.image.identifier) else { return nil }

        return await withCheckedContinuation { continuation in
            loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, error in
                guard let data, error == nil else {
                    continuation.resume(returning: nil)
                    return
                }
                // Sniff magic bytes to determine MIME type.
                let mime = Self.mimeType(for: data)
                continuation.resume(returning: (data, mime))
            }
        }
    }

    /// Loads a movie as a temporary file URL if this provider conforms to `public.movie`.
    ///
    /// The system copies the video into a temp location. The caller is responsible
    /// for deleting the file after upload.
    ///
    /// - Returns: The file URL and MIME type on success, `nil` otherwise.
    func loadMovieURL() async -> (url: URL, mimeType: String)? {
        guard hasItemConformingToTypeIdentifier(UTType.movie.identifier) else { return nil }

        return await withCheckedContinuation { continuation in
            loadFileRepresentation(forTypeIdentifier: UTType.movie.identifier) { url, error in
                guard let url, error == nil else {
                    continuation.resume(returning: nil)
                    return
                }
                // The URL becomes invalid after this handler returns — copy to a temp file.
                let dest = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString)
                    .appendingPathExtension(url.pathExtension.isEmpty ? "mov" : url.pathExtension)
                do {
                    try FileManager.default.copyItem(at: url, to: dest)
                    let mime = "video/\(dest.pathExtension.lowercased())"
                    continuation.resume(returning: (dest, mime))
                } catch {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    // MARK: - MIME sniffing

    /// Infers a MIME type from the first bytes of image data (magic number detection).
    private static func mimeType(for data: Data) -> String {
        guard !data.isEmpty else { return "application/octet-stream" }
        switch data[0] {
        case 0xFF: return "image/jpeg"
        case 0x89: return "image/png"
        case 0x47: return "image/gif"
        case 0x49, 0x4D: return "image/tiff"
        default:
            // Check for HEIC/HEIF ftyp box (bytes 4–7 == "ftyp").
            if data.count > 8,
               let ftyp = String(data: data[4..<8], encoding: .ascii), ftyp == "ftyp" {
                return "image/heic"
            }
            return "image/jpeg" // safe default
        }
    }
}
