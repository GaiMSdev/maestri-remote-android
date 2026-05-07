import SwiftUI

@main
struct MaestriBridgeApp: App {
    @StateObject private var server = MaestriServer()

    var body: some Scene {
        MenuBarExtra {
            MenuBarView(server: server)
        } label: {
            Image(systemName: server.isRunning
                  ? "antenna.radiowaves.left.and.right"
                  : "antenna.radiowaves.left.and.right.slash")
            .foregroundStyle(server.isRunning ? Color.accentColor : .secondary)
        }
        .menuBarExtraStyle(.window)
    }
}

struct MenuBarView: View {
    @ObservedObject var server: MaestriServer

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Maestri Remote Bridge")
                .font(.headline)

            Divider()

            HStack {
                Circle()
                    .fill(server.isRunning ? Color.green : Color.red)
                    .frame(width: 8, height: 8)
                Text(server.isRunning
                     ? "Running on port \(server.port.rawValue)"
                     : "Stopped")
                    .font(.subheadline)
            }

            Text("\(server.clientCount) client\(server.clientCount == 1 ? "" : "s") connected")
                .font(.caption)
                .foregroundStyle(.secondary)

            if !server.cliAvailable {
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.triangle")
                        .foregroundStyle(.orange)
                    Text("maestri CLI not found")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }

            Divider()

            VStack(alignment: .leading, spacing: 4) {
                Text("Pairing PIN")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack {
                    Text(server.pin)
                        .font(.system(.title2, design: .monospaced).bold())
                    Spacer()
                    Button {
                        server.regeneratePIN()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption)
                    }
                    .buttonStyle(.borderless)
                    .help("Generate a new PIN")
                }
            }

            Divider()

            Button(server.isRunning ? "Stop Bridge" : "Start Bridge") {
                server.isRunning ? server.stop() : server.start()
            }
            .keyboardShortcut("s", modifiers: .command)

            Button("Quit") {
                NSApplication.shared.terminate(nil)
            }
            .keyboardShortcut("q")
        }
        .padding()
        .frame(width: 240)
        .task {
            if !server.isRunning {
                server.start()
            }
        }
    }
}
