import Testing
@testable import Spesify

@MainActor
struct VoiceRecorderLifecycleTests {
    @Test(arguments: [true, false])
    func latePermissionCannotStartAudioAfterCancellationOrStop(cancelTask: Bool) async {
        let entered = AsyncStream<Void>.makeStream()
        let permission = AsyncStream<Void>.makeStream()
        let recorder = VoiceExpenseRecorder {
            entered.continuation.yield(())
            var events = permission.stream.makeAsyncIterator()
            await events.next()
        }
        let pending = Task {
            try await recorder.start { _ in Issue.record("Closed recorder delivered a transcript") }
        }
        var events = entered.stream.makeAsyncIterator()
        await events.next()
        if cancelTask { pending.cancel() } else { recorder.stop() }
        permission.continuation.yield(())
        do {
            try await pending.value
            Issue.record("Late permission started audio")
        } catch {
            #expect(error is CancellationError)
        }
        #expect(recorder.stop().isEmpty)
    }
}
