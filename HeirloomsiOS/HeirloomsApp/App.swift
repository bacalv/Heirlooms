import SwiftUI

/// Entry point for the HeirloomsApp target.
///
/// This file must be added to the HeirloomsApp Xcode target (not the Swift Package).
/// See SETUP.md for instructions.
@main
struct HeirloomsApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .overlay {
                    // SEC-014: Show a privacy screen whenever the app is inactive or
                    // backgrounded, so the iOS app-switcher screenshot never captures
                    // vault content. Equivalent to Android FLAG_SECURE (SEC-009).
                    //
                    // scenePhase transitions to .inactive before the OS takes its
                    // screenshot, so the overlay is in place in time.
                    if scenePhase != .active {
                        PrivacyScreenView()
                            .ignoresSafeArea()
                            .transition(.opacity)
                    }
                }
                .animation(.easeInOut(duration: 0.15), value: scenePhase == .active)
        }
    }
}

// MARK: - PrivacyScreenView

/// Full-screen overlay shown when the app is inactive or backgrounded (SEC-014).
///
/// iOS takes an app-switcher screenshot at the moment the scene transitions to
/// `.inactive`. By rendering this view over all content whenever `scenePhase !=
/// .active`, we ensure the screenshot captures only the privacy screen rather than
/// any decrypted vault content.
///
/// This is the iOS equivalent of `FLAG_SECURE` on Android (SEC-009).
struct PrivacyScreenView: View {
    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.blue)

                Text("Heirlooms")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
            }
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
