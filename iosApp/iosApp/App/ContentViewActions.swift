@preconcurrency import ComposeApp
import SwiftUI
import UniformTypeIdentifiers

private enum CsvImportReadError: LocalizedError {
    case fileTooLarge

    var errorDescription: String? {
        switch self {
        case .fileTooLarge:
            return appLocalized("Unable to import the CSV file")
        }
    }
}

extension ContentView {
    func handleIncomingURL(_ url: URL) {
        guard url.scheme == "homebudget" else {
            return
        }

        switch url.host {
        case "add-expense":
            path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
        case "voice-expense":
            startVoiceExpense()
        default:
            break
        }
    }

    func handleCsvSelection(result: Result<URL, Error>) {
        switch result {
        case let .success(url):
            Task {
                do {
                    let text = try await readCsvText(from: url)
                    await MainActor.run {
                        importCsv(text: text)
                    }
                } catch {
                    await MainActor.run {
                        showCsvFeedback(error.localizedDescription, style: .error)
                    }
                }
            }
        case let .failure(error):
            showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    func readCsvText(from url: URL) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            let didAccessSecurityScope = url.startAccessingSecurityScopedResource()
            defer {
                if didAccessSecurityScope {
                    url.stopAccessingSecurityScopedResource()
                }
            }

            if let fileSize = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize,
               fileSize > Self.maxCsvImportBytes {
                throw CsvImportReadError.fileTooLarge
            }

            let data = try Data(contentsOf: url, options: [.mappedIfSafe])
            guard data.count <= Self.maxCsvImportBytes else {
                throw CsvImportReadError.fileTooLarge
            }

            return String(decoding: data, as: UTF8.self)
        }.value
    }

    @MainActor
    func importCsv(text: String) {
        guard !text.isEmpty else {
            showCsvFeedback(appLocalized("Unable to import the CSV file"), style: .error)
            return
        }

        Task { @MainActor [csvImportController] in
            guard let result = try? await csvImportController.importCsv(text: text) else {
                showCsvFeedback(appLocalized("Unable to import the CSV file"), style: .error)
                return
            }

            if let successMessage = result.successMessage {
                HomeBudgetWidgetSummaryRefresher.shared.refresh()
                showCsvFeedback(successMessage, style: .success)
            } else if let errorMessage = result.errorMessage {
                showCsvFeedback(errorMessage, style: .error)
            }
        }
    }

    func exportCsv(startDate: Date, endDate: Date) {
        let normalizedStartDate = Calendar.current.startOfDay(for: startDate)
        let normalizedEndDate = Calendar.current.startOfDay(for: endDate)

        guard normalizedStartDate <= normalizedEndDate else {
            showCsvFeedback(appLocalized("Start date must be on or before end date"), style: .error)
            return
        }

        Task { @MainActor [csvExportController] in
            let result = try? await csvExportController.exportCsv(
                startDateMillis: Int64(normalizedStartDate.timeIntervalSince1970 * 1000),
                endDateMillis: Int64(normalizedEndDate.timeIntervalSince1970 * 1000)
            )

            if let fileName = result?.fileName, let content = result?.content {
                csvExportFilename = fileName
                csvExportDocument = CsvExportDocument(text: content)
                showCsvExportSheet = false
                showCsvExporter = true
            } else {
                showCsvFeedback(
                    result?.errorMessage ?? appLocalized("Unable to export the CSV file"),
                    style: .error
                )
            }
        }
    }

    func handleCsvExport(result: Result<URL, Error>) {
        switch result {
        case .success:
            showCsvFeedback(appLocalized("CSV file exported"), style: .success)
        case let .failure(error):
            showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    @MainActor
    func showCsvFeedback(_ message: String, style: AppGlassBannerStyle) {
        bannerPresenter.show(message, style: style)
    }

    var activeImportAllowedContentTypes: [UTType] {
        [.commaSeparatedText, .plainText, .text]
    }

    func handleImportSelection(result: Result<URL, Error>) {
        activeImportPicker = nil
        handleCsvSelection(result: result)
    }

    func presentAfterMenuDismiss(_ action: @escaping @MainActor () -> Void) {
        DispatchQueue.main.async {
            Task { @MainActor in
                action()
            }
        }
    }
}
