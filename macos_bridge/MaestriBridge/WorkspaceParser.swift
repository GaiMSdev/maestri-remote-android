import Foundation

enum WorkspaceParser {

    /// Parse the output of `maestri list` into a WorkspacePayload.
    static func parse(listOutput: String) -> WorkspacePayload {
        var nodes: [NodePayload] = []
        var notes: [NotePayload] = []

        var inAgents = false
        var inNotes = false

        for line in listOutput.components(separatedBy: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            if trimmed.hasPrefix("Connected agents") {
                inAgents = true; inNotes = false; continue
            }
            if trimmed.hasPrefix("Connected notes") {
                inAgents = false; inNotes = true; continue
            }

            guard trimmed.hasPrefix("- name:") else { continue }

            // Extract name between quotes: `- name: "Codex"`
            // Also handle indented chained notes: `  - name: "..."` already trimmed above
            if let name = extractQuotedName(from: trimmed) {
                let id = makeId(from: name)
                if inAgents {
                    nodes.append(NodePayload(
                        id: id,
                        label: name,
                        agentType: inferAgentType(name),
                        status: "IDLE",
                        connectedTo: []
                    ))
                } else if inNotes {
                    notes.append(NotePayload(id: id, title: name, content: ""))
                }
            }
        }

        return WorkspacePayload(
            id: "maestri-workspace",
            name: "Maestri",
            nodes: nodes,
            notes: notes,
            connections: []
        )
    }

    // MARK: - Private helpers

    private static func extractQuotedName(from line: String) -> String? {
        // Matches: - name: "Some Name"
        guard let start = line.range(of: "\""),
              let end = line.range(of: "\"", options: .backwards),
              start.lowerBound < end.lowerBound else { return nil }
        let nameRange = line.index(after: start.lowerBound)..<end.lowerBound
        return String(line[nameRange])
    }

    private static func makeId(from name: String) -> String {
        name.lowercased()
            .components(separatedBy: .alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "_")
    }

    private static func inferAgentType(_ name: String) -> String {
        let lower = name.lowercased()
        if lower.contains("claude") { return "CLAUDE_CODE" }
        if lower.contains("codex")  { return "CODEX" }
        if lower.contains("gemini") { return "GEMINI" }
        if lower.contains("shell")  { return "SHELL" }
        return "UNKNOWN"
    }
}
