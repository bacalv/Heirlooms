import XCTest
@testable import HeirloomsCore

final class KeychainManagerTests: XCTestCase {

    // MARK: - setUp / tearDown

    override func setUp() {
        super.setUp()
        // Clean up before each test so prior state doesn't leak.
        KeychainManager.deleteMasterKey()
        KeychainManager.deletePlotKey()
    }

    override func tearDown() {
        KeychainManager.deleteMasterKey()
        KeychainManager.deletePlotKey()
        super.tearDown()
    }

    // MARK: - saveMasterKey / getMasterKey round-trip

    func test_saveMasterKey_andGetMasterKey_roundTrip() throws {
        let key = randomBytes(32)
        try KeychainManager.saveMasterKey(key)
        let retrieved = try KeychainManager.getMasterKey()
        XCTAssertEqual(retrieved, key)
    }

    func test_saveMasterKey_rejectsWrongLength() {
        let shortKey = randomBytes(16)
        XCTAssertThrowsError(try KeychainManager.saveMasterKey(shortKey))
    }

    func test_saveMasterKey_overwritesPreviousValue() throws {
        let first = randomBytes(32)
        let second = randomBytes(32)
        try KeychainManager.saveMasterKey(first)
        try KeychainManager.saveMasterKey(second)
        let retrieved = try KeychainManager.getMasterKey()
        XCTAssertEqual(retrieved, second)
    }

    func test_getMasterKey_throwsNotFoundWhenAbsent() {
        XCTAssertThrowsError(try KeychainManager.getMasterKey()) { error in
            XCTAssertEqual(error as? HeirloomsError, .keychainNotFound)
        }
    }

    // MARK: - deleteMasterKey

    func test_deleteMasterKey_removesStoredKey() throws {
        let key = randomBytes(32)
        try KeychainManager.saveMasterKey(key)
        KeychainManager.deleteMasterKey()
        XCTAssertThrowsError(try KeychainManager.getMasterKey()) { error in
            XCTAssertEqual(error as? HeirloomsError, .keychainNotFound)
        }
    }

    func test_deleteMasterKey_isIdempotentWhenNothingStored() {
        // Should not crash when called with no key present.
        KeychainManager.deleteMasterKey()
        KeychainManager.deleteMasterKey()
    }

    // MARK: - Slot isolation: master key and plot key are independent

    func test_masterKeyAndPlotKey_useDistinctSlots() throws {
        let masterBytes = randomBytes(32)
        let plotBytes   = randomBytes(32)

        try KeychainManager.saveMasterKey(masterBytes)
        try KeychainManager.savePlotKey(plotBytes)

        let retrievedMaster = try KeychainManager.getMasterKey()
        let retrievedPlot   = try KeychainManager.getPlotKey()

        XCTAssertEqual(retrievedMaster, masterBytes, "Master key slot must not be overwritten by plot key")
        XCTAssertEqual(retrievedPlot,   plotBytes,   "Plot key slot must not be overwritten by master key")
        XCTAssertNotEqual(retrievedMaster, retrievedPlot, "Master and plot keys must live in separate slots")
    }

    func test_deletePlotKey_doesNotDeleteMasterKey() throws {
        let masterBytes = randomBytes(32)
        try KeychainManager.saveMasterKey(masterBytes)
        try KeychainManager.savePlotKey(randomBytes(32))

        KeychainManager.deletePlotKey()

        // Master key must still be retrievable.
        let retrieved = try KeychainManager.getMasterKey()
        XCTAssertEqual(retrieved, masterBytes)
    }

    func test_deleteMasterKey_doesNotDeletePlotKey() throws {
        let plotBytes = randomBytes(32)
        try KeychainManager.saveMasterKey(randomBytes(32))
        try KeychainManager.savePlotKey(plotBytes)

        KeychainManager.deleteMasterKey()

        // Plot key must still be retrievable.
        let retrieved = try KeychainManager.getPlotKey()
        XCTAssertEqual(retrieved, plotBytes)
    }

    // MARK: - Helpers

    private func randomBytes(_ count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }
}
