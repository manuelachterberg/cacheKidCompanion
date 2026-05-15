import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = ImportViewModel()
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                if let mission = viewModel.importedMission {
                    MissionBuilderView(mission: mission)
                } else {
                    VStack(spacing: 16) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.gray)
                        
                        Text("Cache teilen")
                            .font(.title2)
                            .fontWeight(.bold)
                        
                        Text("Öffne eine Geocaching-App und teile einen Cache mit CacheKid.")
                            .multilineTextAlignment(.center)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                    }
                    .padding()
                }
            }
            .navigationTitle("CacheKid Host")
        }
        .onAppear {
            viewModel.checkForPendingShare()
        }
    }
}

struct MissionBuilderView: View {
    let mission: ActiveMission
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(mission.childTitle)
                .font(.title)
                .fontWeight(.bold)
            
            Text("Code: \(mission.cacheCode)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            Text(mission.summary)
                .font(.body)
            
            Divider()
            
            HStack {
                Image(systemName: "mappin.circle.fill")
                Text("Ziel: \(mission.target.latitude), \(mission.target.longitude)")
            }
            .font(.caption)
            .foregroundColor(.secondary)
            
            Spacer()
            
            Button("Mission vorbereiten") {
                // TODO: Trigger mission preparation
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
        }
        .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
