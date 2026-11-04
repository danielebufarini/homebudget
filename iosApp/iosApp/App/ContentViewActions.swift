import SwiftUI
import UniformTypeIdentifiers

extension ContentView {
    func consumePendingOpenURLIfNeeded() {
        guard let pendingOpenURL else { return }
        self.pendingOpenURL = nil
        handleIncomingURL(pendingOpenURL.url)
    }

    func handleIncomingURL(_ url: URL) {
        if url.isFileURL {
            transferModel.importScreenshot(from: url)
            return
        }
        guard url.scheme == "spesify" else { return }
        switch url.host {
        case "add-expense":
            path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
        case "voice-expense":
            startVoiceExpense()
        default: break
        }
    }

    func handleCsvSelection(result: Result<URL, Error>) {
        switch result {
        case let .success(url): transferModel.importCsv(from: url)
        case let .failure(error):
            transferModel.cancelCsvImport()
            showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    func exportCsv(startDate: Date, endDate: Date) {
        transferModel.exportCsv(startDate: startDate, endDate: endDate)
    }

    func handleCsvExport(result: Result<URL, Error>) {
        switch result {
        case .success: showCsvFeedback(appLocalized("CSV file exported"), style: .success)
        case let .failure(error): showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    @MainActor
    func showCsvFeedback(_ message: String, style: AppGlassBannerStyle) {
        bannerPresenter.show(message, style: style)
    }

    var activeImportAllowedContentTypes: [UTType] { [.commaSeparatedText, .plainText, .text] }

    func handleImportSelection(result: Result<URL, Error>) {
        activeImportPicker = nil
        handleCsvSelection(result: result)
    }

    func presentAfterMenuDismiss(_ action: @escaping @MainActor () -> Void) {
        transferModel.presentAfterMenuDismiss(action)
    }
}
