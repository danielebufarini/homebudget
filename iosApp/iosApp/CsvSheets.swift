import SwiftUI
import UniformTypeIdentifiers

enum ImportPickerKind {
    case csv
}

struct CsvExportDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.commaSeparatedText, .plainText]

    var text: String

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

struct CsvTransferSheet: View {
    let onCancel: () -> Void
    let onExportCsv: () -> Void
    let onImportCsv: () -> Void

    var body: some View {
        AppActionSheet(
            title: appLocalized("CSV Import & Export"),
            description: appLocalized("Move selected data using CSV files stored on this phone."),
            note: appLocalized("Not suitable for full app restore."),
            primaryLabel: appLocalized("Export CSV…"),
            primaryAction: onExportCsv,
            secondaryLabel: appLocalized("Import CSV"),
            secondaryAction: onImportCsv,
            onCancel: onCancel
        )
    }
}

struct CsvExportSheet: View {
    private enum DateField {
        case start
        case end
    }

    @Binding var startDate: Date
    @Binding var endDate: Date
    let onCancel: () -> Void
    let onExport: (Date, Date) -> Void
    @State private var activeField: DateField = .start

    private var activeDateBinding: Binding<Date> {
        switch activeField {
        case .start:
            return $startDate
        case .end:
            return $endDate
        }
    }

    var body: some View {
        AppActionSheet(
            primaryLabel: appLocalized("Export CSV"),
            primaryAction: { onExport(startDate, endDate) },
            secondaryLabel: appLocalized("Cancel"),
            secondaryAction: onCancel,
            showCancelButton: false,
            content: {
                VStack(spacing: 16) {
                    AppGlassSheetSection(title: appLocalized("From")) {
                        exportDateButton(
                            title: appLocalized("From"),
                            date: startDate,
                            selected: activeField == .start
                        ) {
                            activeField = .start
                        }
                    }

                    AppGlassSheetSection(title: appLocalized("To")) {
                        exportDateButton(
                            title: appLocalized("To"),
                            date: endDate,
                            selected: activeField == .end
                        ) {
                            activeField = .end
                        }
                    }

                    AppGlassSheetSection(
                        title: activeField == .start ? appLocalized("From") : appLocalized("To")
                    ) {
                        LiquidGlassCalendar(
                            selectedDate: activeDateBinding,
                            displayedMonth: Binding(
                                get: {
                                    let date = activeDateBinding.wrappedValue
                                    return Calendar.current.date(
                                        from: Calendar.current.dateComponents([.year, .month], from: date)
                                    ) ?? date
                                },
                                set: { newValue in
                                    let currentDate = activeDateBinding.wrappedValue
                                    let selectedDay = Calendar.current.component(.day, from: currentDate)
                                    if let range = Calendar.current.range(of: .day, in: .month, for: newValue) {
                                        let clampedDay = min(selectedDay, range.count)
                                        if let updatedDate = Calendar.current.date(
                                            from: DateComponents(
                                                year: Calendar.current.component(.year, from: newValue),
                                                month: Calendar.current.component(.month, from: newValue),
                                                day: clampedDay
                                            )
                                        ) {
                                            activeDateBinding.wrappedValue = updatedDate
                                        }
                                    }
                                }
                            )
                        )
                    }
                }
            }
        )
    }

    @ViewBuilder
    private func exportDateButton(
        title: String,
        date: Date,
        selected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(date.formatted(date: .abbreviated, time: .omitted))
                        .font(.body.weight(.medium))
                        .foregroundStyle(.primary)
                }

                Spacer()

                Image(systemName: "calendar")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(selected ? Color.accentColor : .secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(selected ? Color.accentColor.opacity(0.14) : Color(uiColor: .secondarySystemBackground))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(
                        selected ? Color.accentColor.opacity(0.55) : Color(uiColor: .separator).opacity(0.2),
                        lineWidth: 1
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

struct AppActionSheet<Content: View>: View {
    let title: String?
    let description: String?
    let note: String?
    let primaryLabel: String
    let primaryAction: () -> Void
    let secondaryLabel: String?
    let secondaryAction: (() -> Void)?
    let showCancelButton: Bool
    let onCancel: (() -> Void)?
    @ViewBuilder let content: Content

    init(
        title: String? = nil,
        description: String? = nil,
        note: String? = nil,
        primaryLabel: String,
        primaryAction: @escaping () -> Void,
        secondaryLabel: String? = nil,
        secondaryAction: (() -> Void)? = nil,
        showCancelButton: Bool = true,
        onCancel: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content = { EmptyView() }
    ) {
        self.title = title
        self.description = description
        self.note = note
        self.primaryLabel = primaryLabel
        self.primaryAction = primaryAction
        self.secondaryLabel = secondaryLabel
        self.secondaryAction = secondaryAction
        self.showCancelButton = showCancelButton
        self.onCancel = onCancel
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if title != nil || description != nil || note != nil {
                    VStack(alignment: .leading, spacing: 8) {
                        if let title {
                            Text(title)
                                .font(.headline)
                                .foregroundStyle(.primary)
                        }

                        if let description {
                            Text(description)
                                .font(.body)
                                .foregroundStyle(.secondary)
                        }

                        if let note {
                            Text(note)
                                .font(.body)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                content

                Button(primaryLabel, action: primaryAction)
                    .buttonStyle(.glassProminent)
                    .frame(maxWidth: .infinity)

                if let secondaryLabel, let secondaryAction {
                    Button(secondaryLabel, action: secondaryAction)
                        .buttonStyle(.glass)
                        .frame(maxWidth: .infinity)
                }

                if showCancelButton, let onCancel {
                    Button(appLocalized("Cancel"), action: onCancel)
                        .buttonStyle(.glass)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 20)
        }
        .appGlassSheetChrome()
    }
}
