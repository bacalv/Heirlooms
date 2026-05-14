import UIKit
import SwiftUI
import Social
import UniformTypeIdentifiers
import HeirloomsCore

/// Entry point for the `HeirloomsShare` Share Extension target.
///
/// Xcode's Share Extension template generates a `ShareViewController` that
/// subclasses `SLComposeServiceViewController`. We instead subclass
/// `UIViewController` directly and host a SwiftUI view — this gives us a
/// full-screen modal with our own navigation stack and progress UI.
///
/// **How this is wired up:**
/// 1. In Xcode, after creating the target (File → New → Target → Share Extension),
///    replace the auto-generated `ShareViewController.swift` with this file.
/// 2. In `NSExtension → NSExtensionPrincipalClass` inside the extension's
///    `Info.plist`, set the value to `$(PRODUCT_MODULE_NAME).ShareViewController`.
///
/// **Supported types:** `public.image` and `public.movie`.
/// The extension's `Info.plist` NSExtensionActivationRule declares both UTIs.
final class ShareViewController: UIViewController {

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        // Guard: require a session token — if the user has never activated the
        // main app, we cannot upload anything. Show an error and bail out.
        guard ShareExtensionKeychain.getSessionToken() != nil else {
            showNotActivatedError()
            return
        }

        // Extract media from the extension context asynchronously, then present
        // the plot picker once we know what we are sharing.
        Task { await extractAndPresent() }
    }

    // MARK: - Media extraction

    private func extractAndPresent() async {
        var shareItems: [ShareItem] = []

        guard let extensionItems = extensionContext?.inputItems as? [NSExtensionItem] else {
            showExtractionError()
            return
        }

        for extensionItem in extensionItems {
            guard let attachments = extensionItem.attachments else { continue }
            for provider in attachments {
                if let result = await provider.loadImageData() {
                    shareItems.append(.image(data: result.data, mimeType: result.mimeType))
                } else if let result = await provider.loadMovieURL() {
                    shareItems.append(.video(url: result.url, mimeType: result.mimeType))
                }
            }
        }

        guard !shareItems.isEmpty else {
            showExtractionError()
            return
        }

        await MainActor.run {
            presentPickerView(items: shareItems)
        }
    }

    // MARK: - SwiftUI hosting

    private func presentPickerView(items: [ShareItem]) {
        let rootView = PlotPickerView(items: items) { [weak self] in
            self?.dismissExtension()
        }
        let hostingController = UIHostingController(rootView: rootView)
        hostingController.modalPresentationStyle = .pageSheet

        addChild(hostingController)
        view.addSubview(hostingController.view)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        hostingController.didMove(toParent: self)
    }

    // MARK: - Error states

    private func showNotActivatedError() {
        showErrorMessage(
            title: "Open Heirlooms first",
            message: "You need to activate the Heirlooms app before you can share photos."
        )
    }

    private func showExtractionError() {
        showErrorMessage(
            title: "Could not load media",
            message: "Heirlooms could not read the shared item. Please try again."
        )
    }

    private func showErrorMessage(title: String, message: String) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
                self?.dismissExtension()
            })
            self.present(alert, animated: true)
        }
    }

    // MARK: - Dismissal

    /// Completes and dismisses the extension, returning control to the host app.
    private func dismissExtension() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}
