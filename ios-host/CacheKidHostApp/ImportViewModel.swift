import Foundation
import Combine

class ImportViewModel: ObservableObject {
    @Published var pendingShareText: String?
    @Published var isImporting: Bool = false
    @Published var importedMission: ActiveMission?
    @Published var errorMessage: String?
    
    private let sharedDefaults = UserDefaults(suiteName: "group.com.cachekid.companion")
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        NotificationCenter.default.publisher(for: .init("ImportTriggered"))
            .sink { [weak self] _ in
                self?.checkForPendingShare()
            }
            .store(in: &cancellables)
    }
    
    func checkForPendingShare() {
        guard let text = sharedDefaults?.string(forKey: "pendingShareContent"),
              let timestamp = sharedDefaults?.double(forKey: "pendingShareTimestamp"),
              Date().timeIntervalSince1970 - timestamp < 300 else {
            return
        }
        
        pendingShareText = text
        sharedDefaults?.removeObject(forKey: "pendingShareContent")
        sharedDefaults?.removeObject(forKey: "pendingShareTimestamp")
        
        parseSharedContent(text)
    }
    
    private func parseSharedContent(_ text: String) {
        isImporting = true
        
        // TODO: Implement actual parsing (GPX, Geocaching API response, etc.)
        // For now, create a placeholder mission
        let mission = ActiveMission(
            missionId: UUID().uuidString,
            cacheCode: "GC12345",
            sourceTitle: "Imported Cache",
            childTitle: "Neuer Cache",
            summary: "Shared from Geocaching App",
            target: MissionTarget(latitude: 52.52, longitude: 13.405),
            routeOrigin: nil,
            waypoints: []
        )
        
        importedMission = mission
        isImporting = false
    }
}
