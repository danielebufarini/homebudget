import Foundation
import Testing
@testable import Spesify

@MainActor
struct ContentTransferPresentationTests {
    @Test
    func closingTheOwnerSuppressesItsQueuedPresentation() async {
        let model = ContentTransferViewModel()
        var presentations = 0
        model.presentAfterMenuDismiss { presentations += 1 }
        model.stop()
        await drainMainQueue()
        #expect(presentations == 0)
    }

    @Test
    func onlyTheLatestQueuedPresentationRuns() async {
        let model = ContentTransferViewModel()
        var presentations: [String] = []
        model.presentAfterMenuDismiss { presentations.append("old") }
        model.presentAfterMenuDismiss { presentations.append("new") }
        await drainMainQueue()
        #expect(presentations == ["new"])
    }

    private func drainMainQueue() async {
        await withCheckedContinuation { continuation in DispatchQueue.main.async { continuation.resume() } }
    }
}
