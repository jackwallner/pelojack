import Foundation

/// The Bluetooth service the bike tablet advertises. Mirrors `RideCommand.kt` on the tablet.
enum BikeUUID {
    static let service = "7A1F0010-5C3B-4E7E-9C8A-50E1A0C0DE01"
    /// The watch writes heart rate here, in the standard Heart Rate Measurement format.
    static let heartRate = "7A1F0011-5C3B-4E7E-9C8A-50E1A0C0DE01"
    /// The tablet notifies ride events here.
    static let ride = "7A1F0012-5C3B-4E7E-9C8A-50E1A0C0DE01"
}

/// What the tablet tells the watch about the ride. Both sides test the same byte strings.
enum RideCommand: Equatable, Sendable {
    case start
    case pause
    case resume
    case metrics(power: Int, cadence: Int, kilojoules: Double, meters: Int)
    case end(seconds: Int, kilojoules: Double, meters: Int)

    init?(_ data: Data) {
        let bytes = [UInt8](data)
        guard let kind = bytes.first else { return nil }
        func uint(_ offset: Int, _ size: Int) -> Int? {
            guard bytes.count >= offset + size else { return nil }
            return (0..<size).reduce(0) { $0 | Int(bytes[offset + $1]) << (8 * $1) }
        }
        switch kind {
        case 1: self = .start
        case 4: self = .pause
        case 5: self = .resume
        case 2:
            guard let power = uint(1, 2), let cadence = uint(3, 1), let tenths = uint(4, 4), let meters = uint(8, 4) else { return nil }
            self = .metrics(power: power, cadence: cadence, kilojoules: Double(tenths) / 10, meters: meters)
        case 3:
            guard let seconds = uint(1, 4), let tenths = uint(5, 4), let meters = uint(9, 4) else { return nil }
            self = .end(seconds: seconds, kilojoules: Double(tenths) / 10, meters: meters)
        default:
            return nil
        }
    }
}

/// A Heart Rate Measurement (0x2A37) value: flags, then the rate as one byte or two.
func heartRateMeasurement(_ bpm: Int) -> Data {
    let rate = max(0, min(bpm, 65_535))
    if rate <= 255 { return Data([0x00, UInt8(rate)]) }
    return Data([0x01, UInt8(rate & 0xFF), UInt8(rate >> 8)])
}
