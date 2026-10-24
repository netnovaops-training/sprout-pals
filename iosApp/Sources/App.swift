// The iPhone app's entry point. Everything you see is the shared Kotlin game (the Shared framework,
// built from ../shared by the Gradle step in project.yml); this file just puts it on screen.
import SwiftUI
import Shared

@main
struct SproutPalsApp: App {
    var body: some Scene {
        WindowGroup {
            GameView()
                .ignoresSafeArea(.keyboard)   // the garden-code keyboard slides over the game, not under it
        }
    }
}

/// Hosts the Compose Multiplatform screen (MainViewController in shared/src/iosMain).
struct GameView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosAppKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
