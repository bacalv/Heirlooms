import Foundation

/// URLSession-based async/await client for the Heirlooms server API.
///
/// All calls add the `X-Api-Key` header using the token retrieved from the Keychain.
/// The base URL is `https://api.heirlooms.digital`.
///
/// This class is designed to be subclassed (or swapped out) in unit tests by
/// providing a custom `URLSession` via the `session` initialiser parameter.
public final class HeirloomsAPI {

    public static let defaultBaseURL = URL(string: "https://api.heirlooms.digital")!

    private let baseURL: URL
    let session: URLSession           // internal so tests can inspect

    /// Initialises the API client.
    ///
    /// - Parameters:
    ///   - baseURL: Override for unit tests. Default: `https://api.heirlooms.digital`.
    ///   - session: Override for unit tests. Default: `URLSession.shared`.
    public init(
        baseURL: URL = HeirloomsAPI.defaultBaseURL,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.session = session
    }

    // MARK: - Upload flow

    /// Initiates an encrypted upload, returning signed GCS URLs for content and thumbnail.
    ///
    /// Calls `POST /api/content/uploads/initiate` with `storage_class: "encrypted"`.
    ///
    /// - Parameters:
    ///   - filename: Not sent to the server; used locally for logging.
    ///   - contentType: MIME type (e.g. `"image/jpeg"`).
    ///   - plotId: If non-nil, the upload will be associated with this shared plot.
    ///     (The association is set at confirm time via tags or plot membership; this
    ///     parameter is included in the request body for future server-side routing.)
    public func initiateUpload(
        filename: String,
        contentType: String,
        plotId: String? = nil
    ) async throws -> UploadTicket {
        var body: [String: Any] = [
            "mimeType": contentType,
            "storage_class": "encrypted",
        ]
        if let plotId {
            body["plotId"] = plotId
        }

        let url = baseURL.appendingPathComponent("api/content/uploads/initiate")
        let request = try buildRequest(url: url, method: "POST", body: body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "initiateUpload")

        let json = try decode(data, as: [String: String].self, context: "initiateUpload")
        guard let storageKey = json["storageKey"],
              let uploadUrl = json["uploadUrl"] else {
            throw HeirloomsError.decodingError("Missing storageKey or uploadUrl in initiate response")
        }

        return UploadTicket(
            storageKey: storageKey,
            uploadUrl: uploadUrl,
            thumbnailStorageKey: json["thumbnailStorageKey"],
            thumbnailUploadUrl: json["thumbnailUploadUrl"]
        )
    }

    /// Confirms an encrypted upload after the ciphertext blobs have been PUT to GCS.
    ///
    /// Calls `POST /api/content/uploads/confirm`.
    ///
    /// - Parameters:
    ///   - uploadId: Ignored — the server uses `storageKey` as the primary key here.
    ///   - storageKey: From the `UploadTicket`.
    ///   - thumbnailStorageKey: From the `UploadTicket`.
    ///   - mimeType: Original MIME type.
    ///   - fileSize: Total byte count of the *encrypted* content blob.
    ///   - wrappedDEK: Base64-encoded symmetric envelope (master-aes256gcm-v1).
    ///   - wrappedThumbDEK: Base64-encoded symmetric envelope for thumbnail DEK (may be nil).
    ///   - contentType: Alias for `mimeType` for callers using the interface name from the task spec.
    public func confirmUpload(
        storageKey: String,
        thumbnailStorageKey: String?,
        mimeType: String,
        fileSize: Int64,
        wrappedDEK: Data,
        wrappedThumbDEK: Data?,
        contentHash: String? = nil,
        takenAt: Date? = nil
    ) async throws {
        var body: [String: Any] = [
            "storageKey": storageKey,
            "mimeType": mimeType,
            "fileSize": fileSize,
            "storage_class": "encrypted",
            "envelopeVersion": 1,
            "wrappedDek": wrappedDEK.base64EncodedString(),
            "dekFormat": EnvelopeCrypto.algMasterSymmetric,
        ]
        if let thumbnailStorageKey {
            body["thumbnailStorageKey"] = thumbnailStorageKey
        }
        if let wrappedThumbDEK {
            body["wrappedThumbnailDek"] = wrappedThumbDEK.base64EncodedString()
            body["thumbnailDekFormat"] = EnvelopeCrypto.algMasterSymmetric
        }
        if let contentHash {
            body["contentHash"] = contentHash
        }
        if let takenAt {
            let iso = ISO8601DateFormatter()
            body["takenAt"] = iso.string(from: takenAt)
        }

        let url = baseURL.appendingPathComponent("api/content/uploads/confirm")
        let request = try buildRequest(url: url, method: "POST", body: body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "confirmUpload")
    }

    // MARK: - Item listing

    /// Lists items in a shared plot, returning one page.
    ///
    /// Calls `GET /api/content/uploads?plot_id=<id>&limit=50&cursor=<cursor>`.
    public func listPlotItems(
        plotId: String,
        cursor: String? = nil,
        limit: Int = 50
    ) async throws -> [PlotItem] {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("api/content/uploads"),
            resolvingAgainstBaseURL: false
        )!
        var queryItems = [
            URLQueryItem(name: "plot_id", value: plotId),
            URLQueryItem(name: "limit", value: String(limit)),
        ]
        if let cursor {
            queryItems.append(URLQueryItem(name: "cursor", value: cursor))
        }
        components.queryItems = queryItems

        let url = components.url!
        let request = try buildRequest(url: url, method: "GET")
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "listPlotItems")

        let page = try decode(data, as: UploadListPage.self, context: "listPlotItems")
        return page.items
    }

    /// Fetches the raw encrypted content blob for an upload.
    ///
    /// Calls `GET /api/content/uploads/<id>/file`.
    public func fetchItem(uploadId: String) async throws -> Data {
        let url = baseURL
            .appendingPathComponent("api/content/uploads")
            .appendingPathComponent(uploadId)
            .appendingPathComponent("file")
        let request = try buildRequest(url: url, method: "GET")
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "fetchItem")
        return data
    }

    /// Fetches the raw encrypted thumbnail blob for an upload.
    ///
    /// Calls `GET /api/content/uploads/<id>/thumb`.
    public func fetchThumbnail(uploadId: String) async throws -> Data {
        let url = baseURL
            .appendingPathComponent("api/content/uploads")
            .appendingPathComponent(uploadId)
            .appendingPathComponent("thumb")
        let request = try buildRequest(url: url, method: "GET")
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "fetchThumbnail")
        return data
    }

    // MARK: - Registration

    /// Redeems a friend invite token and creates a new account.
    ///
    /// Calls `POST /api/auth/register`.
    ///
    /// On success, the caller should store `result.sessionToken` and `result.userId`
    /// in the Keychain via `KeychainManager.saveSessionToken(_:)`.
    public func register(
        inviteToken: String,
        username: String,
        displayName: String,
        authSalt: Data,
        authVerifier: Data,
        wrappedMasterKey: Data,
        wrapFormat: String,
        pubkeyFormat: String,
        pubkey: Data,
        deviceId: String,
        deviceLabel: String,
        deviceKind: String = "ios"
    ) async throws -> RegisterResponse {
        let body: [String: Any] = [
            "invite_token": inviteToken,
            "username": username,
            "display_name": displayName,
            "auth_salt": authSalt.base64EncodedString(),
            "auth_verifier": authVerifier.base64EncodedString(),
            "wrapped_master_key": wrappedMasterKey.base64EncodedString(),
            "wrap_format": wrapFormat,
            "pubkey_format": pubkeyFormat,
            "pubkey": pubkey.base64EncodedString(),
            "device_id": deviceId,
            "device_label": deviceLabel,
            "device_kind": deviceKind,
        ]

        // Registration does not require an existing session token.
        let url = baseURL.appendingPathComponent("api/auth/register")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "register")
        return try decode(data, as: RegisterResponse.self, context: "register")
    }

    // MARK: - Plot membership

    /// Submits the recipient sharing pubkey to the server to begin plot join flow.
    ///
    /// Calls `POST /api/plots/join`.
    ///
    /// Returns the pending inviteId and inviterDisplayName so the app can poll
    /// for confirmation.
    public func joinPlot(
        token: String,
        recipientSharingPubkey: Data
    ) async throws -> (inviteId: String, inviterDisplayName: String) {
        let body: [String: Any] = [
            "token": token,
            "recipientSharingPubkey": recipientSharingPubkey.base64EncodedString(),
        ]
        let url = baseURL.appendingPathComponent("api/plots/join")
        let request = try buildRequest(url: url, method: "POST", body: body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "joinPlot")

        let json = try decode(data, as: [String: String].self, context: "joinPlot")
        guard let inviteId = json["inviteId"],
              let inviterDisplayName = json["inviterDisplayName"] else {
            throw HeirloomsError.decodingError("Missing inviteId or inviterDisplayName in join response")
        }
        return (inviteId, inviterDisplayName)
    }

    /// Lists all shared plot memberships for the current user.
    ///
    /// Calls `GET /api/plots/shared`.
    public func listSharedMemberships() async throws -> [SharedMembership] {
        let url = baseURL.appendingPathComponent("api/plots/shared")
        let request = try buildRequest(url: url, method: "GET")
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "listSharedMemberships")
        return try decode(data, as: [SharedMembership].self, context: "listSharedMemberships")
    }

    /// Fetches the wrapped plot key for a specific shared plot.
    ///
    /// Calls `GET /api/plots/<id>/plot-key`.
    public func getPlotKey(plotId: String) async throws -> PlotKeyResponse {
        let url = baseURL
            .appendingPathComponent("api/plots")
            .appendingPathComponent(plotId)
            .appendingPathComponent("plot-key")
        let request = try buildRequest(url: url, method: "GET")
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "getPlotKey")
        return try decode(data, as: PlotKeyResponse.self, context: "getPlotKey")
    }

    // MARK: - Pairing

    /// Initiates the device-pairing flow, returning a numeric code for the web to display.
    ///
    /// Calls `POST /api/auth/pairing/initiate`.
    public func initiatePairing() async throws -> (code: String, expiresAt: String) {
        let url = baseURL.appendingPathComponent("api/auth/pairing/initiate")
        let request = try buildRequest(url: url, method: "POST", body: [:])
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data, context: "initiatePairing")
        let json = try decode(data, as: [String: String].self, context: "initiatePairing")
        guard let code = json["code"], let expiresAt = json["expires_at"] else {
            throw HeirloomsError.decodingError("Missing code or expires_at in pairing response")
        }
        return (code, expiresAt)
    }

    // MARK: - Request building helpers

    /// Builds an authenticated request. Reads the session token from Keychain.
    /// Pass `body: nil` for GET requests.
    func buildRequest(url: URL, method: String, body: [String: Any]? = nil) throws -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method

        // Add auth header.
        if let token = try? KeychainManager.getSessionToken() {
            request.setValue(token, forHTTPHeaderField: "X-Api-Key")
        }

        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        return request
    }

    /// Validates that an HTTP response has a 2xx status code.
    private func validateResponse(_ response: URLResponse, data: Data, context: String) throws {
        guard let http = response as? HTTPURLResponse else {
            throw HeirloomsError.networkError(0, "Non-HTTP response in \(context)")
        }
        guard (200..<300).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? "<binary>"
            throw HeirloomsError.networkError(http.statusCode, "\(context): \(body)")
        }
    }

    /// Decodes `data` as `T`, wrapping errors in `HeirloomsError.decodingError`.
    private func decode<T: Decodable>(_ data: Data, as type: T.Type, context: String) throws -> T {
        do {
            let decoder = JSONDecoder()
            return try decoder.decode(type, from: data)
        } catch {
            throw HeirloomsError.decodingError("\(context): \(error.localizedDescription)")
        }
    }
}
