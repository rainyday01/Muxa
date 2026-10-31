import SwiftUI

struct TerminalProbeView: View {
    @StateObject private var controller = TerminalSessionController()
    @State private var didLoadFixture = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("SwiftTerm Probe")
                    .font(.headline)
                Spacer()
                Text("\(controller.columns)x\(controller.rows)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("terminal.probe.size")
            }
            .padding(.horizontal)
            .frame(height: 44)

            TerminalViewport(controller: controller, terminalAccessibilityIdentifier: "terminal.probe")
                .background(Color.black)

            Text("Local fixtures: \(TerminalCompatibilityFixture.summary)")
                .font(.caption2.monospaced())
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                .frame(height: 22)
                .accessibilityIdentifier("terminal.probe.compatibility")

            Text("Input: \(controller.lastInput)")
                .font(.caption.monospaced())
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                .frame(height: 32)
                .accessibilityIdentifier("terminal.probe.input")
        }
        .background(Color(uiColor: .systemBackground))
        .onAppear {
            guard !didLoadFixture else { return }
            didLoadFixture = true
            controller.echoesInput = true
            controller.setCursorStyle(.steadyBlock)
            controller.feed(TerminalProbeFixture.content)
        }
    }
}

enum TerminalProbeFixture {
    static let content = """
    \u{1B}[2J\u{1B}[H\u{1B}[1mMuxa SwiftTerm\u{1B}[0m\r
    Unicode: 中文 👩🏽‍💻 e\u{301}\r
    TrueColor: \u{1B}[38;2;19;49;174mRGB\u{1B}[0m\r
    Input:\u{20}
    """
}

enum TerminalCompatibilityFixture {
    static let applications = ["vim", "htop", "tmux", "Claude", "Codex", "Grok"]
    static let summary = applications.joined(separator: " · ")
    static let output = """
    \u{1B}[1;32mvim\u{1B}[0m  CJK: 中文  Emoji: 🧪
    \u{1B}[1;34mhtop\u{1B}[0m  16/256/TrueColor
    \u{1B}[1;36mtmux\u{1B}[0m  pane 0 80x24
    \u{1B}[38;2;255;140;0mClaude\u{1B}[0m  interactive prompt
    \u{1B}[38;2;0;180;255mCodex\u{1B}[0m  interactive prompt
    \u{1B}[38;2;180;0;255mGrok\u{1B}[0m  interactive prompt
    """
}
