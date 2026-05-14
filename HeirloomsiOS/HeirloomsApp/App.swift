import SwiftUI

/// Entry point for the HeirloomsApp target.
///
/// This file must be added to the HeirloomsApp Xcode target (not the Swift Package).
/// See SETUP.md for instructions.
@main
struct HeirloomsApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
        }
    }
}

// MARK: - AppDelegate

/// Handles background URLSession events forwarded by the OS.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        BackgroundUploadManager.shared.handleEventsForBackgroundURLSession(
            identifier: identifier,
            completionHandler: completionHandler
        )
    }
}

// MARK: - AppState

/// Observable object driving the root navigation decision.
final class AppState: ObservableObject {

    enum Phase {
        /// No session token stored.
        case needsActivation
        /// Session token stored but no shared plot bound yet.
        case needsPlotScan
        /// Fully activated — session + plot key in Keychain.
        case ready(plotId: String)
    }

    @Published var phase: Phase = .needsActivation

    init() {
        refreshPhase()
    }

    func refreshPhase() {
        if let _ = try? KeychainManager.getSessionToken(),
           let plotId = try? KeychainManager.getPlotId() {
            phase = .ready(plotId: plotId)
        } else if let _ = try? KeychainManager.getSessionToken() {
            phase = .needsPlotScan
        } else {
            phase = .needsActivation
        }
    }
}

// MARK: - RootView

/// Selects the correct root view based on `AppState.phase`.
struct RootView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        switch appState.phase {
        case .needsActivation:
            ActivateView()
        case .needsPlotScan:
            ActivateView(scanMode: .plotInvite)
        case .ready(let plotId):
            HomeView(plotId: plotId)
        }
    }
}

// MARK: - Import stubs (app target imports HeirloomsCore)
// When building the Xcode app target, add HeirloomsCore as a framework dependency.
// The Swift Package resolves `BackgroundUploadManager`, `KeychainManager`, etc.
import HeirloomsCore
