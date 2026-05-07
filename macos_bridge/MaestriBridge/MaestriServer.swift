import Foundation
import Network
import Combine

class MaestriServer: ObservableObject {

    // MARK: - Published state

    @Published var isRunning = false
    @Published var clientCount = 0
    @Published var pin: String = MaestriServer.generatePIN()
    @Published var cliAvailable: Bool = MaestriCLI.shared.isAvailable

    let port: NWEndpoint.Port = 8765

    // MARK: - Private state

    private var listener: NWListener?
    private var netService: NetService?

    /// Connections that have connected but not yet sent a valid auth message.
    private var pendingAuth: [NWConnection] = []
    /// Connections that have passed PIN authentication.
    private var authenticated: [NWConnection] = []

    private var pollingTimer: Timer?
    private var lastCheckOutput: [String: String] = [:]
    private var terminalSeqs: [String: Int] = [:]
    private var knownNodes: [NodePayload] = []
    private var knownNotes: [NotePayload] = []

    // MARK: - Start / Stop

    func start() {
        guard !isRunning else { return }

        let parameters = NWParameters.tcp
        let wsOptions = NWProtocolWebSocket.Options()
        wsOptions.autoReplyPing = true
        parameters.defaultProtocolStack.applicationProtocols.insert(wsOptions, at: 0)

        guard let newListener = try? NWListener(using: parameters, on: port) else {
            print("[Bridge] Could not create listener on port \(port)")
            return
        }
        listener = newListener

        newListener.stateUpdateHandler = { [weak self] state in
            DispatchQueue.main.async {
                switch state {
                case .ready:
                    self?.isRunning = true
                    self?.advertiseBonjour()
                    self?.startPolling()
                    print("[Bridge] Ready on port \(self?.port.rawValue ?? 0)")
                case .failed(let error):
                    print("[Bridge] Listener failed: \(error)")
                    self?.stop()
                default:
                    break
                }
            }
        }

        newListener.newConnectionHandler = { [weak self] conn in
            DispatchQueue.main.async { self?.accept(conn) }
        }

        newListener.start(queue: .main)
    }

    func stop() {
        pollingTimer?.invalidate()
        pollingTimer = nil
        netService?.stop()
        netService = nil
        listener?.cancel()
        listener = nil

        let goodbye = MessageEncoder.encode(SessionEndMessage(reason: "server_stopped"))
        for conn in authenticated {
            if let json = goodbye { send(json: json, to: conn) }
            conn.cancel()
        }
        pendingAuth.forEach { $0.cancel() }
        pendingAuth.removeAll()
        authenticated.removeAll()
        knownNodes.removeAll()
        knownNotes.removeAll()
        lastCheckOutput.removeAll()
        terminalSeqs.removeAll()
        isRunning = false
        clientCount = 0
    }

    func regeneratePIN() {
        pin = Self.generatePIN()
    }

    // MARK: - Accepting connections

    private func accept(_ connection: NWConnection) {
        pendingAuth.append(connection)

        connection.stateUpdateHandler = { [weak self] state in
            DispatchQueue.main.async {
                switch state {
                case .cancelled, .failed:
                    self?.remove(connection)
                default:
                    break
                }
            }
        }

        connection.start(queue: .main)
        receiveNext(from: connection)
    }

    private func remove(_ connection: NWConnection) {
        pendingAuth.removeAll { $0 === connection }
        let before = authenticated.count
        authenticated.removeAll { $0 === connection }
        if authenticated.count != before { clientCount = authenticated.count }
    }

    // MARK: - Receive loop

    private func receiveNext(from connection: NWConnection) {
        connection.receiveMessage { [weak self] data, _, _, error in
            guard error == nil, let data = data,
                  let text = String(data: data, encoding: .utf8) else { return }
            DispatchQueue.main.async {
                self?.dispatch(text: text, from: connection)
            }
            self?.receiveNext(from: connection)
        }
    }

    // MARK: - Message dispatch

    private func dispatch(text: String, from connection: NWConnection) {
        guard let data = text.data(using: .utf8),
              let msg = try? JSONDecoder().decode(InboundMessage.self, from: data) else {
            print("[Bridge] Could not parse message, ignoring")
            return
        }

        if pendingAuth.contains(where: { $0 === connection }) {
            handleAuth(msg: msg, on: connection)
            return
        }
        guard authenticated.contains(where: { $0 === connection }) else { return }

        switch msg.type {
        case "terminal_input":
            if let nodeId = msg.nodeId, let text = msg.text {
                forwardTerminalInput(nodeId: nodeId, text: text)
            }
        case "ombro_request":
            replyWithOmbro(to: connection)
        case "note_update":
            if let noteId = msg.noteId, let content = msg.content {
                forwardNoteUpdate(noteId: noteId, content: content)
            }
        case "ping":
            break // NWProtocolWebSocket handles protocol pings automatically
        default:
            break // Forward-compatible: silently ignore unknown types
        }
    }

    // MARK: - PIN auth

    private func handleAuth(msg: InboundMessage, on connection: NWConnection) {
        guard msg.type == "auth" else {
            // First message must be auth; anything else closes the connection
            connection.cancel()
            pendingAuth.removeAll { $0 === connection }
            return
        }

        if msg.pin == pin {
            pendingAuth.removeAll { $0 === connection }
            authenticated.append(connection)
            clientCount = authenticated.count
            print("[Bridge] Client authenticated (\(authenticated.count) total)")
            sendWorkspaceSnapshot(to: connection)
        } else {
            print("[Bridge] Auth rejected — wrong PIN")
            if let json = MessageEncoder.encode(SessionEndMessage(reason: "auth_failed")) {
                send(json: json, to: connection)
            }
            connection.cancel()
            pendingAuth.removeAll { $0 === connection }
        }
    }

    // MARK: - Workspace snapshot (sent after successful auth)

    private func sendWorkspaceSnapshot(to connection: NWConnection) {
        Task.detached {
            let output = MaestriCLI.shared.run(args: ["list"])
            let payload = WorkspaceParser.parse(listOutput: output)
            let snapshot = WorkspaceSnapshot(workspace: payload)

            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.knownNodes = payload.nodes
                self.knownNotes = payload.notes
                if let json = MessageEncoder.encode(snapshot) {
                    self.send(json: json, to: connection)
                }
            }
        }
    }

    // MARK: - Inbound message handlers

    private func forwardTerminalInput(nodeId: String, text: String) {
        let label = knownNodes.first { $0.id == nodeId }?.label ?? nodeId
        MaestriCLI.shared.runAsync(args: ["ask", label, text])
    }

    private func replyWithOmbro(to connection: NWConnection) {
        Task.detached {
            let output = MaestriCLI.shared.run(args: ["list"])
            let payload = WorkspaceParser.parse(listOutput: output)
            let count = payload.nodes.count
            let msg = OmbroSummaryMessage(
                generatedAt: ISO8601DateFormatter().string(from: Date()),
                text: "\(count) agent\(count == 1 ? "" : "s") connected via Maestri Bridge.",
                nextSteps: payload.nodes.map { "Check \($0.label)" }
            )
            if let json = MessageEncoder.encode(msg) {
                DispatchQueue.main.async { [weak self] in
                    self?.send(json: json, to: connection)
                }
            }
        }
    }

    private func forwardNoteUpdate(noteId: String, content: String) {
        let title = knownNotes.first { $0.id == noteId }?.title ?? noteId
        MaestriCLI.shared.runAsync(args: ["note", "write", title, content])
    }

    // MARK: - Polling (workspace refresh + terminal diff)

    private func startPolling() {
        pollingTimer?.invalidate()
        pollingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.poll()
        }
    }

    private func poll() {
        guard !authenticated.isEmpty else { return }

        Task.detached { [weak self] in
            guard let self else { return }

            let listOutput = MaestriCLI.shared.run(args: ["list"])
            let payload = WorkspaceParser.parse(listOutput: listOutput)

            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                // Announce any nodes that didn't exist in the previous snapshot
                for node in payload.nodes where !self.knownNodes.contains(where: { $0.id == node.id }) {
                    if let json = MessageEncoder.encode(NodeStatusMessage(nodeId: node.id, status: node.status)) {
                        self.broadcast(json: json)
                    }
                }
                self.knownNodes = payload.nodes
                self.knownNotes = payload.notes
            }

            // Check each agent's terminal for new output
            for node in payload.nodes {
                let checkOutput = MaestriCLI.shared.run(args: ["check", node.label])
                DispatchQueue.main.async { [weak self] in
                    self?.diffAndBroadcast(nodeId: node.id, newOutput: checkOutput)
                }
            }
        }
    }

    private func diffAndBroadcast(nodeId: String, newOutput: String) {
        let previous = lastCheckOutput[nodeId] ?? ""
        guard newOutput != previous, !newOutput.isEmpty else { return }

        let prevLines = previous.isEmpty ? [] : previous.components(separatedBy: "\n")
        let newLines = newOutput.components(separatedBy: "\n")

        for line in newLines.dropFirst(prevLines.count) {
            let stripped = line.trimmingCharacters(in: .whitespaces)
            guard !stripped.isEmpty else { continue }
            let seq = (terminalSeqs[nodeId] ?? 0) + 1
            terminalSeqs[nodeId] = seq
            if let json = MessageEncoder.encode(TerminalLineMessage(nodeId: nodeId, line: stripped, seq: seq)) {
                broadcast(json: json)
            }
        }

        lastCheckOutput[nodeId] = newOutput
    }

    // MARK: - Send helpers

    func send(json: String, to connection: NWConnection) {
        guard let data = json.data(using: .utf8) else { return }
        let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "text", metadata: [metadata])
        connection.send(content: data, contentContext: context, isComplete: true,
                        completion: .contentProcessed { _ in })
    }

    func broadcast(json: String) {
        authenticated.forEach { send(json: json, to: $0) }
    }

    // MARK: - Bonjour / mDNS

    private func advertiseBonjour() {
        netService = NetService(
            domain: "local.",
            type: "_maestri._tcp.",
            name: "Maestri Bridge",
            port: Int32(port.rawValue)
        )
        netService?.publish()
    }

    // MARK: - Helpers

    static func generatePIN() -> String {
        String(format: "%06d", Int.random(in: 0...999_999))
    }
}
