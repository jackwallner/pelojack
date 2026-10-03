import SwiftUI

private let accent = Color(red: 1, green: 0.416, blue: 0.169)

struct WatchView: View {
    let workout: WorkoutManager

    var body: some View {
        switch workout.state {
        case .idle: idle
        case .running, .paused: riding
        case .saving: ProgressView("Saving")
        }
    }

    private var idle: some View {
        VStack(spacing: 10) {
            switch workout.link.state {
            case .connected:
                Image(systemName: "checkmark.circle.fill").font(.system(size: 34)).foregroundStyle(.green)
                Text("Connected to your bike").font(.headline)
                Text("Starts with your ride").font(.footnote).foregroundStyle(.secondary)
            case .searching:
                ProgressView().tint(accent)
                Text("Looking for your bike").font(.headline)
                Text("Hold your watch near the bike").font(.footnote).foregroundStyle(.secondary)
            case .bluetoothOff:
                Image(systemName: "antenna.radiowaves.left.and.right.slash").font(.system(size: 30)).foregroundStyle(.secondary)
                Text("Turn on Bluetooth").font(.headline)
            }
            Button("Start now") { workout.start() }
                .tint(accent)
        }
        .multilineTextAlignment(.center)
    }

    private var riding: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(clock(workout.elapsed(at: context.date)))
                        .font(.system(.title2, design: .rounded).weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(workout.state == .paused ? accent : .primary)
                }
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Image(systemName: "heart.fill").foregroundStyle(.red)
                    Text(workout.heartRate.map(String.init) ?? "--")
                        .font(.system(size: 44, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                    Text("bpm").font(.footnote).foregroundStyle(.secondary)
                }
                if workout.link.state != .connected {
                    Label("Bike not connected", systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(accent)
                }
                if let bike = workout.bike {
                    Text("\(bike.power) W  ·  \(bike.cadence) rpm  ·  \(Int(bike.kilojoules)) kJ")
                        .font(.footnote)
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }
                HStack {
                    Button(workout.state == .paused ? "Resume" : "Pause") {
                        workout.state == .paused ? workout.resume() : workout.pause()
                    }
                    Button("End") { workout.end() }
                        .tint(.red)
                }
                .padding(.top, 6)
            }
        }
    }

    private func clock(_ interval: TimeInterval) -> String {
        let seconds = Int(interval)
        return seconds >= 3600
            ? String(format: "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
            : String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
