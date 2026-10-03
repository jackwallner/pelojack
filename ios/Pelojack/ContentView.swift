import SwiftUI

private let accent = Color(red: 1, green: 0.416, blue: 0.169)

struct ContentView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                HStack(spacing: 14) {
                    Circle()
                        .strokeBorder(accent, lineWidth: 6)
                        .overlay(Circle().fill(.white).frame(width: 9, height: 9))
                        .frame(width: 40, height: 40)
                    Text("Pelojack").font(.largeTitle.bold())
                }
                Text("Pelojack runs on your Apple Watch. There is nothing to do on this phone.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 24) {
                    Step(number: 1, title: "Open Pelojack on your watch", detail: "Near the bike. It finds the bike on its own and taps your wrist when it has.")
                    Step(number: 2, title: "Start a ride on the bike", detail: "The watch workout starts, pauses and ends with it, and your heart rate shows on the bike.")
                    Step(number: 3, title: "Rings and Apple Health", detail: "Each ride is saved as an indoor cycle, with the bike's distance.")
                }
                Text("Tip: add Pelojack to your watch face so it is one tap away.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(24)
        }
        .background(Color.black)
    }
}

private struct Step: View {
    let number: Int
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Text("\(number)")
                .font(.headline)
                .frame(width: 34, height: 34)
                .background(accent, in: .circle)
                .foregroundStyle(.black)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(detail).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}
