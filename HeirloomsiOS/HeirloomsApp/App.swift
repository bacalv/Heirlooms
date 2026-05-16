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
    /// SEC-015: whether the vault requires biometric authentication. Fetched from server on login.
    @Published var requireBiometric: Bool = false
    /// SEC-015: true once the user has passed the biometric gate in the current session.
    @Published var biometricPassed: Bool = false

    init() {
        refreshPhase()
        // Load cached biometric setting.
        requireBiometric = UserDefaults.standard.bool(forKey: "require_biometric")
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

    /// SEC-015: fetch require_biometric from server and cache in UserDefaults.
    func syncBiometricSetting() async {
        guard let _ = try? KeychainManager.getSessionToken() else { return }
        do {
            let api = HeirloomsAPI()
            let account = try await api.getAccount()
            await MainActor.run {
                requireBiometric = account.requireBiometric
                UserDefaults.standard.set(account.requireBiometric, forKey: "require_biometric")
                // If biometric was turned off remotely, clear the gate.
                if !account.requireBiometric { biometricPassed = true }
            }
        } catch {
            // Network failure — keep cached value.
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
            // SEC-015: show biometric gate before vault when setting is enabled.
            if appState.requireBiometric && !appState.biometricPassed {
                BiometricGateView {
                    appState.biometricPassed = true
                }
                .task { await appState.syncBiometricSetting() }
            } else {
                HomeView(plotId: plotId)
                    .task { await appState.syncBiometricSetting() }
            }
        }
    }
}

// MARK: - Import stubs (app target imports HeirloomsCore)
// When building the Xcode app target, add HeirloomsCore as a framework dependency.
// The Swift Package resolves `BackgroundUploadManager`, `KeychainManager`, etc.
import HeirloomsCore
