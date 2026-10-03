import HealthKit
import Observation

/// Runs the watch's indoor cycling workout (for heart rate, rings and Apple Health), sends heart
/// rate straight to the bike, and follows the bike's ride: start, pause, resume and end.
@MainActor
@Observable
final class WorkoutManager: NSObject {
    enum State: Equatable { case idle, running, paused, saving }

    private(set) var state: State = .idle
    private(set) var heartRate: Int?
    private(set) var bike: (power: Int, cadence: Int, kilojoules: Double)?
    let link = BikeLink()

    @ObservationIgnored private let health = HKHealthStore()
    @ObservationIgnored private var session: HKWorkoutSession?
    @ObservationIgnored private var builder: HKLiveWorkoutBuilder?
    @ObservationIgnored private var startDate: Date?
    @ObservationIgnored private var meters: Int?
    /// The session has started its activity (it is not just prepared).
    @ObservationIgnored private var began = false
    @ObservationIgnored private var preparing = false
    /// The bike paused before the watch workout had started; applied once it has.
    @ObservationIgnored private var pausePending = false

    override init() {
        super.init()
        #if DEBUG
        // Simulator screenshots: show a ride in progress without starting a real workout.
        if ProcessInfo.processInfo.environment["PELOJACK_PREVIEW"] != nil {
            state = .running
            heartRate = 142
            bike = (186, 88, 214)
            return
        }
        #endif
        link.onCommand = { [weak self] command in self?.received(command) }
        link.start()
        prepare()
    }

    func elapsed(at date: Date) -> TimeInterval {
        builder?.elapsedTime(at: date) ?? 0
    }

    /// Asks for Health access and prepares a workout session as soon as the app opens. A prepared
    /// session keeps the app running with the wrist down, so the Bluetooth link stays up and the
    /// ride's start reaches the watch without anyone looking at it.
    func prepare() {
        guard session == nil, !preparing, state == .idle else { return }
        preparing = true
        Task {
            defer { preparing = false }
            try? await health.requestAuthorization(toShare: Self.shared, read: Self.read)
            guard session == nil, state == .idle, let session = makeSession() else { return }
            session.prepare()
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(Self.preparedLimit))
                self?.releaseIfUnused(session)
            }
        }
    }

    func start() {
        guard state == .idle else { return }
        state = .running
        Task {
            if session == nil {
                try? await health.requestAuthorization(toShare: Self.shared, read: Self.read)
            }
            begin()
        }
    }

    func pause() {
        guard state != .idle else { return }
        pausePending = true
        session?.pause()
    }

    func resume() {
        pausePending = false
        session?.resume()
    }

    func end(meters: Int? = nil) {
        guard let session, began, state != .idle, state != .saving else { return }
        self.meters = meters ?? self.meters
        state = .saving
        session.end()
    }

    private static let shared: Set<HKSampleType> = [HKObjectType.workoutType(), HKQuantityType(.activeEnergyBurned), HKQuantityType(.distanceCycling)]
    private static let read: Set<HKObjectType> = [HKQuantityType(.heartRate), HKQuantityType(.activeEnergyBurned)]
    /// A prepared session left unused this long is ended, so a forgotten app does not hold the
    /// heart rate sensor on all day.
    private static let preparedLimit: TimeInterval = 30 * 60

    private func makeSession() -> HKWorkoutSession? {
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = .cycling
        configuration.locationType = .indoor
        guard let session = try? HKWorkoutSession(healthStore: health, configuration: configuration) else { return nil }
        let builder = session.associatedWorkoutBuilder()
        builder.dataSource = HKLiveWorkoutDataSource(healthStore: health, workoutConfiguration: configuration)
        session.delegate = self
        builder.delegate = self
        self.session = session
        self.builder = builder
        began = false
        return session
    }

    private func releaseIfUnused(_ prepared: HKWorkoutSession) {
        guard session === prepared, !began else { return }
        session = nil
        builder = nil
        prepared.end()
    }

    private func begin() {
        guard let session = session ?? makeSession(), let builder else {
            state = .idle
            return
        }
        let now = Date()
        startDate = now
        meters = nil
        bike = nil
        began = true
        session.startActivity(with: now)
        builder.beginCollection(withStart: now) { _, _ in }
        if pausePending { session.pause() }
    }

    /// Adds the bike's distance, then saves the workout to Apple Health.
    private func finish(at end: Date) {
        guard let builder, began else { return }
        let distance = meters.flatMap { meters -> HKQuantitySample? in
            guard let startDate, meters > 0 else { return nil }
            return HKQuantitySample(
                type: HKQuantityType(.distanceCycling),
                quantity: HKQuantity(unit: .meter(), doubleValue: Double(meters)),
                start: startDate,
                end: end,
            )
        }
        Task {
            if let distance { try? await builder.addSamples([distance]) }
            try? await builder.endCollection(at: end)
            _ = try? await builder.finishWorkout()
            reset()
        }
    }

    private func reset() {
        session = nil
        builder = nil
        began = false
        pausePending = false
        state = .idle
        heartRate = nil
    }

    private func sessionChanged(_ changed: HKWorkoutSession, to state: HKWorkoutSessionState, at date: Date) {
        guard changed === session else { return }
        switch state {
        case .running: self.state = .running
        case .paused: self.state = .paused
        case .ended: finish(at: date)
        default: break
        }
    }

    private func sessionFailed(_ failed: HKWorkoutSession) {
        guard failed === session, !began else { return }
        reset()
    }

    private func collected(heartRate bpm: Int) {
        heartRate = bpm
        link.send(heartRate: bpm)
    }

    private func received(_ command: RideCommand) {
        switch command {
        case .start: start()
        case .pause: pause()
        case .resume: resume()
        case let .metrics(power, cadence, kilojoules, meters):
            bike = (power, cadence, kilojoules)
            self.meters = meters
        case let .end(_, _, meters): end(meters: meters)
        }
    }
}

extension WorkoutManager: HKWorkoutSessionDelegate {
    nonisolated func workoutSession(
        _ workoutSession: HKWorkoutSession,
        didChangeTo toState: HKWorkoutSessionState,
        from fromState: HKWorkoutSessionState,
        date: Date,
    ) {
        Task { @MainActor in sessionChanged(workoutSession, to: toState, at: date) }
    }

    nonisolated func workoutSession(_ workoutSession: HKWorkoutSession, didFailWithError error: Error) {
        Task { @MainActor in sessionFailed(workoutSession) }
    }
}

extension WorkoutManager: HKLiveWorkoutBuilderDelegate {
    nonisolated func workoutBuilder(_ workoutBuilder: HKLiveWorkoutBuilder, didCollectDataOf collectedTypes: Set<HKSampleType>) {
        let type = HKQuantityType(.heartRate)
        guard collectedTypes.contains(type),
              let quantity = workoutBuilder.statistics(for: type)?.mostRecentQuantity() else { return }
        let bpm = Int(quantity.doubleValue(for: .count().unitDivided(by: .minute())).rounded())
        Task { @MainActor in collected(heartRate: bpm) }
    }

    nonisolated func workoutBuilderDidCollectEvent(_ workoutBuilder: HKLiveWorkoutBuilder) {}
}
