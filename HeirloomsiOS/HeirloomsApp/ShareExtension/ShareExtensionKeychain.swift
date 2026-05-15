import Foundation
import Security
import CryptoKit

/// Keychain access for the Share Extension process.
///
/// The Share Extension runs in a separate process from the main app. It reads
/// the session token, plot key, and plot ID using the **shared Keychain access
/// group** (`digital.heirlooms.keychain`). Both the main app target and the
/// `HeirloomsShare` extension target must declare this group in their
/// entitlements files under `keychain-access-groups`.
///
/// Items written by the main app (via `KeychainManager`) use the service name
/// `digital.heirlooms.ios` and `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.
/// We read them here using the same query attributes plus `kSecAttrAccessGroup`.
///
/// **Important:** A Share Extension activates while the device is unlocked
/// (the user initiated the share action from the share sheet). Keychain items
/// with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` are therefore available.
final class ShareExtensionKeychain {

    // MARK: - Constants

    /// Must match `KeychainManager.service` in HeirloomsCore.
    private static let service = "digital.heirlooms.ios"

    /// The shared Keychain access group declared in both targets' entitlements.
    /// Format: `<TeamID>.<group-name>` at runtime; in entitlements use the
    /// literal string and let Xcode substitute the team prefix.
    /// Here we use the app-identifier-based group so it works with free certs too.
    private static let keychainGroup = "digital.heirlooms.keychain"

    // MARK: - Account keys — must match KeychainManager's private constants

    private static let sessionTokenAccount = "session_token"
    private static let plotKeyAccount      = "plot_key"
    private static let plotIdAccount       = "plot_id"

    // MARK: - Public accessors

    /// Returns the stored session token, or `nil` if not found.
    static func getSessionToken() -> String? {
        guard let data = readItem(account: sessionTokenAccount) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Returns the 32-byte plot key as a `SymmetricKey`, or `nil` if not found or wrong length.
    static func getPlotKey() -> SymmetricKey? {
        guard let data = readItem(account: plotKeyAccount), data.count == 32 else { return nil }
        return SymmetricKey(data: data)
    }

    /// Returns the stored plot UUID string, or `nil` if not found.
    static func getPlotId() -> String? {
        guard let data = readItem(account: plotIdAccount) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    // MARK: - Private helpers

    private static func readItem(account: String) -> Data? {
        var query: [String: Any] = [
            kSecClass as String:           kSecClassGenericPassword,
            kSecAttrService as String:     service,
            kSecAttrAccount as String:     account,
            kSecReturnData as String:      true,
            kSecMatchLimit as String:      kSecMatchLimitOne,
            kSecAttrAccessGroup as String: keychainGroup,
        ]
        _ = query // suppress unused warning; dictionary is used in SecItemCopyMatching

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess, let data = item as? Data else {
            // Fallback: try without access group (works if the main app hasn't set one yet).
            var fallbackQuery: [String: Any] = [
                kSecClass as String:       kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account,
                kSecReturnData as String:  true,
                kSecMatchLimit as String:  kSecMatchLimitOne,
            ]
            _ = fallbackQuery
            var fallbackItem: CFTypeRef?
            let fallbackStatus = SecItemCopyMatching(fallbackQuery as CFDictionary, &fallbackItem)
            guard fallbackStatus == errSecSuccess, let fallbackData = fallbackItem as? Data else {
                return nil
            }
            return fallbackData
        }
        return data
    }
}
