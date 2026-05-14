import SwiftUI
import PhotosUI
import HeirloomsCore

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
        // In the full implementation, store userId in Keychain at registration.
        UserDefaults.standard.string(forKey: "heirlooms.myUserId")
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
        // Load the raw data from the picker item.
        // In production: load as a file URL and stream to avoid RAM spikes.
        guard let data = try? await pickerItem.loadTransferable(type: Data.self) else {
            print("[HomeView] Failed to load picker item data")
            return
        }

        let contentType = pickerItem.supportedContentTypes.first?.preferredMIMEType ?? "application/octet-stream"

        do {
            // Generate DEK.
            let dek = EnvelopeCrypto.generateDEK()

            // Encrypt content.
            let encryptedContent = try EnvelopeCrypto.encryptContent(plaintext: data, dek: dek)

            // Wrap DEK under plot key (symmetric wrap).
            let dekBytes = dek.withUnsafeBytes { Data($0) }
            let wrappedDek = try EnvelopeCrypto.wrapSymmetric(
                plaintext: dekBytes,
                wrappingKey: plotKey,
                algorithmID: EnvelopeCrypto.algMasterSymmetric
            )

            // Initiate upload.
            let ticket = try await api.initiateUpload(
                filename: "upload",
                contentType: contentType,
                plotId: plotId
            )

            // Write encrypted content to a temp file for background upload.
            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
            try encryptedContent.write(to: tempURL)

            // Enqueue background upload.
            BackgroundUploadManager.shared.enqueueUpload(localURL: tempURL, ticket: ticket)

            // Confirm upload (this can also be done after the background task completes
            // via a completion notification in the full implementation).
            try await api.confirmUpload(
                storageKey: ticket.storageKey,
                thumbnailStorageKey: ticket.thumbnailStorageKey,
                mimeType: contentType,
                fileSize: Int64(encryptedContent.count),
                wrappedDEK: wrappedDek,
                wrappedThumbDEK: nil
            )
        } catch {
            print("[HomeView] Upload failed: \(error.localizedDescription)")
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
              let wrappedDek = item.wrappedThumbnailDek,
              let plotKeyData = try? KeychainManager.getPlotKey(),
              plotKeyData.count == 32
        else { return }

        let plotKey = SymmetricKey(data: plotKeyData)
        let api = HeirloomsAPI()

        do {
            let encryptedThumb = try await api.fetchThumbnail(uploadId: item.id)
            let wrappedDekData = Data(base64Encoded: wrappedDek) ?? Data()
            let dekBytes = try EnvelopeCrypto.unwrapSymmetric(
                envelope: wrappedDekData,
                unwrappingKey: plotKey,
                expectedAlgorithmID: EnvelopeCrypto.algMasterSymmetric
            )
            let thumbDek = SymmetricKey(data: dekBytes)
            let plaintextThumb = try EnvelopeCrypto.decryptContent(envelope: encryptedThumb, dek: thumbDek)
            thumbnail = UIImage(data: plaintextThumb)
        } catch {
            // Silently fail — thumbnail stays as placeholder.
        }
    }
}

// MARK: - SettingsView stub

struct SettingsView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        List {
            Section("Account") {
                LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "–")
                Button("API Key Reset") {
                    // TODO: API key reset semantics TBD (see brief).
                }
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
    }
}

// MARK: - Stub views

struct PairingView: View {
    var body: some View {
        Text("Pairing code generation — TODO")
            .navigationTitle("Devices & Access")
    }
}

struct DiagnosticsView: View {
    var body: some View {
        Text("Diagnostics log — TODO")
            .navigationTitle("Diagnostics")
    }
}
