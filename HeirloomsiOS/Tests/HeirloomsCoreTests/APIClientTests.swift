import XCTest
@testable import HeirloomsCore

// MARK: - Mock URLProtocol

/// Intercepts URLSession requests in tests, returning canned responses.
final class MockURLProtocol: URLProtocol {

    /// Set this before each test to define the response.
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            XCTFail("MockURLProtocol.requestHandler not set")
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

// MARK: - Helpers

private func makeMockSession() -> URLSession {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [MockURLProtocol.self]
    return URLSession(configuration: config)
}

private func makeClient(baseURL: URL = HeirloomsAPI.defaultBaseURL) -> HeirloomsAPI {
    HeirloomsAPI(baseURL: baseURL, session: makeMockSession())
}

private func httpResponse(url: URL, status: Int) -> HTTPURLResponse {
    HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!
}

// MARK: - URL construction tests

final class APIClientURLTests: XCTestCase {

    func test_initiateUpload_callsCorrectURL() async throws {
        let base = URL(string: "https://api.heirlooms.digital")!
        let client = makeClient(baseURL: base)

        var capturedURL: URL?
        MockURLProtocol.requestHandler = { request in
            capturedURL = request.url
            let json = #"{"storageKey":"sk1","uploadUrl":"https://gcs.example/sk1","thumbnailStorageKey":"tsk1","thumbnailUploadUrl":"https://gcs.example/tsk1"}"#
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        _ = try await client.initiateUpload(filename: "test.jpg", contentType: "image/jpeg", plotId: nil)

        XCTAssertEqual(capturedURL?.path, "/api/content/uploads/initiate")
        XCTAssertEqual(capturedURL?.host, "api.heirlooms.digital")
    }

    func test_listPlotItems_callsCorrectURL() async throws {
        let base = URL(string: "https://api.heirlooms.digital")!
        let client = makeClient(baseURL: base)

        var capturedURL: URL?
        MockURLProtocol.requestHandler = { request in
            capturedURL = request.url
            let json = #"{"items":[],"next_cursor":null}"#
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        _ = try await client.listPlotItems(plotId: "plot-uuid-123")

        XCTAssertEqual(capturedURL?.path, "/api/content/uploads")
        XCTAssertNotNil(capturedURL?.query?.contains("plot_id=plot-uuid-123"))
    }

    func test_confirmUpload_callsCorrectURL() async throws {
        let base = URL(string: "https://api.heirlooms.digital")!
        let client = makeClient(baseURL: base)

        var capturedURL: URL?
        MockURLProtocol.requestHandler = { request in
            capturedURL = request.url
            return (httpResponse(url: request.url!, status: 201), Data())
        }

        let wrappedDek = Data(repeating: 0x01, count: 32)
        try await client.confirmUpload(
            storageKey: "sk1",
            thumbnailStorageKey: nil,
            mimeType: "image/jpeg",
            fileSize: 1024,
            wrappedDEK: wrappedDek,
            wrappedThumbDEK: nil
        )

        XCTAssertEqual(capturedURL?.path, "/api/content/uploads/confirm")
    }

    func test_fetchItem_callsCorrectURL() async throws {
        let base = URL(string: "https://api.heirlooms.digital")!
        let client = makeClient(baseURL: base)

        var capturedURL: URL?
        MockURLProtocol.requestHandler = { request in
            capturedURL = request.url
            return (httpResponse(url: request.url!, status: 200), Data("encrypted".utf8))
        }

        _ = try await client.fetchItem(uploadId: "upload-uuid-abc")

        XCTAssertEqual(capturedURL?.path, "/api/content/uploads/upload-uuid-abc/file")
    }

    func test_getPlotKey_callsCorrectURL() async throws {
        let base = URL(string: "https://api.heirlooms.digital")!
        let client = makeClient(baseURL: base)

        var capturedURL: URL?
        MockURLProtocol.requestHandler = { request in
            capturedURL = request.url
            let json = #"{"wrappedPlotKey":"AAAA","plotKeyFormat":"p256-ecdh-hkdf-aes256gcm-v1"}"#
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        _ = try await client.getPlotKey(plotId: "my-plot-id")

        XCTAssertEqual(capturedURL?.path, "/api/plots/my-plot-id/plot-key")
    }
}

// MARK: - X-Api-Key header tests

final class APIClientAuthHeaderTests: XCTestCase {

    private var client: HeirloomsAPI!
    private let testToken = "test-session-token-xyz"

    override func setUpWithError() throws {
        try super.setUpWithError()
        // Store a test token in the Keychain before each test.
        try KeychainManager.saveSessionToken(testToken)
        client = makeClient()
    }

    override func tearDownWithError() throws {
        KeychainManager.deleteSessionToken()
        try super.tearDownWithError()
    }

    func test_listPlotItems_sendsApiKeyHeader() async throws {
        var capturedHeaders: [String: String]?
        MockURLProtocol.requestHandler = { request in
            capturedHeaders = request.allHTTPHeaderFields
            let json = #"{"items":[],"next_cursor":null}"#
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        _ = try await client.listPlotItems(plotId: "any-plot")

        XCTAssertEqual(capturedHeaders?["X-Api-Key"], testToken,
                       "X-Api-Key header must match stored session token")
    }

    func test_initiateUpload_sendsApiKeyHeader() async throws {
        var capturedHeaders: [String: String]?
        MockURLProtocol.requestHandler = { request in
            capturedHeaders = request.allHTTPHeaderFields
            let json = #"{"storageKey":"sk","uploadUrl":"https://gcs/sk"}"#
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        _ = try await client.initiateUpload(filename: "f.jpg", contentType: "image/jpeg")

        XCTAssertEqual(capturedHeaders?["X-Api-Key"], testToken)
    }

    func test_fetchItem_sendsApiKeyHeader() async throws {
        var capturedHeaders: [String: String]?
        MockURLProtocol.requestHandler = { request in
            capturedHeaders = request.allHTTPHeaderFields
            return (httpResponse(url: request.url!, status: 200), Data("bytes".utf8))
        }

        _ = try await client.fetchItem(uploadId: "uid")

        XCTAssertEqual(capturedHeaders?["X-Api-Key"], testToken)
    }
}

// MARK: - Response parsing tests

final class APIClientResponseParsingTests: XCTestCase {

    private var client: HeirloomsAPI!

    override func setUp() {
        super.setUp()
        client = makeClient()
    }

    func test_listPlotItems_parsesItems() async throws {
        let sampleJSON = """
        {
            "items": [
                {
                    "id": "item-1",
                    "uploaded_at": "2026-05-14T10:00:00Z",
                    "mime_type": "image/jpeg",
                    "wrapped_dek": "AAAAAA==",
                    "dek_format": "master-aes256gcm-v1",
                    "wrapped_thumbnail_dek": null,
                    "thumbnail_dek_format": null,
                    "thumbnail_storage_key": "thumb-1",
                    "storage_key": "content-1",
                    "uploader_user_id": "user-abc"
                },
                {
                    "id": "item-2",
                    "uploaded_at": "2026-05-14T11:00:00Z",
                    "mime_type": "video/mp4",
                    "wrapped_dek": "BBBBBB==",
                    "dek_format": "master-aes256gcm-v1",
                    "wrapped_thumbnail_dek": null,
                    "thumbnail_dek_format": null,
                    "thumbnail_storage_key": null,
                    "storage_key": "content-2",
                    "uploader_user_id": "user-xyz"
                }
            ],
            "next_cursor": "cursor-xyz"
        }
        """

        MockURLProtocol.requestHandler = { request in
            return (httpResponse(url: request.url!, status: 200), Data(sampleJSON.utf8))
        }

        let items = try await client.listPlotItems(plotId: "plot-1")

        XCTAssertEqual(items.count, 2)

        let first = items[0]
        XCTAssertEqual(first.id, "item-1")
        XCTAssertEqual(first.contentType, "image/jpeg")
        XCTAssertEqual(first.wrappedDek, "AAAAAA==")
        XCTAssertEqual(first.storageKey, "content-1")
        XCTAssertEqual(first.uploaderUserId, "user-abc")

        let second = items[1]
        XCTAssertEqual(second.id, "item-2")
        XCTAssertEqual(second.contentType, "video/mp4")
        XCTAssertNil(second.thumbnailStorageKey)
    }

    func test_listPlotItems_emptyPage() async throws {
        let json = #"{"items":[],"next_cursor":null}"#
        MockURLProtocol.requestHandler = { request in
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        let items = try await client.listPlotItems(plotId: "empty-plot")
        XCTAssertTrue(items.isEmpty)
    }

    func test_listPlotItems_http401_throws() async throws {
        MockURLProtocol.requestHandler = { request in
            let json = #"{"error":"Unauthorized"}"#
            return (httpResponse(url: request.url!, status: 401), Data(json.utf8))
        }

        do {
            _ = try await client.listPlotItems(plotId: "plot")
            XCTFail("Expected networkError to be thrown")
        } catch HeirloomsError.networkError(let code, _) {
            XCTAssertEqual(code, 401)
        }
    }

    func test_listPlotItems_http500_throws() async throws {
        MockURLProtocol.requestHandler = { request in
            return (httpResponse(url: request.url!, status: 500), Data("Server error".utf8))
        }

        do {
            _ = try await client.listPlotItems(plotId: "plot")
            XCTFail("Expected networkError to be thrown")
        } catch HeirloomsError.networkError(let code, _) {
            XCTAssertEqual(code, 500)
        }
    }

    func test_initiateUpload_parsesTicket() async throws {
        let json = """
        {
            "storageKey": "ck-abc",
            "uploadUrl": "https://gcs.example.com/ck-abc",
            "thumbnailStorageKey": "tk-abc",
            "thumbnailUploadUrl": "https://gcs.example.com/tk-abc"
        }
        """
        MockURLProtocol.requestHandler = { request in
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        let ticket = try await client.initiateUpload(filename: "photo.jpg", contentType: "image/jpeg")

        XCTAssertEqual(ticket.storageKey, "ck-abc")
        XCTAssertEqual(ticket.uploadUrl, "https://gcs.example.com/ck-abc")
        XCTAssertEqual(ticket.thumbnailStorageKey, "tk-abc")
        XCTAssertEqual(ticket.thumbnailUploadUrl, "https://gcs.example.com/tk-abc")
    }

    func test_initiateUpload_missingStorageKey_throws() async throws {
        let json = #"{"uploadUrl":"https://gcs.example.com/ck"}"#
        MockURLProtocol.requestHandler = { request in
            return (httpResponse(url: request.url!, status: 200), Data(json.utf8))
        }

        do {
            _ = try await client.initiateUpload(filename: "f.jpg", contentType: "image/jpeg")
            XCTFail("Expected decodingError")
        } catch HeirloomsError.decodingError {
            // expected
        }
    }
}
