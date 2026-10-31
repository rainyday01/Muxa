import SwiftUI

struct TerminalAppearanceView: View {
    @Bindable var preferences: AppPreferencesStore

    var body: some View {
        List {
            Section("Size") {
                let spec = preferences.currentFontSpec()
                Stepper(
                    value: fontSizeBinding,
                    in: spec.minimum ... spec.maximum
                ) {
                    LabeledContent("Font size", value: fontSizeLabel)
                }
                .accessibilityIdentifier("settings.font-size")
                Text(fontSizeCaption)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section("Color scheme") {
                ForEach(TerminalColorCatalog.all) { scheme in
                    Button {
                        preferences.terminalSchemeID = scheme.id
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(scheme.displayName)
                                Text(scheme.id)
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if scheme.id == preferences.terminalSchemeID {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(.accent)
                            }
                        }
                    }
                    .accessibilityIdentifier("settings.scheme.\(scheme.id)")
                }
            }
            Section("Preview") {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(TerminalColorCatalog.previewLines.enumerated()), id: \.offset) { _, line in
                        Text(line.isEmpty ? " " : line)
                    }
                }
                .font(.system(size: CGFloat(preferences.resolvedFontSize), design: .monospaced))
                .foregroundStyle(Color(uiColor: UIColor(muxaHex: preferences.terminalScheme.foreground) ?? .white))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color(uiColor: UIColor(muxaHex: preferences.terminalScheme.background) ?? .black))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("settings.terminal-preview")
            }
        }
        .navigationTitle("Terminal Appearance")
    }

    private var fontSizeBinding: Binding<Int> {
        Binding(
            get: { preferences.resolvedFontSize },
            set: { preferences.setCustomFontSize($0) }
        )
    }

    private var fontSizeLabel: String {
        let suffix = preferences.terminalFontSizeAutomatic ? " · fits screen" : ""
        return "\(preferences.resolvedFontSize) pt\(suffix)"
    }

    private var fontSizeCaption: String {
        let spec = preferences.currentFontSpec()
        return "Range \(spec.minimum)–\(spec.maximum) pt for this screen and text size. Pinch the terminal to adjust."
    }
}
