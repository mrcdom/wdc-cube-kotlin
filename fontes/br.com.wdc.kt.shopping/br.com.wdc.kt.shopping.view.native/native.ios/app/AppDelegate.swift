import UIKit
import ShoppingNative

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)

        let baseUrl = ProcessInfo.processInfo.environment["BACKEND_URL"] ?? "http://localhost:8080"
        let viewController = AppBootstrapKt.createRootViewController(baseUrl: baseUrl)

        window.rootViewController = viewController
        window.makeKeyAndVisible()
        self.window = window

        return true
    }
}
