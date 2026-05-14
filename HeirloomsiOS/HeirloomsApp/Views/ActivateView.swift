import SwiftUI
import AVFoundation
import HeirloomsCore

/// Handles both Scan 1 (friend invite → account registration) and
/// Scan 2 (plot invite → plot key redemption).
struct ActivateView: View {

    enum ScanMode {
        case friendInvite    // Scan 1: heirlooms://invite/friend/<token>
        case plotInvite      // Scan 2: heirlooms://invite/plot/<token>
    }

    var scanMode: ScanMode = .friendInvite

    @EnvironmentObject private var appState: AppState
    @State private var isShowingScanner = false
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()

                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.blue)

                Text(titleText)
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)

                Text(subtitleText)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                Spacer()

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }

                Button(action: { isShowingScanner = true }) {
                    Label(buttonText, systemImage: "qrcode.viewfinder")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding(.horizontal, 32)
                .disabled(isLoading)

                if isLoading {
                    ProgressView()
                        .padding(.bottom, 8)
                }

                Spacer()
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
        .sheet(isPresented: $isShowingScanner) {
            QRScannerView(mode: scanMode) { scannedURL in
                isShowingScanner = false
                Task { await handle(scannedURL: scannedURL) }
            }
        }
    }

    // MARK: - Computed strings

    private var titleText: String {
        switch scanMode {
        case .friendInvite: return "Welcome to Heirlooms"
        case .plotInvite:   return "Scan the shared album code"
        }
    }

    private var subtitleText: String {
        switch scanMode {
        case .friendInvite:
            return "Your family member has a QR code ready. Tap Activate to scan it."
        case .plotInvite:
            return "Ask your family member to show the album QR code."
        }
    }

    private var buttonText: String {
        switch scanMode {
        case .friendInvite: return "Activate"
        case .plotInvite:   return "Scan QR Code"
        }
    }

    // MARK: - QR handling

    private func handle(scannedURL url: URL) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        guard url.scheme == "heirlooms" else {
            errorMessage = "This doesn't look like a Heirlooms code. Try again."
            return
        }

        switch url.host {
        case "invite" where url.pathComponents.dropFirst().first == "friend":
            await handleFriendInvite(url: url)
        case "invite" where url.pathComponents.dropFirst().first == "plot":
            await handlePlotInvite(url: url)
        default:
            errorMessage = "Unrecognised QR code. Please ask your family member to regenerate it."
        }
    }

    private func handleFriendInvite(url: URL) async {
        let token = url.lastPathComponent
        guard !token.isEmpty else {
            errorMessage = "Invalid invite link."
            return
        }

        do {
            // Generate P-256 keypair and store in Keychain.
            try KeychainManager.generateSharingKeypair()
            let pubkeyData = try KeychainManager.getSharingPublicKeyData()

            // Generate registration credentials.
            let authSalt = generateRandomBytes(16)
            let authKey  = generateRandomBytes(32)
            let authVerifier = sha256(authKey)

            // Wrap a fresh master key to the device's own public key.
            let masterKey = EnvelopeCrypto.generateDEK()
            let (wrappedMasterKey, _) = try EnvelopeCrypto.wrapDEK(
                dek: masterKey,
                recipientPublicKeyData: pubkeyData
            )

            let deviceId = UUID().uuidString
            let api = HeirloomsAPI()
            let response = try await api.register(
                inviteToken: token,
                username: "ios-\(deviceId.prefix(8).lowercased())",
                displayName: "iPhone",
                authSalt: authSalt,
                authVerifier: authVerifier,
                wrappedMasterKey: wrappedMasterKey,
                wrapFormat: EnvelopeCrypto.algAsymmetric,
                pubkeyFormat: "p256-uncompressed",
                pubkey: pubkeyData,
                deviceId: deviceId,
                deviceLabel: "iPhone",
                deviceKind: "ios"
            )

            try KeychainManager.saveSessionToken(response.sessionToken)
            // Store master key raw bytes for later use.
            let masterKeyData = masterKey.withUnsafeBytes { Data($0) }
            try KeychainManager.savePlotKey(masterKeyData) // repurposed slot until plot key is set

            await MainActor.run {
                appState.refreshPhase()
            }
        } catch {
            await MainActor.run {
                errorMessage = "Activation failed: \(error.localizedDescription)"
            }
        }
    }

    private func handlePlotInvite(url: URL) async {
        let token = url.lastPathComponent
        guard !token.isEmpty else {
            errorMessage = "Invalid plot invite link."
            return
        }

        do {
            let pubkeyData = try KeychainManager.getSharingPublicKeyData()
            let privateKey = try KeychainManager.getSharingPrivateKey()
            let api = HeirloomsAPI()

            // Step 1: Register our pubkey for this invite.
            let (inviteId, _) = try await api.joinPlot(token: token, recipientSharingPubkey: pubkeyData)
            _ = inviteId // used for logging in production

            // Step 2: Poll for membership to become active.
            var plotId: String?
            for _ in 0..<30 {
                let memberships = try await api.listSharedMemberships()
                if let active = memberships.first(where: { $0.status == "active" }) {
                    plotId = active.plotId
                    break
                }
                try await Task.sleep(nanoseconds: 2_000_000_000) // 2s poll interval
            }

            guard let resolvedPlotId = plotId else {
                errorMessage = "Timed out waiting for confirmation. Please try again."
                return
            }

            // Step 3: Fetch and unwrap the plot key.
            let plotKeyResponse = try await api.getPlotKey(plotId: resolvedPlotId)
            guard let wrappedKeyData = Data(base64Encoded: plotKeyResponse.wrappedPlotKey) else {
                throw HeirloomsError.decodingError("Invalid base64 in wrapped plot key")
            }
            let plotKey = try EnvelopeCrypto.unwrapDEK(
                wrappedKey: wrappedKeyData,
                recipientPrivateKey: privateKey
            )

            // Step 4: Store in Keychain.
            let plotKeyData = plotKey.withUnsafeBytes { Data($0) }
            try KeychainManager.savePlotKey(plotKeyData)
            try KeychainManager.savePlotId(resolvedPlotId)

            await MainActor.run {
                appState.refreshPhase()
            }
        } catch {
            await MainActor.run {
                errorMessage = "Could not join shared album: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Crypto helpers

    private func generateRandomBytes(_ count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }

    private func sha256(_ data: Data) -> Data {
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
        }
        return Data(hash)
    }
}

// MARK: - QRScannerView stub

/// Camera-based QR scanner.
/// In the full implementation this wraps `AVCaptureSession` with
/// `AVMetadataObjectTypeQRCode`. This stub compiles and can be replaced.
struct QRScannerView: View {

    let mode: ActivateView.ScanMode
    let onScanned: (URL) -> Void

    var body: some View {
        // TODO: Replace with AVCaptureSession-based scanner implementation.
        // The scanner should:
        // 1. Request camera permission.
        // 2. Start an AVCaptureSession with AVCaptureDeviceInput (back camera).
        // 3. Add AVCaptureMetadataOutput configured for .qr.
        // 4. On metadata detection, parse the string value as a URL.
        // 5. Filter for scheme "heirlooms" and call onScanned(_:).
        VStack {
            Text("Camera access required")
            Text("(Full QR scanner implementation needed)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

// CommonCrypto import for SHA-256 (only needed in app target, not in HeirloomsCore).
import CommonCrypto
