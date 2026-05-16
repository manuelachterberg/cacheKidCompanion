import Foundation

/// Represents a geocache target position
struct MissionTarget: Codable, Equatable {
    let latitude: Double
    let longitude: Double
}

/// A waypoint along the route
struct MissionWaypoint: Codable, Equatable {
    let latitude: Double
    let longitude: Double
}

/// Full mission definition shared between host and kid
struct ActiveMission: Codable, Identifiable {
    var id: String { missionId }
    let missionId: String
    let cacheCode: String
    let sourceTitle: String
    let childTitle: String
    let summary: String
    let target: MissionTarget
    let routeOrigin: MissionTarget?
    let waypoints: [MissionWaypoint]
}

/// Snapshot of player location
struct LocationSnapshot: Codable {
    let latitude: Double
    let longitude: Double
}

/// Package sent from host to kid device
struct MissionPackage: Codable {
    let mission: ActiveMission
    let routePoints: [MissionWaypoint]
    let createdAt: Date
    let version: String
}
