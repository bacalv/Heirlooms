import Foundation
import Security

/// Manages all Keychain interactions for HeirloomsCore.
///
/// Stores four items:
///  - The P-256 private sharing key (Secure Enclave if available)
///  - The session token (raw string)
///  - The master key (raw 32-byte AES-256 key, stored at account activation)
///  - The plot key (raw 32-byte AES-256 key, stored after joining a shared plot)
///
/// All items use `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` so they are
/// bound to the device and survive app re-install and re-signing, but do NOT
/// migrate to a new device via iCloud Keychain backup.
public final class KeychainManager {

    // MARK: - Constants

    private static let sharingKeyTag = "digital.heirlooms.sharing.privkey"
        .data(using: .utf8)!
    private static let sessionTokenAccount = "session_token"
    private static let masterKeyAccount = "master_key"
    private static let plotKeyAccount = "plot_key"
    private static let plotIdAccount = "plot_id"
    private static let userIdAccount = "user_id"
    private static let service = "digital.heirlooms.ios"

    // MARK: - Sharing keypair

    /// Generates a new P-256 sharing keypair and stores the private key in the Keychain.
    /// Uses the Secure Enclave when available (physical iPhone/iPad), falls back to
    /// software Keychain on the Simulator or unsupported hardware.
    ///
    /// - Throws: `HeirloomsError.keypairGeneration` if key creation fails.
    /// - Returns: The `SecKey` private key reference (retained by Keychain).
    @discardableResult
    public static func generateSharingKeypair() throws -> SecKey {
        // Delete any existing key first so we don't accumulate stale entries.
        deleteSharingKey()

        var error: Unmanaged<CFError>?

        // Access control: biometric/passcode-protected, this device only.
        // `.privateKeyUsage` is required for Secure Enclave keys.
        let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .privateKeyUsage,
            &error
        )
        if accessControl == nil {
            let msg = error?.takeRetainedValue().localizedDescription ?? "unknown"
            throw HeirloomsError.keypairGeneration("SecAccessControlCreateWithFlags failed: \(msg)")
        }

        let privateKeyAttrs: [String: Any] = [
            kSecAttrIsPermanent as String:    true,
            kSecAttrApplicationTag as String: sharingKeyTag,
            kSecAttrAccessControl as String:  accessControl!,
        ]

        // Attempt Secure Enclave first; fall back to software key.
        var params: [String: Any] = [
            kSecAttrKeyType as String:        kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String:  256,
            kSecAttrTokenID as String:        kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String:    privateKeyAttrs,
        ]

        var privateKey = SecKeyCreateRandomKey(params as CFDictionary, &error)

        if privateKey == nil {
            // Secure Enclave not available (Simulator, older hardware). Use software key.
            params.removeValue(forKey: kSecAttrTokenID as String)
            privateKey = SecKeyCreateRandomKey(params as CFDictionary, &error)
        }

        guard let key = privateKey else {
            let msg = error?.takeRetainedValue().localizedDescription ?? "unknown"
            throw HeirloomsError.keypairGeneration(msg)
        }

        return key
    }

    /// Retrieves the stored P-256 private key from the Keychain.
    ///
    /// - Throws: `HeirloomsError.keychainNotFound` if no key is stored yet.
    /// - Throws: `HeirloomsError.keychainRead` on other Keychain errors.
    public static func getSharingPrivateKey() throws -> SecKey {
        let query: [String: Any] = [
            kSecClass as String:              kSecClassKey,
            kSecAttrApplicationTag as String: sharingKeyTag,
            kSecAttrKeyType as String:        kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String:          true,
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecSuccess:
            // swiftlint:disable:next force_cast
            return (item as! SecKey)
        case errSecItemNotFound:
            throw HeirloomsError.keychainNotFound
        default:
            throw HeirloomsError.keychainRead(status)
        }
    }

    /// Returns the sharing public key as a 65-byte uncompressed SEC1 point (0x04 || x || y).
    /// This is the format expected by the server `pubkey` field and by the envelope spec.
    ///
    /// - Throws: `HeirloomsError.keychainNotFound` if no key is stored.
    /// - Throws: `HeirloomsError.publicKeyExport` if export fails.
    public static func getSharingPublicKeyData() throws -> Data {
        let privateKey = try getSharingPrivateKey()

        guard let publicKey = SecKeyCopyPublicKey(privateKey) else {
            throw HeirloomsError.publicKeyExport("SecKeyCopyPublicKey returned nil")
        }

        var error: Unmanaged<CFError>?
        guard let keyData = SecKeyCopyExternalRepresentation(publicKey, &error) as Data? else {
            let msg = error?.takeRetainedValue().localizedDescription ?? "unknown"
            throw HeirloomsError.publicKeyExport(msg)
        }

        guard keyData.count == 65, keyData[0] == 0x04 else {
            throw HeirloomsError.publicKeyExport(
                "Unexpected public key format: \(keyData.count) bytes, prefix \(String(format: "0x%02x", keyData[0]))"
            )
        }

        return keyData
    }

    // MARK: - Session token

    /// Stores a session token in the Keychain (generic password item).
    public static func saveSessionToken(_ token: String) throws {
        let data = Data(token.utf8)
        try saveGenericPassword(data: data, account: sessionTokenAccount)
    }

    /// Retrieves the stored session token.
    /// - Throws: `HeirloomsError.keychainNotFound` if not yet stored.
    public static func getSessionToken() throws -> String {
        let data = try getGenericPassword(account: sessionTokenAccount)
        guard let token = String(data: data, encoding: .utf8) else {
            throw HeirloomsError.keychainRead(errSecDecode)
        }
        return token
    }

    /// Deletes the stored session token.
    public static func deleteSessionToken() {
        deleteGenericPassword(account: sessionTokenAccount)
    }

    // MARK: - Master key

    /// Stores the 32-byte raw master key in the Keychain.
    /// The master key is derived at account activation (friend-invite flow) and is used
    /// to wrap keys for web-session pairing. It must never be overwritten by the plot key.
    public static func saveMasterKey(_ keyData: Data) throws {
        guard keyData.count == 32 else {
            throw HeirloomsError.encryptionFailed("Master key must be 32 bytes, got \(keyData.count)")
        }
        try saveGenericPassword(data: keyData, account: masterKeyAccount)
    }

    /// Retrieves the 32-byte master key.
    public static func getMasterKey() throws -> Data {
        try getGenericPassword(account: masterKeyAccount)
    }

    /// Deletes the master key (e.g. on account reset).
    public static func deleteMasterKey() {
        deleteGenericPassword(account: masterKeyAccount)
    }

    // MARK: - Plot key

    /// Stores the 32-byte raw plot key in the Keychain.
    public static func savePlotKey(_ keyData: Data) throws {
        guard keyData.count == 32 else {
            throw HeirloomsError.encryptionFailed("Plot key must be 32 bytes, got \(keyData.count)")
        }
        try saveGenericPassword(data: keyData, account: plotKeyAccount)
    }

    /// Retrieves the 32-byte plot key.
    public static func getPlotKey() throws -> Data {
        try getGenericPassword(account: plotKeyAccount)
    }

    /// Deletes the plot key (e.g. on "Reset shared plot").
    public static func deletePlotKey() {
        deleteGenericPassword(account: plotKeyAccount)
    }

    // MARK: - User ID

    /// Stores the server-assigned user UUID string.
    public static func saveUserId(_ userId: String) throws {
        let data = Data(userId.utf8)
        try saveGenericPassword(data: data, account: userIdAccount)
    }

    /// Retrieves the stored user UUID string.
    /// - Throws: `HeirloomsError.keychainNotFound` if not yet stored.
    public static func getUserId() throws -> String {
        let data = try getGenericPassword(account: userIdAccount)
        guard let id = String(data: data, encoding: .utf8) else {
            throw HeirloomsError.keychainRead(errSecDecode)
        }
        return id
    }

    /// Deletes the stored user ID.
    public static func deleteUserId() {
        deleteGenericPassword(account: userIdAccount)
    }

    // MARK: - Plot ID

    /// Stores the current shared plot UUID string.
    public static func savePlotId(_ plotId: String) throws {
        let data = Data(plotId.utf8)
        try saveGenericPassword(data: data, account: plotIdAccount)
    }

    /// Retrieves the stored plot UUID string.
    public static func getPlotId() throws -> String {
        let data = try getGenericPassword(account: plotIdAccount)
        guard let id = String(data: data, encoding: .utf8) else {
            throw HeirloomsError.keychainRead(errSecDecode)
        }
        return id
    }

    /// Deletes the stored plot ID.
    public static func deletePlotId() {
        deleteGenericPassword(account: plotIdAccount)
    }

    // MARK: - Generic password helpers

    private static func saveGenericPassword(data: Data, account: String) throws {
        // Try to update existing item first.
        let query: [String: Any] = [
            kSecClass as String:   kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: data,
        ]
        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)

        if updateStatus == errSecItemNotFound {
            // Item does not exist yet — insert.
            var addQuery = query
            addQuery[kSecValueData as String] = data
            addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
            if addStatus != errSecSuccess {
                throw HeirloomsError.keychainWrite(addStatus)
            }
        } else if updateStatus != errSecSuccess {
            throw HeirloomsError.keychainWrite(updateStatus)
        }
    }

    private static func getGenericPassword(account: String) throws -> Data {
        let query: [String: Any] = [
            kSecClass as String:            kSecClassGenericPassword,
            kSecAttrService as String:      service,
            kSecAttrAccount as String:      account,
            kSecReturnData as String:       true,
            kSecMatchLimit as String:       kSecMatchLimitOne,
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecSuccess:
            return (item as! Data) // swiftlint:disable:this force_cast
        case errSecItemNotFound:
            throw HeirloomsError.keychainNotFound
        default:
            throw HeirloomsError.keychainRead(status)
        }
    }

    private static func deleteGenericPassword(account: String) {
        let query: [String: Any] = [
            kSecClass as String:        kSecClassGenericPassword,
            kSecAttrService as String:  service,
            kSecAttrAccount as String:  account,
        ]
        SecItemDelete(query as CFDictionary)
    }

    private static func deleteSharingKey() {
        let query: [String: Any] = [
            kSecClass as String:              kSecClassKey,
            kSecAttrApplicationTag as String: sharingKeyTag,
            kSecAttrKeyType as String:        kSecAttrKeyTypeECSECPrimeRandom,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
