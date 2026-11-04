import Foundation
import Testing
@testable import Spesify

@MainActor
struct VoiceExpenseLifecycleTests {
    @Test
    func closingDuringSnapshotLoadIgnoresTheLateSnapshot() async throws {
        let controller = VoiceControllerStub()
        controller.loadGate = VoiceTestGate()
        let model = VoiceExpenseEntryViewModel(
            controller: controller, recorder: VoiceRecorderStub(), recognition: VoiceRecognitionStub()
        )
        let pending = try #require(model.loadingTask)
        await controller.loadGate?.waitUntilEntered()
        model.dispose()
        controller.loadGate?.open()
        await pending.value
        #expect(!model.canStartCapture)
        #expect(!model.isBusy)
    }

    @Test
    func closingDuringPermissionRequestStopsTheLateRecorder() async throws {
        let recorder = VoiceRecorderStub()
        recorder.startGate = VoiceTestGate()
        let model = await loadedModel(recorder: recorder)
        model.toggleRecording()
        let pending = try #require(model.operationTask)
        await recorder.startGate?.waitUntilEntered()
        model.dispose()
        recorder.startGate?.open()
        await pending.value
        #expect(!recorder.isActive)
        #expect(!model.isRecording)
        #expect(!model.isBusy)
    }

    @Test
    func cancelledInterpretationDoesNotCreateAFallbackDraftAfterClosing() async throws {
        let recorder = VoiceRecorderStub()
        let recognition = VoiceRecognitionStub()
        recognition.gate = VoiceTestGate()
        recognition.error = CancellationError()
        let model = await loadedModel(recorder: recorder, recognition: recognition)
        model.toggleRecording()
        await model.operationTask?.value
        model.toggleRecording()
        let pending = try #require(model.operationTask)
        await recognition.gate?.waitUntilEntered()
        model.dispose()
        recognition.gate?.open()
        await pending.value
        #expect(model.draft == nil)
        #expect(model.errorMessage == nil)
        #expect(!model.isBusy)
    }

    @Test
    func savingIsAcceptedOnceAndCannotCloseADisposedSheet() async throws {
        let controller = VoiceControllerStub()
        controller.saveGate = VoiceTestGate()
        let model = await loadedModel(controller: controller)
        model.toggleRecording()
        await model.operationTask?.value
        model.toggleRecording()
        await model.operationTask?.value
        #expect(model.draft != nil)
        var completions = 0
        model.commit { completions += 1 }
        let firstSave = try #require(model.savingTask)
        await controller.saveGate?.waitUntilEntered()
        model.commit { completions += 1 }
        let lastSave = try #require(model.savingTask)
        model.dispose()
        controller.saveGate?.open()
        await firstSave.value
        await lastSave.value
        #expect(controller.saveCount == 1)
        #expect(completions == 0)
        #expect(!model.isBusy)
    }

    @Test
    func asynchronousSpeechFailureReleasesRecordingStateAndAllowsRetry() async {
        let recorder = VoiceRecorderStub()
        let model = await loadedModel(recorder: recorder)
        model.toggleRecording()
        await model.operationTask?.value
        #expect(model.isRecording)
        let oldFailure = recorder.failureHandler
        recorder.failureHandler?("Speech failed")
        #expect(!recorder.isActive)
        #expect(!model.isRecording)
        #expect(model.errorMessage == "Speech failed")
        model.errorMessage = nil
        model.toggleRecording()
        await model.operationTask?.value
        #expect(model.isRecording)
        oldFailure?("Late error")
        #expect(model.isRecording)
        #expect(model.errorMessage == nil)
        model.dispose()
    }

    @Test
    func snapshotCancellationRemainsSilentAndDoesNotEnableCapture() async {
        let controller = VoiceControllerStub()
        controller.loadError = CancellationError()
        let model = await loadedModel(controller: controller)
        #expect(!model.canStartCapture)
        #expect(model.errorMessage == nil)
        #expect(!model.isBusy)
        model.dispose()
    }

    @Test
    func releasingTheOwnerDuringPostSaveRefreshCancelsTheTask() async throws {
        let controller = VoiceControllerStub()
        var model: VoiceExpenseEntryViewModel? = await loadedModel(controller: controller)
        model?.toggleRecording()
        await model?.operationTask?.value
        model?.toggleRecording()
        await model?.operationTask?.value
        controller.loadGate = VoiceTestGate()
        weak let releasedModel = model
        model?.commit { Issue.record("Released voice sheet received save completion") }
        let pending = try #require(model?.savingTask)
        await controller.loadGate?.waitUntilEntered()
        model = nil
        #expect(releasedModel == nil)
        #expect(pending.isCancelled)
        controller.loadGate?.open()
        await pending.value
    }

    private func loadedModel(
        controller: VoiceControllerStub = VoiceControllerStub(),
        recorder: VoiceRecorderStub = VoiceRecorderStub(),
        recognition: VoiceRecognitionStub = VoiceRecognitionStub()
    ) async -> VoiceExpenseEntryViewModel {
        let model = VoiceExpenseEntryViewModel(controller: controller, recorder: recorder, recognition: recognition)
        await model.loadingTask?.value
        return model
    }
}

// Gates deliberately ignore cancellation to exercise late platform/provider results.
@MainActor
private final class VoiceTestGate {
    private var isOpen = false
    private var entered = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        entered = true
        entryWaiters.forEach { $0.resume() }
        entryWaiters.removeAll()
        if !isOpen { await withCheckedContinuation { waiters.append($0) } }
    }

    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { entryWaiters.append($0) } }
    }

    func open() {
        isOpen = true
        waiters.forEach { $0.resume() }
        waiters.removeAll()
    }
}

@MainActor
private final class VoiceRecorderStub: VoiceExpenseRecording {
    var startGate: VoiceTestGate?
    var isActive = false
    var failureHandler: (@MainActor @Sendable (String) -> Void)?
    func start(
        onTranscript: @escaping @MainActor @Sendable (String) -> Void,
        onFailure: @escaping @MainActor @Sendable (String) -> Void
    ) async throws {
        await startGate?.wait()
        failureHandler = onFailure
        isActive = true
        onTranscript("Ho speso 12 euro per Cibo")
    }
    func stop() -> String {
        isActive = false
        return "Ho speso 12 euro per Cibo"
    }
}

@MainActor
private final class VoiceControllerStub: VoiceExpenseControlling {
    var loadGate: VoiceTestGate?
    var saveGate: VoiceTestGate?
    var saveCount = 0
    var loadError: (any Error)?
    func loadSnapshotData() async throws -> VoiceExpenseSnapshotData? {
        await loadGate?.wait()
        if let loadError { throw loadError }
        return VoiceExpenseSnapshotData(categories: [.init(id: "food", name: "Cibo")], recentExpenses: [])
    }
    func persist(draft: VoiceExpenseDraft) async -> (success: Bool, message: String?) {
        saveCount += 1
        await saveGate?.wait()
        return (true, nil)
    }
}

@MainActor
private final class VoiceRecognitionStub: VoiceExpenseRecognizing {
    let isAvailable = true
    let unavailableMessage: String? = nil
    var gate: VoiceTestGate?
    var error: (any Error)?
    func interpret(
        transcript: String, categories: [VoiceExpenseCategory], expenses: [VoiceExpenseCandidate]
    ) async throws -> VoiceExpenseInterpretation {
        await gate?.wait()
        if let error { throw error }
        return VoiceExpenseInterpretation(
            intent: .create, expenseId: nil, amount: "12.00", categoryId: "food", categoryName: "Cibo",
            description: nil, date: nil, isShared: false, summary: "Cibo"
        )
    }
}
