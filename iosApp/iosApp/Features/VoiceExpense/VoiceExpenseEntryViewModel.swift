@preconcurrency import ComposeApp
import SwiftUI
import Observation

enum VoiceExpensePreparation {
    case snapshot, microphone
}

enum VoiceExpenseState {
    case preparing(VoiceExpensePreparation)
    case ready
    case recording
    case interpreting
    case review(VoiceExpenseDraft)
    case saving(VoiceExpenseDraft)
    case error(String, draft: VoiceExpenseDraft?)
}

@MainActor
@Observable
final class VoiceExpenseEntryViewModel {
    var transcript = ""
    var statusMessage: String?
    private(set) var state: VoiceExpenseState = .preparing(.snapshot)

    private let controller: any VoiceExpenseControlling
    private let recorder: any VoiceExpenseRecording
    private let recognition: any VoiceExpenseRecognizing
    private let writeTasks: ApplicationWriteTasks
    private let refreshWidget: @MainActor () -> Void
    @ObservationIgnored private(set) var loadingTask: Task<Void, Never>?
    @ObservationIgnored private(set) var operationTask: Task<Void, Never>?
    @ObservationIgnored private(set) var savingTask: Task<Void, Never>?
    @ObservationIgnored private var categoriesById: [String: VoiceExpenseCategory] = [:]
    @ObservationIgnored private var expensesById: [String: VoiceExpenseCandidate] = [:]
    private var snapshotLoaded = false
    @ObservationIgnored private var shouldAutoStartWhenReady = false
    @ObservationIgnored private var hasHandledAutoStart = false
    @ObservationIgnored private var disposed = false
    @ObservationIgnored private var operationGeneration = 0

    init(
        controller: any VoiceExpenseControlling = IosVoiceExpenseController(),
        recorder: any VoiceExpenseRecording = VoiceExpenseRecorder(),
        recognition: any VoiceExpenseRecognizing = SystemVoiceExpenseRecognizer(),
        writeTasks: ApplicationWriteTasks = .shared,
        refreshWidget: @escaping @MainActor () -> Void = { SpesifyWidgetSummaryRefresher.shared.refresh() }
    ) {
        self.controller = controller
        self.recorder = recorder
        self.recognition = recognition
        self.writeTasks = writeTasks
        self.refreshWidget = refreshWidget
        loadSnapshot()
    }

    isolated deinit {
        loadingTask?.cancel()
        operationTask?.cancel()
        savingTask?.cancel()
        recorder.stop()
    }

    var draft: VoiceExpenseDraft? {
        switch state {
        case let .review(draft), let .saving(draft): draft
        case let .error(_, draft): draft
        default: nil
        }
    }

    var errorMessage: String? {
        get {
            if case let .error(message, _) = state { message } else { nil }
        }
        set {
            if let newValue { state = .error(newValue, draft: draft) }
            else if case let .error(_, draft) = state { state = draft.map(VoiceExpenseState.review) ?? .ready }
        }
    }

    var isRecording: Bool {
        if case .recording = state { true } else { false }
    }

    var isBusy: Bool {
        switch state {
        case .preparing(.microphone), .interpreting, .saving: true
        default: false
        }
    }

    var busyLabel: String {
        switch state {
        case .preparing(.microphone): voiceExpenseRecordingStartMessage()
        case .interpreting: voiceExpenseUnderstandingMessage()
        case let .saving(draft): voiceExpenseCommitBusyLabel(for: draft.intent)
        default: ""
        }
    }

    var canStartCapture: Bool { !disposed && snapshotLoaded && recognition.isAvailable }
    var canCommit: Bool { !disposed && draft != nil && !isBusy }
    var commitButtonTitle: String { voiceExpenseCommitButtonTitle(for: draft) }

    func toggleRecording() {
        guard !disposed, !isBusy else { return }
        if isRecording { stopRecording() } else { startRecording() }
    }

    func requestAutoStart() {
        guard !disposed else { return }
        shouldAutoStartWhenReady = true
        hasHandledAutoStart = false
        attemptPendingAutoStart()
    }

    func commit(onSuccess: @escaping () -> Void) {
        guard canCommit, let draft else { return }
        state = .saving(draft)
        statusMessage = nil
        let generation = operationGeneration
        let worker = writeTasks.perform { [controller, refreshWidget] in
            let result = try await controller.persist(draft: draft)
            if result.success { refreshWidget() }
            return result
        }
        savingTask = Task { [weak self, controller] in
            do {
                let result = try await worker.value
                try Task.checkCancellation()
                guard self?.disposed == false, self?.operationGeneration == generation else { return }
                if result.success {
                    let snapshot = try? await controller.loadSnapshotData()
                    try Task.checkCancellation()
                    guard let self, !disposed, generation == operationGeneration else { return }
                    if let snapshot { apply(snapshot: snapshot) }
                    state = .ready
                    onSuccess()
                } else {
                    guard let self, !disposed, generation == operationGeneration else { return }
                    state = .error(voiceExpenseCommitFailureMessage(result.message), draft: draft)
                }
            } catch is CancellationError {
                guard let self, !disposed, generation == operationGeneration else { return }
                state = .review(draft)
            } catch {
                guard let self, !disposed, generation == operationGeneration else { return }
                state = .error(voiceExpenseCommitFailureMessage(error.localizedDescription), draft: draft)
            }
        }
    }

    func dispose() {
        disposed = true
        operationGeneration += 1
        shouldAutoStartWhenReady = false
        loadingTask?.cancel()
        operationTask?.cancel()
        savingTask?.cancel()
        recorder.stop()
        state = .ready
        statusMessage = nil
    }

    private func loadSnapshot() {
        statusMessage = voiceExpenseSnapshotLoadingMessage()
        loadingTask = Task { [weak self, controller] in
            do {
                let snapshot = try await controller.loadSnapshotData()
                try Task.checkCancellation()
                guard let self, !disposed else { return }
                guard let snapshot else {
                    statusMessage = nil
                    state = .error(voiceExpenseSnapshotLoadFailureMessage(), draft: nil)
                    return
                }
                state = .ready
                apply(snapshot: snapshot)
            } catch {
                guard let self, !disposed else { return }
                statusMessage = nil
                state = (error is CancellationError || Task.isCancelled)
                    ? .ready : .error(voiceExpenseSnapshotLoadFailureMessage(), draft: nil)
            }
        }
    }

    private func attemptPendingAutoStart() {
        guard shouldAutoStartWhenReady, !hasHandledAutoStart,
              canStartCapture, !isBusy, !isRecording else { return }
        shouldAutoStartWhenReady = false
        hasHandledAutoStart = true
        startRecording()
    }

    private func startRecording() {
        guard canStartCapture else { return }
        statusMessage = nil
        transcript = ""
        state = .preparing(.microphone)
        operationGeneration += 1
        let generation = operationGeneration
        operationTask?.cancel()
        operationTask = Task { [weak self, recorder] in
            do {
                try Task.checkCancellation()
                try await recorder.start(
                    onTranscript: { [weak self] updatedTranscript in
                        guard let self, !disposed, generation == operationGeneration else { return }
                        transcript = updatedTranscript
                    },
                    onFailure: { [weak self] message in
                        guard let self, !disposed, generation == operationGeneration else { return }
                        self.recorder.stop()
                        operationGeneration += 1
                        state = .error(message, draft: nil)
                    }
                )
                try Task.checkCancellation()
                guard let self, !disposed, generation == operationGeneration else {
                    recorder.stop()
                    return
                }
                state = .recording
            } catch {
                recorder.stop()
                guard let self, !disposed, generation == operationGeneration else { return }
                state = error is CancellationError ? .ready : .error(error.localizedDescription, draft: nil)
            }
        }
    }

    private func stopRecording() {
        let text = recorder.stop().trimmingCharacters(in: .whitespacesAndNewlines)
        state = .ready
        guard !text.isEmpty else {
            state = .error(voiceExpenseNoSpeechMessage(), draft: nil)
            return
        }
        transcript = text
        interpretTranscript(text)
    }

    private func interpretTranscript(_ text: String) {
        guard snapshotLoaded else {
            state = .error(voiceExpenseSnapshotStillLoadingMessage(), draft: nil)
            return
        }
        guard recognition.isAvailable else {
            if let message = recognition.unavailableMessage { state = .error(message, draft: nil) }
            return
        }
        state = .interpreting
        statusMessage = nil
        operationGeneration += 1
        let generation = operationGeneration
        let categories = categoriesById.values.sorted { $0.name < $1.name }
        let expenses = Array(expensesById.values.sorted { $0.date > $1.date }.prefix(80))
        operationTask?.cancel()
        operationTask = Task { [weak self, recognition] in
            do {
                let interpretation = try await recognition.interpret(
                    transcript: text, categories: categories, expenses: expenses
                )
                try Task.checkCancellation()
                guard let self, !disposed, generation == operationGeneration else { return }
                if let draft = buildDraft(from: interpretation, transcript: text) {
                    state = .review(draft)
                } else {
                    state = .ready
                    statusMessage = unresolvedVoiceExpenseDraftMessage(
                        interpretation: interpretation, transcript: text, categoriesById: categoriesById
                    )
                }
            } catch {
                guard !Task.isCancelled, let self, !disposed, generation == operationGeneration else { return }
                if error is CancellationError {
                    state = .ready
                } else if let interpretation = parseSimpleExpenseIntent(transcript: text),
                          let draft = buildDraft(from: interpretation, transcript: text) {
                    state = .review(draft)
                } else {
                    state = .error(expenseParsingFailureMessage(for: error), draft: nil)
                }
            }
        }
    }

    private func buildDraft(from interpretation: VoiceExpenseInterpretation, transcript: String) -> VoiceExpenseDraft? {
        buildVoiceExpenseDraft(
            from: interpretation, transcript: transcript, categoriesById: categoriesById, expensesById: expensesById
        )
    }

    private func apply(snapshot: VoiceExpenseSnapshotData) {
        categoriesById = Dictionary(uniqueKeysWithValues: snapshot.categories.lazy.map { ($0.id, $0) })
        expensesById = Dictionary(uniqueKeysWithValues: snapshot.recentExpenses.lazy.map { ($0.id, $0) })
        snapshotLoaded = true
        statusMessage = recognition.isAvailable ? nil : recognition.unavailableMessage
        attemptPendingAutoStart()
    }
}
