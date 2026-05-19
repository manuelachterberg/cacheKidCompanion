import Foundation
import Combine

class ImportViewModel: ObservableObject {
    @Published var pendingShareText: String?
    @Published var isImporting: Bool = false
    @Published var importedMission: ActiveMission?
    @Published var errorMessage: String?
    
    private let appGroupIdentifier = "group.com.cachekid.companion"
    private let pendingShareFilename = "pending-share.json"
    private var lastProcessedContent: String?
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        print("[ImportViewModel] INIT")
        NotificationCenter.default.publisher(for: .init("ImportTriggered"))
            .sink { [weak self] _ in
                self?.checkForPendingShare()
            }
            .store(in: &cancellables)
    }
    
    func checkForPendingShare() {
        print("[ImportViewModel] === CHECKING ===")
        
        let text = readPendingShareFromFile() ?? readPendingShareFromUserDefaults()
        guard let content = text else {
            print("[ImportViewModel] No pending share found.")
            return
        }
        
        if content == lastProcessedContent {
            print("[ImportViewModel] Already processed, skipping.")
            return
        }
        
        print("[ImportViewModel] Found share (length: \(content.count)): \(content.prefix(300))")
        pendingShareText = content
        lastProcessedContent = content
        parseSharedContent(content)
        clearPendingShare()
    }
    
    private func readPendingShareFromFile() -> String? {
        print("[ImportViewModel] readPendingShareFromFile called")
        
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) else {
            print("[ImportViewModel] App group container NOT available")
            return nil
        }
        let fileURL = containerURL.appendingPathComponent(pendingShareFilename)
        
        print("[ImportViewModel] Container: \(containerURL.path)")
        print("[ImportViewModel] File exists: \(FileManager.default.fileExists(atPath: fileURL.path))")
        
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return nil
        }
        
        guard let data = try? Data(contentsOf: fileURL),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let text = json["content"] as? String,
              let timestamp = json["timestamp"] as? TimeInterval else {
            print("[ImportViewModel] Failed to parse file")
            return nil
        }
        
        let age = Date().timeIntervalSince1970 - timestamp
        guard age < 300 else {
            print("[ImportViewModel] Too old (\(age)s)")
            return nil
        }
        
        print("[ImportViewModel] Read from file (age: \(age)s)")
        return text
    }
    
    private func readPendingShareFromUserDefaults() -> String? {
        print("[ImportViewModel] readPendingShareFromUserDefaults called")
        
        let sharedDefaults = UserDefaults(suiteName: appGroupIdentifier)
        guard let text = sharedDefaults?.string(forKey: "pendingShareContent"),
              let timestamp = sharedDefaults?.double(forKey: "pendingShareTimestamp"),
              Date().timeIntervalSince1970 - timestamp < 300 else {
            return nil
        }
        print("[ImportViewModel] Read from UserDefaults")
        return text
    }
    
    private func clearPendingShare() {
        let sharedDefaults = UserDefaults(suiteName: appGroupIdentifier)
        sharedDefaults?.removeObject(forKey: "pendingShareContent")
        sharedDefaults?.removeObject(forKey: "pendingShareTimestamp")
        
        if let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) {
            let fileURL = containerURL.appendingPathComponent(pendingShareFilename)
            try? FileManager.default.removeItem(at: fileURL)
            print("[ImportViewModel] Cleared share file")
        }
    }
    
    private func parseSharedContent(_ text: String) {
        isImporting = true
        
        let parser = SharedCacheParser()
        let result = parser.parse(text)
        
        print("[ImportViewModel] Parse status: \(result.status)")
        
        if let importData = result.value {
            print("[ImportViewModel] Extracted code='\(importData.cacheCode ?? "nil")' title='\(importData.sourceTitle ?? "nil")'")
            
            let mission = ActiveMission(
                missionId: UUID().uuidString,
                cacheCode: importData.cacheCode ?? "GC?????",
                sourceTitle: importData.sourceTitle ?? "Unbekannter Cache",
                childTitle: importData.sourceTitle ?? "Unbekannter Cache",
                summary: "Folge der Karte bis zum grossen X.",
                target: importData.target ?? MissionTarget(latitude: 52.52, longitude: 13.405),
                routeOrigin: nil,
                waypoints: []
            )
            importedMission = mission
            print("[ImportViewModel] Set mission: \(mission.cacheCode)")
        } else {
            errorMessage = result.messages.joined(separator: "\n")
            print("[ImportViewModel] No data extracted: \(result.messages)")
        }
        
        isImporting = false
    }
}

// MARK: - Shared Cache Parser (ported from Android SharedCacheParser)

struct SharedCacheImport {
    let rawText: String
    let cacheCode: String?
    let sourceTitle: String?
    let target: MissionTarget?
    let sourceApp: String?
}

enum SharedCacheImportStatus: CustomStringConvertible {
    case success
    case partial
    case invalid
    
    var description: String {
        switch self {
        case .success: return "success"
        case .partial: return "partial"
        case .invalid: return "invalid"
        }
    }
}

struct SharedCacheImportResult {
    let status: SharedCacheImportStatus
    let value: SharedCacheImport?
    let messages: [String]
}

class SharedCacheParser {
    
    private let cacheCodeRegex = try! NSRegularExpression(pattern: #"\bGC[A-Z0-9]+\b"#, options: .caseInsensitive)
    private let coordInfoRegex = try! NSRegularExpression(
        pattern: #"https?://(?:www\.)?coord\.info/(GC[A-Z0-9]+)"#,
        options: .caseInsensitive
    )
    private let decimalCoordinateRegex = try! NSRegularExpression(
        pattern: #"(-?\d{1,2}\.\d{4,})\s*,\s*(-?\d{1,3}\.\d{4,})"#
    )
    private let directionalCoordinateRegex = try! NSRegularExpression(
        pattern: #"([NS])\s*(\d{1,2})[°\s]+(\d{1,2}\.\d+)\s*[, ]+\s*([EW])\s*(\d{1,3})[°\s]+(\d{1,2}\.\d+)"#,
        options: .caseInsensitive
    )
    
    func parse(_ sharedText: String, sourceApp: String? = nil) -> SharedCacheImportResult {
        let normalizedText = sharedText.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard !normalizedText.isEmpty else {
            return SharedCacheImportResult(
                status: .invalid,
                value: nil,
                messages: ["Shared cache payload is empty."]
            )
        }
        
        let cacheCode = extractCacheCode(normalizedText)
        let title = extractTitle(normalizedText, cacheCode: cacheCode)
        let target = extractTarget(normalizedText)
        
        let importData = SharedCacheImport(
            rawText: normalizedText,
            cacheCode: cacheCode,
            sourceTitle: title,
            target: target,
            sourceApp: sourceApp
        )
        
        var messages: [String] = []
        if cacheCode == nil {
            messages.append("Cache code could not be detected.")
        }
        if title == nil {
            messages.append("Cache title could not be detected.")
        }
        if target == nil {
            messages.append("Target coordinates could not be detected.")
        }
        
        let status: SharedCacheImportStatus
        if messages.isEmpty {
            status = .success
        } else if cacheCode != nil || title != nil || target != nil {
            status = .partial
        } else {
            status = .invalid
        }
        
        return SharedCacheImportResult(
            status: status,
            value: status == .invalid ? nil : importData,
            messages: messages
        )
    }
    
    private func extractCacheCode(_ text: String) -> String? {
        let nsRange = NSRange(text.startIndex..., in: text)
        
        if let match = coordInfoRegex.firstMatch(in: text, options: [], range: nsRange) {
            if let range = Range(match.range(at: 1), in: text) {
                return String(text[range]).uppercased()
            }
        }
        
        if let match = cacheCodeRegex.firstMatch(in: text, options: [], range: nsRange) {
            if let range = Range(match.range, in: text) {
                return String(text[range]).uppercased()
            }
        }
        
        return nil
    }
    
    private func extractTitle(_ text: String, cacheCode: String?) -> String? {
        let lines = text.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .filter { !$0.hasPrefix("http://") && !$0.hasPrefix("https://") }
            .filter { line in
                guard let code = cacheCode else { return true }
                return line.caseInsensitiveCompare(code) != .orderedSame
            }
        
        return lines.first
    }
    
    private func extractTarget(_ text: String) -> MissionTarget? {
        let nsRange = NSRange(text.startIndex..., in: text)
        
        if let match = decimalCoordinateRegex.firstMatch(in: text, options: [], range: nsRange) {
            if let latRange = Range(match.range(at: 1), in: text),
               let lonRange = Range(match.range(at: 2), in: text),
               let latitude = Double(text[latRange]),
               let longitude = Double(text[lonRange]) {
                let target = MissionTarget(latitude: latitude, longitude: longitude)
                if target.isValid {
                    return target
                }
            }
        }
        
        if let match = directionalCoordinateRegex.firstMatch(in: text, options: [], range: nsRange) {
            if let latDirRange = Range(match.range(at: 1), in: text),
               let latDegRange = Range(match.range(at: 2), in: text),
               let latMinRange = Range(match.range(at: 3), in: text),
               let lonDirRange = Range(match.range(at: 4), in: text),
               let lonDegRange = Range(match.range(at: 5), in: text),
               let lonMinRange = Range(match.range(at: 6), in: text) {
                
                let latDir = String(text[latDirRange])
                let latDeg = Double(text[latDegRange])
                let latMin = Double(text[latMinRange])
                let lonDir = String(text[lonDirRange])
                let lonDeg = Double(text[lonDegRange])
                let lonMin = Double(text[lonMinRange])
                
                if let latitude = directionalToDecimal(direction: latDir, degrees: latDeg, minutes: latMin),
                   let longitude = directionalToDecimal(direction: lonDir, degrees: lonDeg, minutes: lonMin) {
                    let target = MissionTarget(latitude: latitude, longitude: longitude)
                    if target.isValid {
                        return target
                    }
                }
            }
        }
        
        return nil
    }
    
    private func directionalToDecimal(direction: String, degrees: Double?, minutes: Double?) -> Double? {
        guard let deg = degrees, let min = minutes else { return nil }
        
        let absolute = abs(deg) + (min / 60.0)
        switch direction.uppercased() {
        case "N", "E": return absolute
        case "S", "W": return -absolute
        default: return nil
        }
    }
}

extension MissionTarget {
    var isValid: Bool {
        latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
    }
}
