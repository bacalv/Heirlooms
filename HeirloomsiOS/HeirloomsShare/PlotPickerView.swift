import SwiftUI
import HeirloomsCore

/// SwiftUI view presented inside the Share Extension.
///
/// Shows a list of the user's shared plots so they can pick a destination,
/// then triggers the encrypted upload and shows progress.
///
/// Navigation flow:
///   1. Loading indicator while listing memberships.
///   2. Plot list — user picks a plot.
///   3. Progress bar while encrypting + uploading.
///   4. Success / error state, then auto-dismiss.
struct PlotPickerView: View {

    // MARK: - Input

    /// The raw media items extracted from the extension context.
    let items: [ShareItem]

    /// Called when the extension should dismiss itself (success or cancel).
    let onDismiss: () -> Void

    // MARK: - State

    @StateObject private var uploader = ShareUploadCoordinator()

    @State private var memberships: [SharedMembership] = []
    @State private var isLoadingPlots = true
    @State private var loadError: String?

    private let api = HeirloomsAPI()

    // MARK: - Body

    var body: some View {
        NavigationStack {
            Group {
                if uploader.isComplete {
                    successView
                } else if uploader.isUploading {
                    uploadingView
                } else if let error = uploader.uploadError {
                    errorView(message: error)
                } else if isLoadingPlots {
                    loadingView
                } else if let error = loadError {
                    errorView(message: error)
                } else if memberships.isEmpty {
                    noMembershipsView
                } else {
                    plotListView
                }
            }
            .navigationTitle("Share to Heirlooms")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { onDismiss() }
                        .disabled(uploader.isUploading)
                }
            }
        }
        .task { await loadMemberships() }
    }

    // MARK: - Sub-views

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text("Loading albums…")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var plotListView: some View {
        List(memberships, id: \.plotId) { membership in
            Button {
                Task { await startUpload(plotId: membership.plotId) }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(membership.plotName)
                            .font(.body)
                        Text(membership.role.capitalized)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.secondary)
                        .font(.caption)
                }
            }
            .buttonStyle(.plain)
        }
        .listStyle(.insetGrouped)
    }

    private var uploadingView: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "lock.fill")
                .font(.system(size: 48))
                .foregroundStyle(.blue)
            Text("Encrypting & uploading…")
                .font(.headline)
            ProgressView(value: uploader.progress)
                .progressViewStyle(.linear)
                .padding(.horizontal, 32)
            Text("\(Int(uploader.progress * 100))%")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var successView: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)
            Text("Uploaded!")
                .font(.title2.bold())
            Text("Your photo has been encrypted and added to the album.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 32)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task {
            // Auto-dismiss after a short success delay.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            onDismiss()
        }
    }

    private var noMembershipsView: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "person.2.slash")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("No shared albums")
                .font(.headline)
            Text("Join a shared album in the Heirlooms app first.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 32)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func errorView(message: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundStyle(.orange)
            Text("Something went wrong")
                .font(.headline)
            Text(message)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 32)
            Button("Dismiss") { onDismiss() }
                .buttonStyle(.bordered)
                .padding(.top, 8)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Actions

    private func loadMemberships() async {
        isLoadingPlots = true
        loadError = nil
        defer { isLoadingPlots = false }

        do {
            memberships = try await api.listSharedMemberships()
        } catch {
            loadError = error.localizedDescription
        }
    }

    private func startUpload(plotId: String) async {
        guard let plotKey = ShareExtensionKeychain.getPlotKey() else {
            uploader.uploadError = "Plot key not available. Open the Heirlooms app to reconnect."
            return
        }

        for item in items {
            switch item {
            case .image(let data, let mimeType):
                await uploader.uploadImage(
                    imageData: data,
                    mimeType: mimeType,
                    plotId: plotId,
                    plotKey: plotKey
                )
            case .video(let url, let mimeType):
                await uploader.uploadVideo(
                    fileURL: url,
                    mimeType: mimeType,
                    plotId: plotId,
                    plotKey: plotKey
                )
            }
            // Stop on first error.
            if uploader.uploadError != nil { return }
        }
    }
}

// MARK: - ShareItem

/// Typed media item extracted from the extension context before the UI is shown.
enum ShareItem {
    case image(data: Data, mimeType: String)
    case video(url: URL, mimeType: String)
}
