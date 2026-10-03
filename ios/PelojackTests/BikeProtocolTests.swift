import XCTest
@testable import Pelojack

/// The same byte strings are asserted by the tablet's `RideCommandTest`; change both together.
final class BikeProtocolTests: XCTestCase {
    private func bytes(_ hex: String) -> Data {
        Data(hex.split(separator: " ").map { UInt8($0, radix: 16)! })
    }

    func testDecodesTabletCommands() {
        XCTAssertEqual(RideCommand(bytes("01")), .start)
        XCTAssertEqual(RideCommand(bytes("04")), .pause)
        XCTAssertEqual(RideCommand(bytes("05")), .resume)
        XCTAssertEqual(RideCommand(bytes("02 d7 00 58 d2 04 00 00 d7 11 00 00")), .metrics(power: 215, cadence: 88, kilojoules: 123.4, meters: 4567))
        XCTAssertEqual(RideCommand(bytes("03 08 07 00 00 bd 0b 00 00 e0 2e 00 00")), .end(seconds: 1800, kilojoules: 300.5, meters: 12_000))
    }

    func testRejectsTruncatedOrUnknownCommands() {
        XCTAssertNil(RideCommand(Data()))
        XCTAssertNil(RideCommand(bytes("02 d7 00")))
        XCTAssertNil(RideCommand(bytes("09")))
    }

    func testEncodesHeartRateLikeAStrap() {
        XCTAssertEqual(heartRateMeasurement(72), bytes("00 48"))
        XCTAssertEqual(heartRateMeasurement(300), bytes("01 2c 01"))
    }
}
