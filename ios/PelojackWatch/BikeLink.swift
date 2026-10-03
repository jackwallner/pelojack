import CoreBluetooth
import Observation
import WatchKit

/// Finds the bike tablet over Bluetooth as soon as the app opens, sends it heart rate, and hears
/// about the ride. The watch can only be the scanning side, so the tablet advertises and waits.
@MainActor
@Observable
final class BikeLink: NSObject {
    enum State: Equatable { case bluetoothOff, searching, connected }

    private(set) var state: State = .searching

    @ObservationIgnored var onCommand: ((RideCommand) -> Void)?
    @ObservationIgnored private var central: CBCentralManager?
    @ObservationIgnored private var bike: CBPeripheral?
    @ObservationIgnored private var heart: CBCharacteristic?

    private let service = CBUUID(string: BikeUUID.service)

    func start() {
        guard central == nil else { return }
        central = CBCentralManager(delegate: self, queue: nil)
    }

    func send(heartRate: Int) {
        guard let bike, let heart, bike.state == .connected else { return }
        bike.writeValue(heartRateMeasurement(heartRate), for: heart, type: .withoutResponse)
    }

    private func scan() {
        guard let central, central.state == .poweredOn else { return }
        bike = nil
        heart = nil
        state = .searching
        central.scanForPeripherals(withServices: [service])
    }

    private func found(_ peripheral: CBPeripheral) {
        guard bike == nil, let central else { return }
        central.stopScan()
        bike = peripheral
        peripheral.delegate = self
        central.connect(peripheral)
        // A connection attempt never times out on its own, and the tablet changes its Bluetooth
        // address from time to time, so a stale attempt is dropped and the scan starts over.
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(10))
            guard let self, self.bike === peripheral, peripheral.state != .connected else { return }
            self.central?.cancelPeripheralConnection(peripheral)
            self.scan()
        }
    }

    /// Callbacks for a peripheral that has already been given up on are ignored.
    private func lost(_ peripheral: CBPeripheral) {
        guard bike == nil || bike === peripheral else { return }
        scan()
    }

    private func ready(heart: CBCharacteristic?, ride: CBCharacteristic?, on peripheral: CBPeripheral) {
        self.heart = heart
        if let ride { peripheral.setNotifyValue(true, for: ride) }
        state = .connected
        WKInterfaceDevice.current().play(.success)
    }
}

// Core Bluetooth calls back on the main queue (queue: nil), so the delegates run on the main actor.
extension BikeLink: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn { scan() } else { state = .bluetoothOff }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        found(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard peripheral === bike else { return }
        peripheral.discoverServices([CBUUID(string: BikeUUID.service)])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        lost(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        lost(peripheral)
    }
}

extension BikeLink: @preconcurrency CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard peripheral === bike, let service = peripheral.services?.first(where: { $0.uuid == CBUUID(string: BikeUUID.service) }) else {
            // Connected, but not to a bike with the service: try again rather than sit stuck.
            if peripheral === bike { central?.cancelPeripheralConnection(peripheral) }
            return
        }
        peripheral.discoverCharacteristics([CBUUID(string: BikeUUID.heartRate), CBUUID(string: BikeUUID.ride)], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard peripheral === bike else { return }
        let characteristics = service.characteristics ?? []
        let heart = characteristics.first { $0.uuid == CBUUID(string: BikeUUID.heartRate) }
        let ride = characteristics.first { $0.uuid == CBUUID(string: BikeUUID.ride) }
        ready(heart: heart, ride: ride, on: peripheral)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == CBUUID(string: BikeUUID.ride), let value = characteristic.value, let command = RideCommand(value) else { return }
        onCommand?(command)
    }
}
