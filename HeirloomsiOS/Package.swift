// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "HeirloomsiOS",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),  // macOS target allows `swift test` on a Mac without an iPhone connected
    ],
    products: [
        .library(name: "HeirloomsCore", targets: ["HeirloomsCore"]),
    ],
    targets: [
        .target(
            name: "HeirloomsCore",
            path: "Sources/HeirloomsCore"
        ),
        .testTarget(
            name: "HeirloomsCoreTests",
            dependencies: ["HeirloomsCore"],
            path: "Tests/HeirloomsCoreTests"
        ),
    ]
)
