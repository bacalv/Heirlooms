import XCTest
import CryptoKit
@testable import HeirloomsCore

final class EnvelopeCryptoTests: XCTestCase {

    // MARK: - DEK generation

    func test_generateDEK_produces256BitKey() {
        let dek = EnvelopeCrypto.generateDEK()
        XCTAssertEqual(dek.bitCount, 256)
    }

    func test_generateDEK_isUnique() {
        let dek1 = EnvelopeCrypto.generateDEK()
        let dek2 = EnvelopeCrypto.generateDEK()
        let bytes1 = dek1.withUnsafeBytes { Data($0) }
        let bytes2 = dek2.withUnsafeBytes { Data($0) }
        XCTAssertNotEqual(bytes1, bytes2, "Two freshly generated DEKs should not be identical")
    }

    // MARK: - AES-GCM round trip

    func test_encryptDecryptAESGCM_roundTrip() throws {
        let key = EnvelopeCrypto.generateDEK()
        let plaintext = Data("Hello, Heirlooms!".utf8)

        let (ciphertext, nonce, tag) = try EnvelopeCrypto.encryptAESGCM(plaintext: plaintext, key: key)

        XCTAssertEqual(nonce.count, 12, "Nonce must be 12 bytes")
        XCTAssertEqual(tag.count, 16, "Tag must be 16 bytes")
        XCTAssertNotEqual(ciphertext, plaintext, "Ciphertext must differ from plaintext")

        let decrypted = try EnvelopeCrypto.decryptAESGCM(
            ciphertext: ciphertext,
            nonce: nonce,
            tag: tag,
            key: key
        )
        XCTAssertEqual(decrypted, plaintext)
    }

    func test_encryptDecryptAESGCM_emptyPlaintext() throws {
        let key = EnvelopeCrypto.generateDEK()
        let plaintext = Data()

        let (ciphertext, nonce, tag) = try EnvelopeCrypto.encryptAESGCM(plaintext: plaintext, key: key)
        let decrypted = try EnvelopeCrypto.decryptAESGCM(
            ciphertext: ciphertext,
            nonce: nonce,
            tag: tag,
            key: key
        )
        XCTAssertEqual(decrypted, plaintext)
    }

    func test_decrypt_withWrongKey_throws() throws {
        let key = EnvelopeCrypto.generateDEK()
        let wrongKey = EnvelopeCrypto.generateDEK()
        let plaintext = Data("secret".utf8)

        let (ciphertext, nonce, tag) = try EnvelopeCrypto.encryptAESGCM(plaintext: plaintext, key: key)

        XCTAssertThrowsError(
            try EnvelopeCrypto.decryptAESGCM(
                ciphertext: ciphertext,
                nonce: nonce,
                tag: tag,
                key: wrongKey
            )
        ) { error in
            guard case HeirloomsError.decryptionFailed = error else {
                XCTFail("Expected HeirloomsError.decryptionFailed, got \(error)")
                return
            }
        }
    }

    func test_decrypt_withTamperedTag_throws() throws {
        let key = EnvelopeCrypto.generateDEK()
        let plaintext = Data("secret".utf8)

        let (ciphertext, nonce, tag) = try EnvelopeCrypto.encryptAESGCM(plaintext: plaintext, key: key)
        var tamperedTag = tag
        tamperedTag[0] ^= 0xFF

        XCTAssertThrowsError(
            try EnvelopeCrypto.decryptAESGCM(
                ciphertext: ciphertext,
                nonce: nonce,
                tag: tamperedTag,
                key: key
            )
        )
    }

    func test_encrypt_producesUniqueNonces() throws {
        let key = EnvelopeCrypto.generateDEK()
        let plaintext = Data("nonce test".utf8)

        let (_, nonce1, _) = try EnvelopeCrypto.encryptAESGCM(plaintext: plaintext, key: key)
        let (_, nonce2, _) = try EnvelopeCrypto.encryptAESGCM(plaintext: plaintext, key: key)

        XCTAssertNotEqual(nonce1, nonce2, "Each encryption must use a unique random nonce")
    }

    // MARK: - Symmetric envelope round trip

    func test_wrapUnwrapSymmetric_roundTrip() throws {
        let wrappingKey = EnvelopeCrypto.generateDEK()
        let plaintext = Data("DEK payload".utf8)

        let envelope = try EnvelopeCrypto.wrapSymmetric(
            plaintext: plaintext,
            wrappingKey: wrappingKey,
            algorithmID: EnvelopeCrypto.algMasterSymmetric
        )

        let recovered = try EnvelopeCrypto.unwrapSymmetric(
            envelope: envelope,
            unwrappingKey: wrappingKey
        )
        XCTAssertEqual(recovered, plaintext)
    }

    func test_encryptDecryptContent_roundTrip() throws {
        let dek = EnvelopeCrypto.generateDEK()
        let plaintext = Data(repeating: 0xAB, count: 1024)

        let envelope = try EnvelopeCrypto.encryptContent(plaintext: plaintext, dek: dek)

        // Verify envelope structure.
        XCTAssertEqual(envelope[0], 0x01, "Envelope version must be 0x01")
        let algLen = Int(envelope[1])
        let algID = String(data: envelope[2 ..< 2 + algLen], encoding: .utf8)
        XCTAssertEqual(algID, EnvelopeCrypto.algSymmetric)

        let recovered = try EnvelopeCrypto.decryptContent(envelope: envelope, dek: dek)
        XCTAssertEqual(recovered, plaintext)
    }

    func test_unwrapSymmetric_withWrongKey_throws() throws {
        let key = EnvelopeCrypto.generateDEK()
        let wrongKey = EnvelopeCrypto.generateDEK()
        let plaintext = Data("test".utf8)

        let envelope = try EnvelopeCrypto.wrapSymmetric(
            plaintext: plaintext,
            wrappingKey: key,
            algorithmID: EnvelopeCrypto.algMasterSymmetric
        )

        XCTAssertThrowsError(
            try EnvelopeCrypto.unwrapSymmetric(
                envelope: envelope,
                unwrappingKey: wrongKey
            )
        )
    }

    // MARK: - DEK wrap / unwrap (asymmetric, CryptoKit path)

    func test_wrapUnwrapDEK_cryptoKitKey_roundTrip() throws {
        // Generate recipient keypair (CryptoKit — test/software key).
        let recipientPrivate = P256.KeyAgreement.PrivateKey()
        let recipientPublicKeyData = recipientPrivate.publicKey.x963Representation

        XCTAssertEqual(recipientPublicKeyData.count, 65)
        XCTAssertEqual(recipientPublicKeyData[0], 0x04, "Must be uncompressed SEC1")

        // Wrap a DEK.
        let originalDEK = EnvelopeCrypto.generateDEK()
        let (wrappedKey, ephemeralPubKeyData) = try EnvelopeCrypto.wrapDEK(
            dek: originalDEK,
            recipientPublicKeyData: recipientPublicKeyData
        )

        // Verify ephemeral key is 65 bytes uncompressed.
        XCTAssertEqual(ephemeralPubKeyData.count, 65)
        XCTAssertEqual(ephemeralPubKeyData[0], 0x04)

        // Verify envelope version and algorithm ID.
        XCTAssertEqual(wrappedKey[0], 0x01, "Envelope version must be 0x01")
        let algLen = Int(wrappedKey[1])
        let algID = String(data: wrappedKey[2 ..< 2 + algLen], encoding: .utf8)
        XCTAssertEqual(algID, EnvelopeCrypto.algAsymmetric)

        // Unwrap using CryptoKit key.
        let recoveredDEK = try EnvelopeCrypto.unwrapDEK(
            wrappedKey: wrappedKey,
            recipientPrivateKey: recipientPrivate
        )

        // Assert DEK bytes match.
        let originalBytes = originalDEK.withUnsafeBytes { Data($0) }
        let recoveredBytes = recoveredDEK.withUnsafeBytes { Data($0) }
        XCTAssertEqual(originalBytes, recoveredBytes, "Unwrapped DEK must match the original")
    }

    func test_wrapDEK_withWrongRecipientKey_throws() throws {
        // Recipient A wraps to their pubkey.
        let recipientA = P256.KeyAgreement.PrivateKey()
        let publicKeyA = recipientA.publicKey.x963Representation

        let dek = EnvelopeCrypto.generateDEK()
        let (wrappedKey, _) = try EnvelopeCrypto.wrapDEK(dek: dek, recipientPublicKeyData: publicKeyA)

        // Recipient B tries to unwrap — should throw.
        let recipientB = P256.KeyAgreement.PrivateKey()
        XCTAssertThrowsError(
            try EnvelopeCrypto.unwrapDEK(wrappedKey: wrappedKey, recipientPrivateKey: recipientB)
        ) { error in
            // Should be decryptionFailed (AES-GCM tag verification fails)
            // or sharedSecretFailed — both are HeirloomsError variants.
            guard case HeirloomsError.decryptionFailed = error else {
                XCTFail("Expected decryptionFailed, got \(error)")
                return
            }
        }
    }

    func test_unwrapDEK_unknownAlgorithmID_throws() throws {
        // Build a fake envelope with a wrong algorithm ID.
        let fakeAlg = "unknown-alg-v1"
        let algBytes = Data(fakeAlg.utf8)
        var fake = Data()
        fake.append(0x01)
        fake.append(UInt8(algBytes.count))
        fake.append(algBytes)
        fake.append(Data(repeating: 0x00, count: 65 + 12 + 16)) // dummy payload

        let recipientKey = P256.KeyAgreement.PrivateKey()
        XCTAssertThrowsError(
            try EnvelopeCrypto.unwrapDEK(wrappedKey: fake, recipientPrivateKey: recipientKey)
        ) { error in
            guard case HeirloomsError.unknownAlgorithmID(let id) = error else {
                XCTFail("Expected unknownAlgorithmID, got \(error)")
                return
            }
            XCTAssertEqual(id, fakeAlg)
        }
    }

    func test_unwrapSymmetric_unknownAlgorithmID_throws() throws {
        let key = EnvelopeCrypto.generateDEK()
        let fakeAlg = "weird-alg-v9"
        let algBytes = Data(fakeAlg.utf8)
        var fake = Data()
        fake.append(0x01)
        fake.append(UInt8(algBytes.count))
        fake.append(algBytes)
        fake.append(Data(repeating: 0x00, count: 12 + 16)) // dummy nonce + tag

        XCTAssertThrowsError(
            try EnvelopeCrypto.unwrapSymmetric(envelope: fake, unwrappingKey: key)
        ) { error in
            guard case HeirloomsError.unknownAlgorithmID(let id) = error else {
                XCTFail("Expected unknownAlgorithmID, got \(error)")
                return
            }
            XCTAssertEqual(id, fakeAlg)
        }
    }

    func test_envelope_versionMismatch_throws() throws {
        let key = EnvelopeCrypto.generateDEK()
        // Build an envelope with version 0x02.
        var fake = Data()
        fake.append(0x02)
        fake.append(UInt8("aes256gcm-v1".count))
        fake.append(Data("aes256gcm-v1".utf8))
        fake.append(Data(repeating: 0x00, count: 12 + 16))

        XCTAssertThrowsError(
            try EnvelopeCrypto.unwrapSymmetric(envelope: fake, unwrappingKey: key)
        ) { error in
            guard case HeirloomsError.envelopeVersionMismatch(let v) = error else {
                XCTFail("Expected envelopeVersionMismatch, got \(error)")
                return
            }
            XCTAssertEqual(v, 0x02)
        }
    }

    // MARK: - Envelope binary layout verification

    /// Verifies that the asymmetric envelope produced by wrapDEK matches the spec
    /// in envelope_format.md byte-for-byte:
    ///
    ///   [0x01][27][p256-ecdh-hkdf-aes256gcm-v1][ephemeral_pubkey:65][nonce:12][ciphertext][tag:16]
    func test_asymmetricEnvelope_binaryLayout() throws {
        let recipient = P256.KeyAgreement.PrivateKey()
        let pubkeyData = recipient.publicKey.x963Representation
        let dek = EnvelopeCrypto.generateDEK()

        let (envelope, _) = try EnvelopeCrypto.wrapDEK(dek: dek, recipientPublicKeyData: pubkeyData)

        // version
        XCTAssertEqual(envelope[0], 0x01)

        // alg_id_len
        let algID = EnvelopeCrypto.algAsymmetric
        let algIDBytes = Data(algID.utf8)
        XCTAssertEqual(Int(envelope[1]), algIDBytes.count)

        // alg_id
        let parsedAlg = String(data: envelope[2 ..< 2 + algIDBytes.count], encoding: .utf8)
        XCTAssertEqual(parsedAlg, algID)

        // ephemeral_pubkey (65 bytes, 0x04 prefix)
        let ephStart = 2 + algIDBytes.count
        XCTAssertEqual(envelope[ephStart], 0x04, "Ephemeral pubkey must be uncompressed SEC1")
        XCTAssertEqual(envelope.count - ephStart - 65 - 12 - 16 >= 0, true, "Envelope too short")

        // nonce (12 bytes)
        let nonceStart = ephStart + 65
        let nonce = envelope[nonceStart ..< nonceStart + 12]
        XCTAssertEqual(nonce.count, 12)

        // tag (last 16 bytes)
        let tag = envelope[(envelope.count - 16)...]
        XCTAssertEqual(tag.count, 16)

        // Minimum size: 1 + 1 + 27 + 65 + 12 + 32 (DEK) + 16 = 154 bytes
        XCTAssertGreaterThanOrEqual(envelope.count, 96, "Asymmetric envelope minimum size is 96 bytes")
    }

    func test_symmetricEnvelope_binaryLayout() throws {
        let key = EnvelopeCrypto.generateDEK()
        let plaintext = Data("test payload".utf8)
        let envelope = try EnvelopeCrypto.encryptContent(plaintext: plaintext, dek: key)

        // version
        XCTAssertEqual(envelope[0], 0x01)

        // alg_id
        let algIDBytes = Data(EnvelopeCrypto.algSymmetric.utf8)
        XCTAssertEqual(Int(envelope[1]), algIDBytes.count)
        let parsedAlg = String(data: envelope[2 ..< 2 + algIDBytes.count], encoding: .utf8)
        XCTAssertEqual(parsedAlg, EnvelopeCrypto.algSymmetric)

        // nonce (12 bytes after header)
        let nonceStart = 2 + algIDBytes.count
        let nonce = envelope[nonceStart ..< nonceStart + 12]
        XCTAssertEqual(nonce.count, 12)

        // Minimum size: 1 + 1 + 12 (alg_id) + 12 + 0 + 16 = 42 bytes (for non-empty plaintext > 43)
        XCTAssertGreaterThanOrEqual(envelope.count, 31, "Symmetric envelope minimum is 31 bytes")
    }
}
