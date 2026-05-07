import Foundation

// MARK: - Outbound (Mac → Android)

struct WorkspaceSnapshot: Encodable {
    let type = "workspace_snapshot"
    let workspace: WorkspacePayload
}

struct WorkspacePayload: Encodable {
    let id: String
    let name: String
    let nodes: [NodePayload]
    let notes: [NotePayload]
    let connections: [ConnectionPayload]
}

struct NodePayload: Encodable {
    let id: String
    let label: String
    let agentType: String
    let status: String
    let connectedTo: [String]
}

struct NotePayload: Encodable {
    let id: String
    let title: String
    let content: String
}

struct ConnectionPayload: Encodable {
    let from: String
    let to: String
}

struct NodeStatusMessage: Encodable {
    let type = "node_status"
    let nodeId: String
    let status: String
}

struct TerminalLineMessage: Encodable {
    let type = "terminal_line"
    let nodeId: String
    let line: String
    let seq: Int
}

struct OmbroSummaryMessage: Encodable {
    let type = "ombro_summary"
    let generatedAt: String
    let text: String
    let nextSteps: [String]
}

struct NoteUpdatedMessage: Encodable {
    let type = "note_updated"
    let note: NotePayload
}

struct SessionEndMessage: Encodable {
    let type = "session_end"
    let reason: String
}

// MARK: - Inbound (Android → Mac)

struct InboundMessage: Decodable {
    let type: String
    let nodeId: String?
    let text: String?
    let noteId: String?
    let content: String?
    let pin: String?
}

// MARK: - JSON helpers

enum MessageEncoder {
    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = .sortedKeys
        return e
    }()

    static func encode<T: Encodable>(_ value: T) -> String? {
        guard let data = try? encoder.encode(value) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
