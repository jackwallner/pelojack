import HealthKit
import SwiftUI
import WatchKit

@main
struct PelojackWatchApp: App {
    @WKApplicationDelegateAdaptor private var delegate: WatchDelegate
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            WatchView(workout: delegate.workout)
        }
        // Opening the app again re-arms it for the next ride.
        .onChange(of: phase) { _, now in
            if now == .active { delegate.workout.prepare() }
        }
    }
}

/// The iPhone opens the watch app with a workout configuration when a ride starts on the bike.
@MainActor
final class WatchDelegate: NSObject, WKApplicationDelegate {
    let workout = WorkoutManager()

    func handle(_ workoutConfiguration: HKWorkoutConfiguration) {
        workout.start()
    }
}
