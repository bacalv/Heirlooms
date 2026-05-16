import SwiftUI
import AVFoundation
import HeirloomsCore

/// Handles both Scan 1 (friend invite → account registration) and
/// Scan 2 (plot invite → plot key redemption).
struct ActivateView: View {

    enum ScanMode {
        case friendInvite    // Scan 1: heirlooms://invite/friend/<token>
        case plotInvite      // Scan 2: heirlooms://invite/plot/<token>
        case webPairing      // Scan 3: JSON {"session_id":"...","pubkey":"..."} from web session
    }

    var scanMode: ScanMode = .friendInvite

    @EnvironmentObject private var appState: AppState
    @State private var isShowingScanner = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var successMessage: String?

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

                if let successMessage {
                    Text(successMessage)
                        .font(.caption)
                        .foregroundStyle(.green)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }

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
            QRScannerView(mode: scanMode) { scannedString in
                isShowingScanner = false
                Task { await handle(scannedString: scannedString) }
            }
        }
    }

    // MARK: - Computed strings

    private var titleText: String {
        switch scanMode {
        case .friendInvite: return "Welcome to Heirlooms"
        case .plotInvite:   return "Scan the shared album code"
        case .webPairing:   return "Connect a device"
        }
    }

    private var subtitleText: String {
        switch scanMode {
        case .friendInvite:
            return "Your family member has a QR code ready. Tap Activate to scan it."
        case .plotInvite:
            return "Ask your family member to show the album QR code."
        case .webPairing:
            return "Show the QR code from your browser to give it access to your Heirlooms."
        }
    }

    private var buttonText: String {
        switch scanMode {
        case .friendInvite: return "Activate"
        case .plotInvite:   return "Scan QR Code"
        case .webPairing:   return "Scan Pairing Code"
        }
    }

    // MARK: - QR handling

    private func handle(scannedString string: String) async {
        isLoading = true
        errorMessage = nil
        successMessage = nil
        defer { isLoading = false }

        // Try heirlooms:// URL first (friend invite and plot invite flows).
        if let url = URL(string: string), url.scheme == "heirlooms" {
            switch url.host {
            case "invite" where url.pathComponents.dropFirst().first == "friend":
                await handleFriendInvite(url: url)
            case "invite" where url.pathComponents.dropFirst().first == "plot":
                await handlePlotInvite(url: url)
            default:
                errorMessage = "Unrecognised QR code. Please ask your family member to regenerate it."
            }
            return
        }

        // Try JSON pairing payload: {"session_id":"...","pubkey":"..."} from web session.
        if let data = string.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: String],
           let sessionId = json["session_id"],
           let webPubkey = json["pubkey"] {
            await handleWebPairing(sessionId: sessionId, webPubkey: webPubkey)
            return
        }

        errorMessage = "This doesn't look like a Heirlooms code. Try again."
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
            // Persist userId so HomeView can distinguish "Shared with you" vs "You shared".
            try KeychainManager.saveUserId(response.userId)
            // Store master key in its dedicated Keychain slot.
            var masterKeyData = masterKey.withUnsafeBytes { Data($0) }
            defer { masterKeyData.resetBytes(in: 0..<masterKeyData.count) }
            try KeychainManager.saveMasterKey(masterKeyData)

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
            var plotKeyData = plotKey.withUnsafeBytes { Data($0) }
            defer { plotKeyData.resetBytes(in: 0..<plotKeyData.count) }
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

    private func handleWebPairing(sessionId: String, webPubkey: String) async {
        do {
            // Base64url → standard base64 → Data (web session pubkey in SPKI/DER format).
            let base64 = webPubkey
                .replacingOccurrences(of: "-", with: "+")
                .replacingOccurrences(of: "_", with: "/")
            let padded = base64 + String(repeating: "=", count: (4 - base64.count % 4) % 4)
            guard let spkiData = Data(base64Encoded: padded) else {
                throw HeirloomsError.invalidPublicKey("Cannot decode web pubkey from QR")
            }

            // Import SPKI → uncompressed x963 (65 bytes) required by wrapDEK.
            let uncompressed = try P256.KeyAgreement.PublicKey(derRepresentation: spkiData).x963Representation

            // Retrieve master key from its dedicated Keychain slot.
            guard let masterKeyData = try? KeychainManager.getMasterKey(), masterKeyData.count == 32 else {
                throw HeirloomsError.decryptionFailed("Master key not available — please re-activate the app")
            }

            // Wrap master key to the web session's P-256 pubkey.
            let (wrappedMasterKey, _) = try EnvelopeCrypto.wrapDEK(
                dek: SymmetricKey(data: masterKeyData),
                recipientPublicKeyData: uncompressed
            )

            let api = HeirloomsAPI()
            try await api.completePairing(
                sessionId: sessionId,
                wrappedMasterKey: wrappedMasterKey,
                webPubkey: webPubkey
            )

            await MainActor.run {
                successMessage = "Browser linked. You can now close this screen."
            }
        } catch {
            await MainActor.run {
                errorMessage = "Pairing failed: \(error.localizedDescription)"
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

// MARK: - QRScannerView

/// Camera-based QR scanner using AVCaptureSession + AVMetadataOutput.
///
/// Displays a live camera preview filling the view. On the first QR code
/// detection, stops the capture session and calls `onScanned` exactly once
/// with the scanned URL. If camera permission is denied, shows a text overlay.
struct QRScannerView: UIViewRepresentable {

    let mode: ActivateView.ScanMode
    let onScanned: (String) -> Void

    func makeUIView(context: Context) -> QRPreviewView {
        let view = QRPreviewView()
        view.coordinator = context.coordinator
        context.coordinator.previewView = view
        context.coordinator.startSessionIfAuthorized()
        return view
    }

    func updateUIView(_ uiView: QRPreviewView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onScanned: onScanned)
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {

        let onScanned: (String) -> Void
        weak var previewView: QRPreviewView?
        private let session = AVCaptureSession()
        private var hasFired = false
        private let sessionQueue = DispatchQueue(label: "digital.heirlooms.qr-session")

        init(onScanned: @escaping (String) -> Void) {
            self.onScanned = onScanned
        }

        func startSessionIfAuthorized() {
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                sessionQueue.async { self.configureAndStart() }
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    if granted { self.sessionQueue.async { self.configureAndStart() } }
                }
            default:
                // Denied or restricted — the preview view shows the permission prompt text.
                break
            }
        }

        private func configureAndStart() {
            guard !session.isRunning else { return }
            session.beginConfiguration()
            defer { session.commitConfiguration() }

            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input) else { return }
            session.addInput(input)

            let metaOutput = AVCaptureMetadataOutput()
            guard session.canAddOutput(metaOutput) else { return }
            session.addOutput(metaOutput)
            metaOutput.setMetadataObjectsDelegate(self, queue: .main)
            if metaOutput.availableMetadataObjectTypes.contains(.qr) {
                metaOutput.metadataObjectTypes = [.qr]
            }

            DispatchQueue.main.async {
                self.previewView?.attachSession(self.session)
            }
            session.startRunning()
        }

        func stopSession() {
            sessionQueue.async { [weak self] in
                self?.session.stopRunning()
            }
        }

        // AVCaptureMetadataOutputObjectsDelegate
        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard !hasFired else { return }
            guard let readableObject = metadataObjects
                    .compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
                    .first(where: { $0.type == .qr }),
                  let stringValue = readableObject.stringValue
            else { return }

            hasFired = true
            stopSession()
            onScanned(stringValue)
        }
    }
}

// MARK: - QRPreviewView

/// UIView subclass hosting an AVCaptureVideoPreviewLayer.
/// Shows a permission-denied overlay when camera access is unavailable.
final class QRPreviewView: UIView {

    weak var coordinator: QRScannerView.Coordinator?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let deniedLabel: UILabel = {
        let label = UILabel()
        label.text = "Camera access is required to scan a QR code.\n\nGo to Settings → Heirlooms → Camera to enable it."
        label.numberOfLines = 0
        label.textAlignment = .center
        label.textColor = .secondaryLabel
        label.font = .preferredFont(forTextStyle: .body)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        addSubview(deniedLabel)
        NSLayoutConstraint.activate([
            deniedLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 32),
            deniedLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -32),
            deniedLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
        ])
        updateDeniedLabelVisibility()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    func attachSession(_ session: AVCaptureSession) {
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = bounds
        self.layer.insertSublayer(layer, at: 0)
        previewLayer = layer
        updateDeniedLabelVisibility()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    private func updateDeniedLabelVisibility() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        deniedLabel.isHidden = (status == .authorized || status == .notDetermined)
    }
}

// CommonCrypto import for SHA-256 (only needed in app target, not in HeirloomsCore).
import CommonCrypto
