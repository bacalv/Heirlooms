# HeirloomsiOS — Setup Guide

Complete instructions for opening the project in Xcode, installing on an iPad
with a free Apple ID, and re-signing after the 7-day certificate expiry.

---

## Prerequisites

| Tool | Version | Where to get |
|---|---|---|
| macOS | Sonoma 14+ recommended | System update |
| Xcode | 15.0+ | Mac App Store (free) |
| Apple ID | Any free Apple ID | appleid.apple.com |
| iPad or iPhone | iOS/iPadOS 16+ | — |

You do **not** need a paid Apple Developer Program membership to build and run
on your own iPad. The $99/yr membership is required only for TestFlight
distribution.

---

## Part 1 — Build and run (free sideloading)

### Step 1: Open the Swift package in Xcode

```
File → Open → HeirloomsiOS/
```

Xcode will resolve the Swift Package and index the `HeirloomsCore` library
target automatically.

### Step 2: Create the Xcode app target

The `HeirloomsApp/` directory contains SwiftUI source files but no `.xcodeproj`
yet. You need to create a new app target manually:

1. **File → New → Project → iOS → App**. Name it `HeirloomsApp`.
   - Interface: SwiftUI
   - Language: Swift
   - Bundle Identifier: `digital.heirlooms.ios.dev` (use any unique reverse-DNS string)
   - Uncheck "Include Tests" (tests live in the Swift package)
   - Save into: `HeirloomsiOS/` (the root of this repo checkout)

2. **Delete the auto-generated ContentView.swift** that Xcode creates.

3. **Add the existing source files** to the new target:
   - Drag `HeirloomsApp/App.swift` into the Xcode project navigator.
   - Drag `HeirloomsApp/Views/ActivateView.swift` → same target.
   - Drag `HeirloomsApp/Views/HomeView.swift` → same target.
   - Drag `HeirloomsApp/Views/FullScreenMediaView.swift` → same target.
   - For each file, confirm "Add to target: HeirloomsApp" in the dialog.

4. **Add HeirloomsCore as a package dependency**:
   - In the project navigator, click the top-level project file.
   - Select the `HeirloomsApp` target → **General → Frameworks, Libraries, and Embedded Content**.
   - Click **+** → **Add Other → Add Package Dependency**.
   - Choose **Add Local...** and navigate to `HeirloomsiOS/` (the directory containing `Package.swift`).
   - Select `HeirloomsCore` from the list of products.

5. **Add App Group entitlement** (needed for Share Extension, optional for now):
   - Target → **Signing & Capabilities → + Capability → App Groups**.
   - Add group: `group.digital.heirlooms`.

6. **Add URL scheme** so QR codes open the app:
   - Target → **Info → URL Types → +**.
   - Identifier: `digital.heirlooms.ios`
   - URL Schemes: `heirlooms`

### Step 3: Configure signing for free sideloading

1. In the **Signing & Capabilities** tab for the `HeirloomsApp` target:
   - Uncheck "Automatically manage signing" (only if prompted about conflicts).
   - Or keep it checked — Xcode will handle the free cert automatically.
   - **Team**: Choose your Apple ID name (shows as "Firstname Lastname (Personal Team)").
   - If no team appears: Xcode → Settings → Accounts → **+** → sign in with your Apple ID.

2. Xcode will create a free development certificate and a wildcard provisioning
   profile automatically. No action required beyond selecting your team.

### Step 4: Connect the iPad and install

1. Connect your iPad to your Mac with a USB cable (or enable WiFi device in
   Xcode → Devices if previously paired).
2. In the Xcode device picker (top toolbar), select your iPad.
3. The first time: the iPad will show a **Trust This Computer?** dialog. Tap Trust
   and enter your iPad passcode.
4. Press **Run** (Cmd-R) or **Product → Run**.
5. Xcode builds, signs, and installs the app. It may take a minute on first build.

### Step 5: Trust the developer on the iPad

The first time you try to open the app, iOS will block it with
"Untrusted Developer":

1. Go to **Settings → General → VPN & Device Management**.
2. Under **Developer App**, tap your Apple ID.
3. Tap **Trust "your@email.com"**.
4. Open the app normally.

---

## Part 2 — Running tests (`swift test`)

The `HeirloomsCore` library and its tests are a standard Swift package.
You can run tests without an attached device:

```bash
cd /path/to/HeirloomsiOS
swift test
```

Or in Xcode: **Product → Test** (Cmd-U) with the `HeirloomsCoreTests` scheme
selected and "My Mac" as the destination.

---

## Part 3 — Re-signing after the 7-day expiry

Free-tier certificates expire after 7 days. The app will stop launching on
device with "App could not be installed" or simply crash to Springboard.

**To re-sign:**

1. Reconnect the iPad to your Mac.
2. Open the same Xcode project.
3. Press **Run** (Cmd-R).

Xcode will automatically:
- Revoke the expired certificate.
- Generate a new one.
- Re-sign the app bundle.
- Re-install over the existing app.

Your app data (Keychain items with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`,
`UserDefaults`, files in the app container) is **preserved** across re-signing
as long as the Bundle Identifier does not change.

**Important:** You are limited to 3 app IDs per free Apple ID at any time.
If you hit this limit, delete an unused app ID at
[developer.apple.com/account](https://developer.apple.com/account) → Certificates,
Identifiers & Profiles → Identifiers.

---

## Part 4 — Upgrading to paid Apple Developer Program

When you are ready to distribute via TestFlight:

1. Enrol at [developer.apple.com/enroll](https://developer.apple.com/enroll).
   Cost: £79/yr (or regional equivalent).
2. In Xcode Signing & Capabilities, change Team from "Personal Team" to the paid team.
3. Change the Bundle Identifier from `digital.heirlooms.ios.dev` to
   `digital.heirlooms.ios` (or any ID registered in your paid account).
4. **Product → Archive → Distribute App → TestFlight**.
5. Add the canonical user as an External Tester in App Store Connect.

**Keychain data compatibility note:** Keychain items stored under the development
Bundle ID (`...ios.dev`) will not be accessible under the production Bundle ID
(`...ios`). If switching Bundle IDs, the user will need to re-activate
(re-scan both QR codes). Plan for this at distribution time.

---

## Part 5 — Share Extension setup

The Share Extension is a separate Xcode target. To add it:

1. **File → New → Target → Share Extension**.
   - Product Name: `HeirloomsShareExtension`
   - Bundle ID: `digital.heirlooms.ios.dev.share` (must have the main app ID as prefix)
2. Add `HeirloomsCore` as a framework dependency to this new target (same as step 2.4 above).
3. Add the App Group entitlement (`group.digital.heirlooms`) to **both** the main app target
   and the Share Extension target — they must match exactly.
4. Replace the auto-generated `ShareViewController.swift` with the real implementation
   (see `HeirloomsApp/ShareExtension/ShareViewController.swift` once that file is written).

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| "No matching provisioning profile found" | Delete the app from the iPad and re-run from Xcode |
| "App could not be installed — code 0xe8008015" | Free cert expired; re-run from Xcode to renew |
| Xcode shows "Untrusted Developer" after re-install | Trust again in Settings → General → VPN & Device Management |
| `swift test` fails with "No such module 'CryptoKit'" | Ensure you are running `swift test` inside `HeirloomsiOS/`; CryptoKit is available on macOS 10.15+ |
| "Cannot find type 'SymmetricKey'" | macOS SDK is too old; update Xcode |
| Build error in `ActivateView.swift` about `CC_SHA256` | CommonCrypto is only available in the app target; ensure `ActivateView.swift` is in the app target, not in `Sources/HeirloomsCore/` |

---

## Directory layout reference

```
HeirloomsiOS/
├── Package.swift                    ← Swift Package (HeirloomsCore library + tests)
├── SETUP.md                         ← this file
├── Sources/
│   └── HeirloomsCore/
│       ├── Crypto/
│       │   ├── KeychainManager.swift
│       │   └── EnvelopeCrypto.swift
│       ├── Networking/
│       │   ├── HeirloomsAPI.swift
│       │   └── BackgroundUploadManager.swift
│       └── Models/
│           └── Models.swift
├── Tests/
│   └── HeirloomsCoreTests/
│       ├── EnvelopeCryptoTests.swift
│       └── APIClientTests.swift
└── HeirloomsApp/                    ← Xcode app target sources (added manually)
    ├── App.swift
    └── Views/
        ├── ActivateView.swift
        ├── HomeView.swift
        └── FullScreenMediaView.swift
```
