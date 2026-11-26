import UIKit
import SwiftUI
import StoreApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // the backend's address, from the launch environment (deploy.sh --backend-url, a UI test), as in the native app
        let baseUrl = ProcessInfo.processInfo.environment["BACKEND_URL"] ?? "http://localhost:8080"
        return MainViewControllerKt.MainViewController(baseUrl: baseUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
