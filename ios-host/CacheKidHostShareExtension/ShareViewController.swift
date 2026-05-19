import UIKit
import Social
import MobileCoreServices
import UniformTypeIdentifiers

class ShareViewController: SLComposeServiceViewController {
    
    private var sharedURL: URL?
    private var sharedText: String?
    
    override func isContentValid() -> Bool {
        return true
    }
    
    override func didSelectPost() {
        extractSharedContent { [weak self] in
            self?.processSharedContent()
            self?.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
        }
    }
    
    override func configurationItems() -> [Any]! {
        return []
    }
    
    private func extractSharedContent(completion: @escaping () -> Void) {
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = item.attachments else {
            completion()
            return
        }
        
        let group = DispatchGroup()
        
        for attachment in attachments {
            if attachment.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                group.enter()
                attachment.loadItem(forTypeIdentifier: UTType.url.identifier) { [weak self] item, _ in
                    if let url = item as? URL {
                        self?.sharedURL = url
                    }
                    group.leave()
                }
            }
            
            if attachment.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                group.enter()
                attachment.loadItem(forTypeIdentifier: UTType.plainText.identifier) { [weak self] item, _ in
                    if let text = item as? String {
                        self?.sharedText = text
                    }
                    group.leave()
                }
            }
        }
        
        group.notify(queue: .main) {
            completion()
        }
    }
    
    private func processSharedContent() {
        guard let text = sharedText ?? sharedURL?.absoluteString else { return }
        
        // Store shared content in shared UserDefaults for main app to pick up
        let sharedDefaults = UserDefaults(suiteName: "group.com.cachekid.companion")
        sharedDefaults?.set(text, forKey: "pendingShareContent")
        sharedDefaults?.set(Date().timeIntervalSince1970, forKey: "pendingShareTimestamp")
        
        // Open main app
        if let url = URL(string: "cachekid://import") {
            _ = openURL(url)
        }
    }
    
    @objc private func openURL(_ url: URL) -> Bool {
        var responder: UIResponder? = self
        while responder != nil {
            if let application = responder as? UIApplication {
                return application.perform(#selector(UIApplication.open(_:options:completionHandler:)), with: url, with: [:]) != nil
            }
            responder = responder?.next
        }
        return false
    }
}
