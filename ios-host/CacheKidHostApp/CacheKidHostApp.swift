import SwiftUI

@main
struct CacheKidHostApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
    var appDelegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        if url.scheme == "cachekid" && url.host == "import" {
            NotificationCenter.default.post(name: .init("ImportTriggered"), object: nil)
            return true
        }
        return false
    }
    
    func applicationDidBecomeActive(_ application: UIApplication) {
        print("[AppDelegate] applicationDidBecomeActive")
        NotificationCenter.default.post(name: .init("CheckPendingShare"), object: nil)
    }
}
