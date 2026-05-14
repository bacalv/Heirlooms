import SwiftUI
import AVKit
import HeirloomsCore

/// Full-screen viewer for a single photo or video.
///
/// - Photos: pinch-to-zoom via `PDFKitRepresentable` or `UIScrollView` wrapper.
/// - Videos: `VideoPlayer` backed by an `AVPlayer` fed a decrypted temp file.
/// - Share: iOS share sheet with "Save Image" / "Save Video" as tier-one actions.
/// - No delete affordance — deliberate product decision (see brief).
struct FullScreenMediaView: View {

    let item: PlotItem
    let plotId: String

    @Environment(\.dismiss) private var dismiss
    @State private var media: MediaContent?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isShowingShareSheet = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                if isLoading {
                    ProgressView()
                        .tint(.white)
                } else if let errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(.yellow)
                        Text(errorMessage)
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                } else if let media {
                    switch media {
                    case .image(let uiImage):
                        ZoomableImageView(image: uiImage)
                            .ignoresSafeArea()
                    case .video(let url):
                        VideoPlayer(player: AVPlayer(url: url))
                            .ignoresSafeArea()
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .foregroundStyle(.white)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { isShowingShareSheet = true }) {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundStyle(media == nil ? .gray : .white)
                    }
                    .disabled(media == nil)
                }
            }
            // No delete button — see brief: "do not surface a delete affordance"
        }
        .task { await loadMedia() }
        .sheet(isPresented: $isShowingShareSheet) {
            if let media {
                ShareSheet(items: media.shareItems)
            }
        }
    }

    // MARK: - Media loading

    private func loadMedia() async {
        isLoading = true
        defer { isLoading = false }

        guard let plotKeyData = try? KeychainManager.getPlotKey(),
              plotKeyData.count == 32 else {
            errorMessage = "Cannot decrypt — shared album key not found."
            return
        }
        let plotKey = SymmetricKey(data: plotKeyData)
        let api = HeirloomsAPI()

        do {
            let encryptedContent = try await api.fetchItem(uploadId: item.id)

            // Unwrap content DEK using the declared format.
            guard let wrappedDekB64 = item.wrappedDek,
                  let wrappedDekData = Data(base64Encoded: wrappedDekB64) else {
                throw HeirloomsError.decodingError("Missing or invalid wrappedDek on item")
            }
            let dekFormat = item.dekFormat ?? EnvelopeCrypto.algMasterSymmetric
            let dek: SymmetricKey
            switch dekFormat {
            case "plot-aes256gcm-v1", EnvelopeCrypto.algSymmetric, EnvelopeCrypto.algMasterSymmetric:
                let dekBytes = try EnvelopeCrypto.unwrapSymmetric(
                    envelope: wrappedDekData,
                    unwrappingKey: plotKey
                )
                dek = SymmetricKey(data: dekBytes)
            case EnvelopeCrypto.algAsymmetric:
                let privateKey = try KeychainManager.getSharingPrivateKey()
                dek = try EnvelopeCrypto.unwrapDEK(
                    wrappedKey: wrappedDekData,
                    recipientPrivateKey: privateKey
                )
            default:
                let dekBytes = try EnvelopeCrypto.unwrapSymmetric(
                    envelope: wrappedDekData,
                    unwrappingKey: plotKey
                )
                dek = SymmetricKey(data: dekBytes)
            }

            // Decrypt content.
            let plaintext = try EnvelopeCrypto.decryptContent(envelope: encryptedContent, dek: dek)

            // Present.
            if item.contentType.hasPrefix("image") {
                if let uiImage = UIImage(data: plaintext) {
                    media = .image(uiImage)
                } else {
                    throw HeirloomsError.decryptionFailed("Could not decode image data")
                }
            } else if item.contentType.hasPrefix("video") {
                // Write decrypted bytes to a temp file for AVPlayer.
                let ext = mimeTypeToExtension(item.contentType)
                let tempURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(item.id)
                    .appendingPathExtension(ext)
                try plaintext.write(to: tempURL)
                media = .video(tempURL)
            } else {
                throw HeirloomsError.decodingError("Unsupported content type: \(item.contentType)")
            }
        } catch {
            errorMessage = "Could not load item: \(error.localizedDescription)"
        }
    }

    private func mimeTypeToExtension(_ mimeType: String) -> String {
        switch mimeType {
        case "video/mp4":        return "mp4"
        case "video/quicktime":  return "mov"
        case "video/x-msvideo":  return "avi"
        case "video/webm":       return "webm"
        default:                 return "mp4"
        }
    }
}

// MARK: - MediaContent

enum MediaContent {
    case image(UIImage)
    case video(URL)

    var shareItems: [Any] {
        switch self {
        case .image(let image): return [image]
        case .video(let url):   return [url]
        }
    }
}

// MARK: - ZoomableImageView

/// A UIScrollView-backed image view supporting pinch-to-zoom.
struct ZoomableImageView: UIViewRepresentable {

    let image: UIImage

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.minimumZoomScale = 1.0
        scrollView.maximumZoomScale = 5.0
        scrollView.delegate = context.coordinator
        scrollView.showsVerticalScrollIndicator = false
        scrollView.showsHorizontalScrollIndicator = false

        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(imageView)

        NSLayoutConstraint.activate([
            imageView.widthAnchor.constraint(equalTo: scrollView.widthAnchor),
            imageView.heightAnchor.constraint(equalTo: scrollView.heightAnchor),
            imageView.centerXAnchor.constraint(equalTo: scrollView.centerXAnchor),
            imageView.centerYAnchor.constraint(equalTo: scrollView.centerYAnchor),
        ])

        context.coordinator.imageView = imageView
        return scrollView
    }

    func updateUIView(_ uiView: UIScrollView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        weak var imageView: UIImageView?
        func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
    }
}

// MARK: - ShareSheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
