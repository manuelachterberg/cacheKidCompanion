import UIKit
import Social
import MobileCoreServices
import UniformTypeIdentifiers

class ShareViewController: SLComposeServiceViewController {
    
    private var sharedURL: URL?
    private var sharedText: String?
    
    private let appGroupIdentifier = "group.com.cachekid.companion"
    private let pendingShareFilename = "pending-share.json"
    
    override func isContentValid() -> Bool {
        return true
    }
    
    override func didSelectPost() {
        extractSharedContent { [weak self] in
            self?.saveSharedContent()
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
    
    private func saveSharedContent() {
        var parts: [String] = []
        if let text = sharedText, !text.isEmpty {
            parts.append(text)
        }
        if let url = sharedURL?.absoluteString, !url.isEmpty {
            parts.append(url)
        }
        
        guard !parts.isEmpty else { return }
        
        let combined = parts.joined(separator: "\n")
        
        let payload: [String: Any] = [
            "content": combined,
            "timestamp": Date().timeIntervalSince1970
        ]
        
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) else {
            print("[ShareExt] Failed to get app group container URL")
            return
        }
        
        let fileURL = containerURL.appendingPathComponent(pendingShareFilename)
        
        do {
            let data = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted])
            try data.write(to: fileURL, options: [.atomic])
            print("[ShareExt] Saved share content to: \(fileURL.path)")
        } catch {
            print("[ShareExt] Failed to write share content: \(error)")
        }
    }
}
