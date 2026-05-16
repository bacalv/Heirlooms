import Foundation
import CryptoKit
import Security

/// Implements the Heirlooms envelope format (envelope_format.md, Version 1).
///
/// Two envelope variants:
///
/// **Symmetric** (`aes256gcm-v1`, `master-aes256gcm-v1`):
/// ```
/// [0x01][alg_id_len][alg_id][nonce:12][ciphertext][tag:16]
/// ```
///
/// **Asymmetric** (`p256-ecdh-hkdf-aes256gcm-v1`):
/// ```
/// [0x01][alg_id_len][alg_id][ephemeral_pubkey:65][nonce:12][ciphertext][tag:16]
/// ```
public enum EnvelopeCrypto {

    // MARK: - Algorithm ID constants

    /// Symmetric content encryption.
    public static let algSymmetric      = "aes256gcm-v1"
    /// DEK wrap under master key.
    public static let algMasterSymmetric = "master-aes256gcm-v1"
    /// DEK wrap under shared plot key.
    public static let algPlotSymmetric  = "plot-aes256gcm-v1"
    /// Master key / DEK wrap to device pubkey via P-256 ECDH.
    public static let algAsymmetric     = "p256-ecdh-hkdf-aes256gcm-v1"

    private static let envelopeVersion: UInt8 = 0x01

    // MARK: - DEK generation

    /// Generates a fresh 256-bit data encryption key using the platform CSPRNG.
    public static func generateDEK() -> SymmetricKey {
        SymmetricKey(size: .bits256)
    }

    // MARK: - AES-256-GCM primitives

    /// Encrypts `plaintext` with AES-256-GCM.
    ///
    /// CryptoKit generates a cryptographically random 12-byte nonce.
    /// Never pass a deterministic nonce — see envelope spec.
    ///
    /// - Returns: `(ciphertext, nonce, tag)` — each as separate `Data` values.
    /// - Throws: `HeirloomsError.encryptionFailed` on failure.
    public static func encryptAESGCM(
        plaintext: Data,
        key: SymmetricKey
    ) throws -> (ciphertext: Data, nonce: Data, tag: Data) {
        do {
            let sealedBox = try AES.GCM.seal(plaintext, using: key)
            let nonceData = Data(sealedBox.nonce)
            return (sealedBox.ciphertext, nonceData, sealedBox.tag)
        } catch {
            throw HeirloomsError.encryptionFailed(error.localizedDescription)
        }
    }

    /// Decrypts a ciphertext produced by `encryptAESGCM`.
    ///
    /// - Throws: `HeirloomsError.decryptionFailed` if the tag does not verify or
    ///   the nonce is not exactly 12 bytes.
    public static func decryptAESGCM(
        ciphertext: Data,
        nonce nonceData: Data,
        tag: Data,
        key: SymmetricKey
    ) throws -> Data {
        guard nonceData.count == 12 else {
            throw HeirloomsError.decryptionFailed("Nonce must be 12 bytes, got \(nonceData.count)")
        }
        do {
            let nonce = try AES.GCM.Nonce(data: nonceData)
            let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            return try AES.GCM.open(sealedBox, using: key)
        } catch let e as HeirloomsError {
            throw e
        } catch {
            throw HeirloomsError.decryptionFailed(error.localizedDescription)
        }
    }

    // MARK: - Symmetric envelope serialisation

    /// Wraps a plaintext (typically a DEK's raw bytes) into a **symmetric** envelope.
    ///
    /// Used for `aes256gcm-v1` (content) and `master-aes256gcm-v1` (DEK-under-master-key).
    ///
    /// Output format:
    /// ```
    /// [0x01][alg_id_len][alg_id bytes][nonce:12][ciphertext][tag:16]
    /// ```
    public static func wrapSymmetric(
        plaintext: Data,
        wrappingKey: SymmetricKey,
        algorithmID: String = algMasterSymmetric
    ) throws -> Data {
        let algBytes = Data(algorithmID.utf8)
        guard algBytes.count <= 64 else {
            throw HeirloomsError.encryptionFailed("Algorithm ID exceeds 64 bytes")
        }
        let (ciphertext, nonce, tag) = try encryptAESGCM(plaintext: plaintext, key: wrappingKey)

        var envelope = Data()
        envelope.append(envelopeVersion)               // 1 byte
        envelope.append(UInt8(algBytes.count))          // 1 byte
        envelope.append(algBytes)                       // N bytes
        envelope.append(nonce)                          // 12 bytes
        envelope.append(ciphertext)                     // variable
        envelope.append(tag)                            // 16 bytes
        return envelope
    }

    /// Unwraps a **symmetric** envelope, returning the plaintext.
    ///
    /// - Parameter expectedAlgorithmID: If non-nil, the parsed algorithm ID must match
    ///   exactly. Provide `nil` to accept any known symmetric algorithm.
    /// - Throws: `HeirloomsError.envelopeVersionMismatch` if version != 0x01.
    /// - Throws: `HeirloomsError.unknownAlgorithmID` if algorithm ID is not recognised.
    /// - Throws: `HeirloomsError.envelopeTooShort` if bytes are insufficient.
    public static func unwrapSymmetric(
        envelope: Data,
        unwrappingKey: SymmetricKey,
        expectedAlgorithmID: String? = nil
    ) throws -> Data {
        let (algID, headerLen) = try parseSymmetricHeader(envelope: envelope)

        if let expected = expectedAlgorithmID, algID != expected {
            throw HeirloomsError.unknownAlgorithmID(algID)
        }
        guard algID == algSymmetric || algID == algMasterSymmetric || algID == algPlotSymmetric else {
            throw HeirloomsError.unknownAlgorithmID(algID)
        }

        // After header: [nonce:12][ciphertext][tag:16]
        let remaining = envelope.count - headerLen
        guard remaining >= 28 else { // 12 + 0 + 16 minimum
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }

        let nonceStart = headerLen
        let nonceData = envelope[nonceStart ..< nonceStart + 12]
        let payloadEnd = envelope.count - 16
        let ciphertext = envelope[nonceStart + 12 ..< payloadEnd]
        let tag = envelope[payloadEnd...]

        return try decryptAESGCM(
            ciphertext: Data(ciphertext),
            nonce: Data(nonceData),
            tag: Data(tag),
            key: unwrappingKey
        )
    }

    // MARK: - Asymmetric envelope (DEK wrap / unwrap)

    /// Wraps `dek` (raw 32-byte symmetric key) to `recipientPublicKeyData` using:
    /// `p256-ecdh-hkdf-aes256gcm-v1`.
    ///
    /// Algorithm:
    /// 1. Generate ephemeral P-256 keypair (software, exportable).
    /// 2. ECDH: `sharedSecret = ECDH(ephemeralPrivate, recipientPublic)`.
    /// 3. HKDF-SHA256 (no salt, empty info) → 32-byte wrapping key.
    /// 4. AES-256-GCM encrypt DEK bytes.
    /// 5. Serialise as asymmetric envelope.
    ///
    /// - Parameter recipientPublicKeyData: 65-byte uncompressed P-256 point (0x04 || x || y).
    /// - Returns: `(wrappedKey: Data, ephemeralPublicKeyData: Data)`.
    ///   `wrappedKey` is the complete binary asymmetric envelope ready to base64-encode
    ///   and send to the server. `ephemeralPublicKeyData` is the 65-byte SEC1 uncompressed
    ///   point (already embedded inside `wrappedKey`; provided separately for convenience).
    /// - Throws: `HeirloomsError.invalidPublicKey`, `HeirloomsError.sharedSecretFailed`,
    ///   or `HeirloomsError.encryptionFailed`.
    public static func wrapDEK(
        dek: SymmetricKey,
        recipientPublicKeyData: Data
    ) throws -> (wrappedKey: Data, ephemeralPublicKeyData: Data) {
        guard recipientPublicKeyData.count == 65, recipientPublicKeyData[0] == 0x04 else {
            throw HeirloomsError.invalidPublicKey(
                "Expected 65-byte uncompressed P-256 point, got \(recipientPublicKeyData.count) bytes"
            )
        }

        // Import recipient public key.
        let recipientPublicKey: P256.KeyAgreement.PublicKey
        do {
            recipientPublicKey = try P256.KeyAgreement.PublicKey(x963Representation: recipientPublicKeyData)
        } catch {
            throw HeirloomsError.invalidPublicKey("Failed to import recipient public key: \(error.localizedDescription)")
        }

        // Generate ephemeral P-256 keypair (software key — must be exportable for ECDH via CryptoKit).
        let ephemeralPrivate = P256.KeyAgreement.PrivateKey()
        let ephemeralPublicKeyData = ephemeralPrivate.publicKey.x963Representation

        // ECDH.
        let sharedSecret: SharedSecret
        do {
            sharedSecret = try ephemeralPrivate.sharedSecretFromKeyAgreement(with: recipientPublicKey)
        } catch {
            throw HeirloomsError.sharedSecretFailed(error.localizedDescription)
        }

        // HKDF-SHA256, no salt, empty info → 32 bytes.
        let wrapKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data(),
            sharedInfo: Data(),
            outputByteCount: 32
        )

        // AES-GCM encrypt the raw DEK bytes.
        var dekBytes = dek.withUnsafeBytes { Data($0) }
        defer { dekBytes.resetBytes(in: 0..<dekBytes.count) }
        let (ciphertext, nonce, tag) = try encryptAESGCM(plaintext: dekBytes, key: wrapKey)

        // Build asymmetric envelope.
        let algBytes = Data(algAsymmetric.utf8)
        var envelope = Data()
        envelope.append(envelopeVersion)               // 1 byte
        envelope.append(UInt8(algBytes.count))          // 1 byte: 27
        envelope.append(algBytes)                       // 27 bytes
        envelope.append(ephemeralPublicKeyData)         // 65 bytes (SEC1 uncompressed)
        envelope.append(nonce)                          // 12 bytes
        envelope.append(ciphertext)                     // 32 bytes (DEK) → produces 32 bytes ciphertext
        envelope.append(tag)                            // 16 bytes

        return (envelope, Data(ephemeralPublicKeyData))
    }

    /// Unwraps a `p256-ecdh-hkdf-aes256gcm-v1` envelope using the recipient's private key.
    ///
    /// Supports both:
    /// - **CryptoKit private keys** (software keys, e.g. generated in tests).
    /// - **Secure Enclave keys** (via `SecKeyCreateSharedSecret` raw API path).
    ///
    /// For Secure Enclave keys, pass `secKeyPrivate`; for CryptoKit keys, pass `cryptoKitPrivate`.
    ///
    /// - Throws: `HeirloomsError` variants on parse, ECDH, or decryption failure.
    public static func unwrapDEK(
        wrappedKey: Data,
        recipientPrivateKey: SecKey
    ) throws -> SymmetricKey {
        let (algID, ephemeralPubKeyData, nonce, ciphertext, tag) =
            try parseAsymmetricEnvelope(wrappedKey)

        guard algID == algAsymmetric else {
            throw HeirloomsError.unknownAlgorithmID(algID)
        }

        // Derive shared secret via Security framework (works for Secure Enclave keys too).
        guard let recipientPublicKey = SecKeyCopyPublicKey(recipientPrivateKey) else {
            throw HeirloomsError.sharedSecretFailed("Could not derive public key from private key")
        }

        // Import the ephemeral public key as a SecKey for ECDH.
        let ephemeralPubSecKey: SecKey
        do {
            ephemeralPubSecKey = try secKeyFromUncompressedP256(ephemeralPubKeyData)
        } catch {
            throw HeirloomsError.invalidPublicKey("Cannot import ephemeral public key: \(error.localizedDescription)")
        }

        // ECDH via Security framework.
        var error: Unmanaged<CFError>?
        guard let sharedSecretCF = SecKeyCopyKeyExchangeResult(
            recipientPrivateKey,
            .ecdhKeyExchangeStandard,   // produces the raw X-coordinate (RFC 2631 shared secret)
            ephemeralPubSecKey,
            [:] as CFDictionary,
            &error
        ) else {
            let msg = error?.takeRetainedValue().localizedDescription ?? "SecKeyCopyKeyExchangeResult failed"
            throw HeirloomsError.sharedSecretFailed(msg)
        }
        let sharedSecretData = sharedSecretCF as Data

        // HKDF-SHA256, no salt, empty info → 32 bytes (matches CryptoKit SharedSecret.hkdfDerivedSymmetricKey).
        let wrapKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: sharedSecretData),
            salt: Data(),
            info: Data(),
            outputByteCount: 32
        )

        // Decrypt the DEK.
        let dekBytes = try decryptAESGCM(
            ciphertext: ciphertext,
            nonce: nonce,
            tag: tag,
            key: wrapKey
        )

        guard dekBytes.count == 32 else {
            throw HeirloomsError.decryptionFailed("Unwrapped DEK has unexpected length: \(dekBytes.count) bytes")
        }

        return SymmetricKey(data: dekBytes)
    }

    /// Convenience overload accepting a CryptoKit `P256.KeyAgreement.PrivateKey` (for tests
    /// and software key paths where the key is not in Secure Enclave).
    public static func unwrapDEK(
        wrappedKey: Data,
        recipientPrivateKey: P256.KeyAgreement.PrivateKey
    ) throws -> SymmetricKey {
        let (algID, ephemeralPubKeyData, nonce, ciphertext, tag) =
            try parseAsymmetricEnvelope(wrappedKey)

        guard algID == algAsymmetric else {
            throw HeirloomsError.unknownAlgorithmID(algID)
        }

        let ephemeralPublicKey: P256.KeyAgreement.PublicKey
        do {
            ephemeralPublicKey = try P256.KeyAgreement.PublicKey(x963Representation: ephemeralPubKeyData)
        } catch {
            throw HeirloomsError.invalidPublicKey(error.localizedDescription)
        }

        let sharedSecret: SharedSecret
        do {
            sharedSecret = try recipientPrivateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)
        } catch {
            throw HeirloomsError.sharedSecretFailed(error.localizedDescription)
        }

        let wrapKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data(),
            sharedInfo: Data(),
            outputByteCount: 32
        )

        let dekBytes = try decryptAESGCM(
            ciphertext: ciphertext,
            nonce: nonce,
            tag: tag,
            key: wrapKey
        )

        guard dekBytes.count == 32 else {
            throw HeirloomsError.decryptionFailed("Unwrapped DEK has unexpected length: \(dekBytes.count) bytes")
        }

        return SymmetricKey(data: dekBytes)
    }

    // MARK: - Full content encryption (symmetric envelope)

    /// Encrypts raw content bytes and returns a complete symmetric envelope (`aes256gcm-v1`).
    ///
    /// This is the format used for encrypted file content and thumbnail blobs.
    public static func encryptContent(plaintext: Data, dek: SymmetricKey) throws -> Data {
        try wrapSymmetric(plaintext: plaintext, wrappingKey: dek, algorithmID: algSymmetric)
    }

    /// Decrypts a symmetric envelope produced by `encryptContent`.
    public static func decryptContent(envelope: Data, dek: SymmetricKey) throws -> Data {
        try unwrapSymmetric(envelope: envelope, unwrappingKey: dek, expectedAlgorithmID: algSymmetric)
    }

    // MARK: - Private parsing helpers

    /// Parses the common prefix of a symmetric envelope and returns
    /// `(algorithmID, headerLength)` where `headerLength` is the byte offset
    /// of the first payload byte (nonce start).
    private static func parseSymmetricHeader(envelope: Data) throws -> (String, Int) {
        // Minimum: version(1) + alg_id_len(1) + alg_id(1) + nonce(12) + tag(16) = 31
        guard envelope.count >= 31 else {
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }
        let version = envelope[0]
        guard version == envelopeVersion else {
            throw HeirloomsError.envelopeVersionMismatch(version)
        }
        let algLen = Int(envelope[1])
        guard algLen >= 1, algLen <= 64 else {
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }
        let algEnd = 2 + algLen
        guard envelope.count >= algEnd else {
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }
        guard let algID = String(data: envelope[2 ..< algEnd], encoding: .utf8) else {
            throw HeirloomsError.unknownAlgorithmID("<invalid UTF-8>")
        }
        return (algID, algEnd)
    }

    /// Parses a complete asymmetric envelope, returning all components.
    ///
    /// Returns: `(algID, ephemeralPubKey, nonce, ciphertext, tag)`.
    private static func parseAsymmetricEnvelope(
        _ envelope: Data
    ) throws -> (String, Data, Data, Data, Data) {
        // Minimum asymmetric envelope: 1 + 1 + 1 + 65 + 12 + 0 + 16 = 96
        guard envelope.count >= 96 else {
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }
        let version = envelope[0]
        guard version == envelopeVersion else {
            throw HeirloomsError.envelopeVersionMismatch(version)
        }
        let algLen = Int(envelope[1])
        guard algLen >= 1, algLen <= 64 else {
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }
        let algEnd = 2 + algLen
        guard envelope.count >= algEnd + 65 + 12 + 16 else {
            throw HeirloomsError.envelopeTooShort(envelope.count)
        }
        guard let algID = String(data: envelope[2 ..< algEnd], encoding: .utf8) else {
            throw HeirloomsError.unknownAlgorithmID("<invalid UTF-8>")
        }

        let ephemeralStart = algEnd
        let ephemeralPubKey = Data(envelope[ephemeralStart ..< ephemeralStart + 65])

        let nonceStart = ephemeralStart + 65
        let nonce = Data(envelope[nonceStart ..< nonceStart + 12])

        let payloadEnd = envelope.count - 16
        let ciphertext = Data(envelope[nonceStart + 12 ..< payloadEnd])
        let tag = Data(envelope[payloadEnd...])

        return (algID, ephemeralPubKey, nonce, ciphertext, tag)
    }

    /// Converts a 65-byte uncompressed P-256 point to a `SecKey` for use with
    /// `SecKeyCopyKeyExchangeResult`.
    private static func secKeyFromUncompressedP256(_ keyData: Data) throws -> SecKey {
        guard keyData.count == 65, keyData[0] == 0x04 else {
            throw HeirloomsError.invalidPublicKey("Expected 65-byte uncompressed P-256 point")
        }
        let attributes: [String: Any] = [
            kSecAttrKeyType as String:        kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeyClass as String:       kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits as String:  256,
        ]
        var error: Unmanaged<CFError>?
        guard let secKey = SecKeyCreateWithData(keyData as CFData, attributes as CFDictionary, &error) else {
            let msg = error?.takeRetainedValue().localizedDescription ?? "SecKeyCreateWithData failed"
            throw HeirloomsError.invalidPublicKey(msg)
        }
        return secKey
    }
}
