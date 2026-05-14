import Foundation

/// Manages background URLSession uploads for HeirloomsCore.
///
/// Background URLSession tasks survive app termination and screen lock.
/// Files are streamed from disk to avoid loading large videos into RAM.
///
/// Usage pattern:
/// ```swift
/// // In AppDelegate:
/// func application(_ application: UIApplication,
///                  handleEventsForBackgroundURLSession identifier: String,
///                  completionHandler: @escaping () -> Void) {
///     BackgroundUploadManager.shared.handleEventsForBackgroundURLSession(
///         identifier: identifier, completionHandler: completionHandler
///     )
/// }
/// ```
public final class BackgroundUploadManager: NSObject {

    // MARK: - Shared instance

    public static let shared = BackgroundUploadManager()

    // MARK: - Constants

    /// URLSession background identifier — must match the value in Info.plist for
    /// `BGTaskSchedulerPermittedIdentifiers` if background processing is added later.
    public static let backgroundSessionIdentifier = "digital.heirlooms.upload"

    /// App Group container identifier (must be enabled in entitlements).
    public static let appGroupIdentifier = "group.digital.heirlooms"

    // MARK: - State

    private lazy var backgroundSession: URLSession = {
        makeBackgroundSession()
    }()

    /// Completion handlers registered by the system when waking the app for
    /// background URL session events. Keyed by session identifier.
    private var backgroundCompletionHandlers: [String: () -> Void] = [:]

    /// Pending upload metadata indexed by task identifier (for delegate callbacks).
    private var pendingUploads: [Int: PendingUpload] = [:]

    private let lock = NSLock()

    // MARK: - Init

    private override init() {
        super.init()
        // Force lazy initialisation of the background session so we re-attach
        // to any in-progress tasks after an app relaunch.
        _ = backgroundSession
    }

    // MARK: - Public API

    /// Enqueues an encrypted file for background upload to the signed GCS URL.
    ///
    /// The file at `localURL` must already be the fully-encrypted envelope.
    /// It will be streamed from disk — the bytes are never loaded into RAM.
    ///
    /// - Parameters:
    ///   - localURL: Path to the encrypted envelope file on disk.
    ///   - ticket: `UploadTicket` from `HeirloomsAPI.initiateUpload(...)`.
    ///   - isThumbnail: Pass `true` to upload to `ticket.thumbnailUploadUrl`.
    /// - Returns: The enqueued `URLSessionUploadTask` (already resumed).
    @discardableResult
    public func enqueueUpload(
        localURL: URL,
        ticket: UploadTicket,
        isThumbnail: Bool = false
    ) -> URLSessionUploadTask {
        let uploadURLString = isThumbnail
            ? (ticket.thumbnailUploadUrl ?? ticket.uploadUrl)
            : ticket.uploadUrl

        var request = URLRequest(url: URL(string: uploadURLString)!)
        request.httpMethod = "PUT"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")

        let task = backgroundSession.uploadTask(with: request, fromFile: localURL)

        lock.lock()
        pendingUploads[task.taskIdentifier] = PendingUpload(
            ticket: ticket,
            isThumbnail: isThumbnail,
            localURL: localURL
        )
        lock.unlock()

        task.resume()
        return task
    }

    /// Called by `AppDelegate` when the system wakes the app to process
    /// background URLSession events.
    ///
    /// Stores the completion handler so `URLSessionDelegate.urlSessionDidFinishEvents`
    /// can call it after processing is complete.
    public func handleEventsForBackgroundURLSession(
        identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        guard identifier == Self.backgroundSessionIdentifier else { return }
        lock.lock()
        backgroundCompletionHandlers[identifier] = completionHandler
        lock.unlock()

        // Re-create the session if needed (in case it was deallocated).
        _ = backgroundSession
    }

    /// Adjusts whether uploads are allowed on cellular.
    ///
    /// Maps to `URLSessionConfiguration.allowsCellularAccess`.
    /// Takes effect for newly created tasks only; existing tasks are not affected.
    public func setAllowsCellular(_ allowed: Bool) {
        UserDefaults.standard.set(allowed, forKey: "heirlooms.allowCellular")
        // Recreate the session with updated config.
        backgroundSession = makeBackgroundSession()
    }

    // MARK: - Private

    private func makeBackgroundSession() -> URLSession {
        let config = URLSessionConfiguration.background(
            withIdentifier: Self.backgroundSessionIdentifier
        )
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        config.allowsCellularAccess = UserDefaults.standard.bool(forKey: "heirlooms.allowCellular")
        config.httpAdditionalHeaders = authHeaders()
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }

    private func authHeaders() -> [String: String] {
        var headers: [String: String] = [:]
        if let token = try? KeychainManager.getSessionToken() {
            headers["X-Api-Key"] = token
        }
        return headers
    }
}

// MARK: - URLSessionDelegate

extension BackgroundUploadManager: URLSessionDelegate, URLSessionTaskDelegate {

    /// Called when all enqueued background events have been delivered.
    /// Fires the stored completion handler so the system can suspend the app.
    public func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        let id = Self.backgroundSessionIdentifier
        lock.lock()
        let handler = backgroundCompletionHandlers.removeValue(forKey: id)
        lock.unlock()

        DispatchQueue.main.async {
            handler?()
        }
    }

    /// Called when a background upload task completes (success or failure).
    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        lock.lock()
        let pending = pendingUploads.removeValue(forKey: task.taskIdentifier)
        lock.unlock()

        guard let pending else { return }

        if let error {
            // Log failure. In production this would write to the Diagnostics log.
            print("[BackgroundUploadManager] Upload failed for \(pending.ticket.storageKey): \(error.localizedDescription)")
            return
        }

        let statusCode = (task.response as? HTTPURLResponse)?.statusCode ?? 0
        if (200..<300).contains(statusCode) {
            print("[BackgroundUploadManager] Upload succeeded for \(pending.ticket.storageKey) (status \(statusCode))")
            // Clean up the local temp file.
            try? FileManager.default.removeItem(at: pending.localURL)
        } else {
            print("[BackgroundUploadManager] Upload returned HTTP \(statusCode) for \(pending.ticket.storageKey)")
        }
    }
}

// MARK: - Supporting types

private struct PendingUpload {
    let ticket: UploadTicket
    let isThumbnail: Bool
    let localURL: URL
}
