import Foundation

final class MaestriCLI {

    static let shared = MaestriCLI()

    private(set) var binaryPath: String?

    private init() {
        binaryPath = findBinary()
    }

    var isAvailable: Bool { binaryPath != nil }

    // MARK: - Public API

    /// Synchronous. Call only from a background thread.
    func run(args: [String]) -> String {
        guard let binary = binaryPath else { return "" }
        return runSync(binary: binary, args: args) ?? ""
    }

    /// Fire-and-forget.
    func runAsync(args: [String]) {
        guard let binary = binaryPath else { return }
        let process = Process()
        process.executableURL = URL(fileURLWithPath: binary)
        process.arguments = args
        process.standardOutput = Pipe()
        process.standardError = Pipe()
        try? process.run()
    }

    // MARK: - Binary discovery

    private func findBinary() -> String? {
        // 1. Explicit env var set by Maestri
        if let envPath = ProcessInfo.processInfo.environment["MAESTRI_CLI"],
           FileManager.default.isExecutableFile(atPath: envPath) {
            return envPath
        }

        // 2. Common install locations
        let candidates = [
            "/opt/homebrew/bin/maestri",
            "/usr/local/bin/maestri",
            "/usr/bin/maestri",
            "\(NSHomeDirectory())/.local/bin/maestri"
        ]
        if let found = candidates.first(where: { FileManager.default.isExecutableFile(atPath: $0) }) {
            return found
        }

        // 3. `which maestri` as a last resort
        if let path = runSync(binary: "/usr/bin/which", args: ["maestri"])?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !path.isEmpty,
           FileManager.default.isExecutableFile(atPath: path) {
            return path
        }

        return nil
    }

    // MARK: - Private

    private func runSync(binary: String, args: [String]) -> String? {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: binary)
        process.arguments = args

        let outPipe = Pipe()
        process.standardOutput = outPipe
        process.standardError = Pipe()

        do {
            try process.run()
            process.waitUntilExit()
            let data = outPipe.fileHandleForReading.readDataToEndOfFile()
            return String(data: data, encoding: .utf8)
        } catch {
            return nil
        }
    }
}
