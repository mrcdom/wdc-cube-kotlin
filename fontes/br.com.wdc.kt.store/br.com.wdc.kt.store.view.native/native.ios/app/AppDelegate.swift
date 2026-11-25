import UIKit
import StoreNative

/**
 * The app: it starts the Kotlin side once, and its window lives in a scene (SceneDelegate) — iOS 27 refuses an app
 * that does not adopt the scene life cycle.
 */
@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    /**
     * The root view controller, made the first time a scene asks for it: the Kotlin application (its session, its
     * navigation) is created once, and a scene that connects again shows the same one.
     */
    lazy var rootViewController: UIViewController = {
        let baseUrl = ProcessInfo.processInfo.environment["BACKEND_URL"] ?? "http://localhost:8080"
        return AppBootstrapKt.createRootViewController(baseUrl: baseUrl)
    }()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
}
