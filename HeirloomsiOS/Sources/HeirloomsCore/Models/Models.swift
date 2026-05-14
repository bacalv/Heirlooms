import Foundation

// MARK: - PlotItem

/// One item (photo or video) in the shared plot.
/// Mirrors the fields returned by GET /api/content/uploads when queried with ?plot_id=...
public struct PlotItem: Codable, Identifiable, Equatable {
    /// Server-assigned UUID.
    public let id: String
    /// ISO-8601 timestamp when the item was uploaded.
    public let uploadedAt: String
    /// MIME type of the original content (e.g. "image/jpeg", "video/mp4").
    public let contentType: String
    /// Base64-encoded symmetric envelope (master-aes256gcm-v1) wrapping the content DEK.
    public let wrappedDek: String?
    /// DEK format string (typically "master-aes256gcm-v1").
    public let dekFormat: String?
    /// Base64-encoded symmetric envelope wrapping the thumbnail DEK. May be nil for videos.
    public let wrappedThumbnailDek: String?
    /// Thumbnail DEK format string.
    public let thumbnailDekFormat: String?
    /// Storage key for the encrypted thumbnail blob.
    public let thumbnailStorageKey: String?
    /// Storage key for the encrypted full-resolution content blob.
    public let storageKey: String
    /// User ID of the account that uploaded this item.
    public let uploaderUserId: String?

    public init(
        id: String,
        uploadedAt: String,
        contentType: String,
        wrappedDek: String?,
        dekFormat: String?,
        wrappedThumbnailDek: String?,
        thumbnailDekFormat: String?,
        thumbnailStorageKey: String?,
        storageKey: String,
        uploaderUserId: String?
    ) {
        self.id = id
        self.uploadedAt = uploadedAt
        self.contentType = contentType
        self.wrappedDek = wrappedDek
        self.dekFormat = dekFormat
        self.wrappedThumbnailDek = wrappedThumbnailDek
        self.thumbnailDekFormat = thumbnailDekFormat
        self.thumbnailStorageKey = thumbnailStorageKey
        self.storageKey = storageKey
        self.uploaderUserId = uploaderUserId
    }

    // MARK: Codable keys (snake_case ↔ camelCase mapping)

    enum CodingKeys: String, CodingKey {
        case id
        case uploadedAt     = "uploaded_at"
        case contentType    = "mime_type"
        case wrappedDek     = "wrapped_dek"
        case dekFormat      = "dek_format"
        case wrappedThumbnailDek    = "wrapped_thumbnail_dek"
        case thumbnailDekFormat     = "thumbnail_dek_format"
        case thumbnailStorageKey    = "thumbnail_storage_key"
        case storageKey             = "storage_key"
        case uploaderUserId         = "uploader_user_id"
    }
}

// MARK: - UploadTicket

/// Returned by POST /api/content/uploads/initiate.
/// Carries the GCS signed URL and server-assigned storage keys.
public struct UploadTicket: Equatable {
    /// Server-assigned storage key for the encrypted content blob.
    public let storageKey: String
    /// Signed GCS URL to PUT the encrypted content blob to.
    public let uploadUrl: String
    /// Server-assigned storage key for the encrypted thumbnail blob.
    public let thumbnailStorageKey: String?
    /// Signed GCS URL to PUT the encrypted thumbnail blob to.
    public let thumbnailUploadUrl: String?

    public init(
        storageKey: String,
        uploadUrl: String,
        thumbnailStorageKey: String?,
        thumbnailUploadUrl: String?
    ) {
        self.storageKey = storageKey
        self.uploadUrl = uploadUrl
        self.thumbnailStorageKey = thumbnailStorageKey
        self.thumbnailUploadUrl = thumbnailUploadUrl
    }
}

// MARK: - UserCredentials

/// Stored in Keychain after successful registration.
public struct UserCredentials: Equatable {
    /// The raw session token (passed verbatim as X-Api-Key header value).
    public let sessionToken: String
    /// Server-assigned UUID for this user.
    public let userId: String

    public init(sessionToken: String, userId: String) {
        self.sessionToken = sessionToken
        self.userId = userId
    }
}

// MARK: - RegisterResponse

/// Decoded from POST /api/auth/register response.
public struct RegisterResponse: Codable {
    public let sessionToken: String
    public let userId: String
    public let expiresAt: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case userId = "user_id"
        case expiresAt = "expires_at"
    }
}

// MARK: - UploadListPage

/// Decoded from GET /api/content/uploads paginated response.
public struct UploadListPage: Codable {
    public let items: [PlotItem]
    public let nextCursor: String?

    enum CodingKeys: String, CodingKey {
        case items
        case nextCursor = "next_cursor"
    }
}

// MARK: - PlotKeyResponse

/// Decoded from GET /api/plots/<id>/plot-key.
public struct PlotKeyResponse: Codable {
    /// Base64-encoded asymmetric envelope (p256-ecdh-hkdf-aes256gcm-v1) wrapping the plot key.
    public let wrappedPlotKey: String
    /// Format identifier — typically "p256-ecdh-hkdf-aes256gcm-v1".
    public let plotKeyFormat: String

    enum CodingKeys: String, CodingKey {
        case wrappedPlotKey
        case plotKeyFormat
    }
}

// MARK: - SharedMembership

/// One entry from GET /api/plots/shared.
public struct SharedMembership: Codable {
    public let plotId: String
    public let plotName: String
    public let role: String
    public let status: String

    enum CodingKeys: String, CodingKey {
        case plotId
        case plotName
        case role
        case status
    }
}

// MARK: - HeirloomsError

/// Domain errors surfaced to callers.
public enum HeirloomsError: Error, LocalizedError {
    case keychainWrite(OSStatus)
    case keychainRead(OSStatus)
    case keychainNotFound
    case keypairGeneration(String)
    case publicKeyExport(String)
    case envelopeVersionMismatch(UInt8)
    case unknownAlgorithmID(String)
    case envelopeTooShort(Int)
    case decryptionFailed(String)
    case encryptionFailed(String)
    case sharedSecretFailed(String)
    case invalidPublicKey(String)
    case networkError(Int, String)
    case decodingError(String)
    case missingCredentials

    public var errorDescription: String? {
        switch self {
        case .keychainWrite(let s):           return "Keychain write failed: \(s)"
        case .keychainRead(let s):            return "Keychain read failed: \(s)"
        case .keychainNotFound:               return "Keychain item not found"
        case .keypairGeneration(let m):       return "Keypair generation failed: \(m)"
        case .publicKeyExport(let m):         return "Public key export failed: \(m)"
        case .envelopeVersionMismatch(let v): return "Unsupported envelope version: \(v)"
        case .unknownAlgorithmID(let id):     return "Unknown algorithm ID: \(id)"
        case .envelopeTooShort(let n):        return "Envelope too short: \(n) bytes"
        case .decryptionFailed(let m):        return "Decryption failed: \(m)"
        case .encryptionFailed(let m):        return "Encryption failed: \(m)"
        case .sharedSecretFailed(let m):      return "ECDH shared secret failed: \(m)"
        case .invalidPublicKey(let m):        return "Invalid public key: \(m)"
        case .networkError(let code, let m):  return "Network error \(code): \(m)"
        case .decodingError(let m):           return "JSON decoding error: \(m)"
        case .missingCredentials:             return "No stored credentials found"
        }
    }
}
