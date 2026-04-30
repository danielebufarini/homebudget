@preconcurrency import ComposeApp
import AVFoundation
import FoundationModels
import Speech
import SwiftUI

@MainActor
final class VoiceExpenseEntryViewModel: ObservableObject {
    @Published var transcript = ""
    @Published var statusMessage: String?
    @Published var draft: VoiceExpenseDraft?
    @Published var isRecording = false
    @Published var isBusy = false
    @Published var busyLabel = ""

    private let controller = IosVoiceExpenseController()
    private let recorder = VoiceExpenseRecorder()
    private let languageModel = SystemLanguageModel.default
    private var categoriesById: [String: VoiceExpenseCategory] = [:]
    private var expensesById: [String: VoiceExpenseCandidate] = [:]
    private var snapshotLoaded = false

    init() {
        loadSnapshot()
    }

    var canStartCapture: Bool {
        snapshotLoaded && languageModel.isAvailable
    }

    var canCommit: Bool {
        draft != nil && !isBusy
    }

    var commitButtonTitle: String {
        draft?.actionButtonTitle ?? appLocalized("Save")
    }

    func toggleRecording() {
        if isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }

    func commit(onSuccess: @escaping () -> Void) {
        guard let draft else {
            return
        }

        isBusy = true
        busyLabel = draft.intent == .create ? appLocalized("Saving expense...") : appLocalized("Updating expense...")
        statusMessage = nil

        Task {
            let result = await persist(draft: draft)
            let finalResult: (success: Bool, message: String?)
            if result.success {
                await refreshSnapshotAfterSave()
                finalResult = result
            } else {
                finalResult = result
            }
            await MainActor.run {
                isBusy = false
                busyLabel = ""
                if finalResult.success {
                    onSuccess()
                } else {
                    statusMessage = finalResult.message ?? appLocalized("Unable to save expense.")
                }
            }
        }
    }

    func dispose() {
        recorder.stop()
        controller.dispose()
    }

    private func loadSnapshot() {
        statusMessage = appLocalized("Loading budget data...")
        controller.loadSnapshot { [weak self] snapshot in
            let snapshotData = snapshot.map(buildVoiceExpenseSnapshotData(from:))
            Task { @MainActor in
                guard let self else {
                    return
                }

                guard let snapshotData else {
                    self.statusMessage = appLocalized("Unable to load expenses and categories.")
                    return
                }

                self.apply(snapshot: snapshotData)
            }
        }
    }

    private func startRecording() {
        statusMessage = nil
        draft = nil
        transcript = ""
        isBusy = true
        busyLabel = appLocalized("Starting microphone...")

        Task { @MainActor in
            do {
                try await recorder.start { [weak self] updatedTranscript in
                    self?.transcript = updatedTranscript
                }
                isRecording = true
                isBusy = false
                busyLabel = ""
            } catch {
                isBusy = false
                busyLabel = ""
                statusMessage = error.localizedDescription
            }
        }
    }

    private func stopRecording() {
        let capturedTranscript = recorder.stop().trimmingCharacters(in: .whitespacesAndNewlines)
        isRecording = false

        guard !capturedTranscript.isEmpty else {
            statusMessage = appLocalized("No speech was captured.")
            return
        }

        transcript = capturedTranscript
        interpretTranscript(capturedTranscript)
    }

    private func interpretTranscript(_ text: String) {
        guard snapshotLoaded else {
            statusMessage = appLocalized("Budget data is still loading.")
            return
        }

        guard languageModel.isAvailable else {
            statusMessage = availabilityMessage(for: languageModel.availability)
            return
        }

        isBusy = true
        busyLabel = appLocalized("Understanding expense...")
        statusMessage = nil
        draft = nil

        Task {
            do {
                let interpretation = try await parseExpenseIntent(
                    transcript: text,
                    categories: Array(categoriesById.values).sorted { $0.name < $1.name },
                    expenses: Array(expensesById.values)
                        .sorted { $0.date > $1.date }
                        .prefix(80)
                        .map { $0 }
                )

                let nextDraft = buildDraft(from: interpretation, transcript: text)
                await MainActor.run {
                    draft = nextDraft
                    statusMessage = nextDraft == nil
                        ? unresolvedDraftMessage(for: interpretation, transcript: text)
                        : nil
                    isBusy = false
                    busyLabel = ""
                }
            } catch {
                if let fallbackInterpretation = parseSimpleExpenseIntent(transcript: text),
                   let fallbackDraft = buildDraft(from: fallbackInterpretation, transcript: text) {
                    await MainActor.run {
                        draft = fallbackDraft
                        statusMessage = nil
                        isBusy = false
                        busyLabel = ""
                    }
                    return
                }

                await MainActor.run {
                    isBusy = false
                    busyLabel = ""
                    statusMessage = expenseParsingFailureMessage(for: error)
                }
            }
        }
    }

    private func buildDraft(from interpretation: VoiceExpenseInterpretation, transcript: String) -> VoiceExpenseDraft? {
        switch interpretation.intent {
        case .create:
            guard
                let amountInput = normalizeAmountInput(interpretation.amount),
                let category = resolveExpenseCategory(
                    categoryId: interpretation.categoryId,
                    categoryName: interpretation.categoryName,
                    transcript: transcript,
                    summary: interpretation.summary
                )
            else {
                return nil
            }

            let resolvedDate = resolveExpenseDate(
                isoValue: interpretation.date,
                transcript: transcript,
                summary: interpretation.summary,
                defaultDate: Calendar.current.startOfDay(for: Date())
            )

            return VoiceExpenseDraft(
                intent: .create,
                expenseId: nil,
                amountInput: amountInput,
                categoryId: category.id,
                categoryName: category.name,
                description: interpretation.description.trimmedNilIfBlank,
                date: resolvedDate,
                isShared: interpretation.isShared ?? false,
                summary: interpretation.summary
            )

        case .update:
            guard
                let expenseId = interpretation.expenseId,
                let existingExpense = expensesById[expenseId]
            else {
                return nil
            }

            let category = resolveExpenseCategory(
                categoryId: interpretation.categoryId,
                categoryName: interpretation.categoryName,
                transcript: transcript,
                summary: interpretation.summary
            ) ?? categoriesById[existingExpense.categoryId]
            guard let category else {
                return nil
            }

            return VoiceExpenseDraft(
                intent: .update,
                expenseId: expenseId,
                amountInput: normalizeAmountInput(interpretation.amount) ?? existingExpense.amountInput,
                categoryId: category.id,
                categoryName: category.name,
                description: interpretation.description ?? existingExpense.description,
                date: resolveExpenseDate(
                    isoValue: interpretation.date,
                    transcript: transcript,
                    summary: interpretation.summary,
                    defaultDate: existingExpense.date
                ),
                isShared: interpretation.isShared ?? existingExpense.isShared,
                summary: interpretation.summary
            )

        case .needClarification:
            return nil
        }
    }

    private func persist(draft: VoiceExpenseDraft) async -> (success: Bool, message: String?) {
        await withCheckedContinuation { continuation in
            let completion: (KotlinBoolean?, String?) -> Void = { success, message in
                continuation.resume(returning: (success?.boolValue == true, message))
            }

            switch draft.intent {
            case .create:
                controller.createExpense(
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared,
                    onComplete: completion
                )
            case .update:
                guard let expenseId = draft.expenseId else {
                    continuation.resume(returning: (false, appLocalized("Expense not found.")))
                    return
                }

                controller.updateExpense(
                    expenseId: expenseId,
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared,
                    onComplete: completion
                )
            case .needClarification:
                continuation.resume(returning: (false, appLocalized("The spoken command still needs clarification.")))
            }
        }
    }

    private func reloadSnapshot() async -> VoiceExpenseSnapshotData? {
        await withCheckedContinuation { continuation in
            controller.loadSnapshot { snapshot in
                continuation.resume(returning: snapshot.map(buildVoiceExpenseSnapshotData(from:)))
            }
        }
    }

    private func refreshSnapshotAfterSave() async {
        guard let snapshot = await reloadSnapshot() else {
            return
        }

        await MainActor.run {
            apply(snapshot: snapshot)
        }
    }

    private func apply(snapshot: VoiceExpenseSnapshotData) {
        categoriesById = Dictionary(uniqueKeysWithValues: snapshot.categories.map { ($0.id, $0) })
        expensesById = Dictionary(uniqueKeysWithValues: snapshot.recentExpenses.map { ($0.id, $0) })
        snapshotLoaded = true
        statusMessage = languageModel.isAvailable
            ? nil
            : availabilityMessage(for: languageModel.availability)
    }

    private func resolveExpenseCategory(
        categoryId: String?,
        categoryName: String?,
        transcript: String,
        summary: String?
    ) -> VoiceExpenseCategory? {
        if let categoryId, let category = categoriesById[categoryId] {
            return category
        }

        if let categoryName {
            if let exactMatch = categoriesById.values.first(where: { $0.name.caseInsensitiveCompare(categoryName) == .orderedSame }) {
                return exactMatch
            }
            let normalizedCategoryName = normalizeVoiceExpenseToken(categoryName)
            if let normalizedMatch = categoriesById.values.first(where: {
                normalizeVoiceExpenseToken($0.name) == normalizedCategoryName
            }) {
                return normalizedMatch
            }
        }

        let searchText = [transcript, summary]
            .compactMap { $0 }
            .joined(separator: " ")
        return categoriesById.values.first(where: { category in
            voiceExpenseCategoryAliases(for: category).contains { alias in
                searchText.localizedCaseInsensitiveContains(alias)
            }
        })
    }

    private func unresolvedDraftMessage(for interpretation: VoiceExpenseInterpretation, transcript: String) -> String {
        switch interpretation.intent {
        case .needClarification:
            return interpretation.summary
        case .create, .update:
            if normalizeAmountInput(interpretation.amount) == nil {
                return appLocalized("I could not understand the amount well enough to prepare the expense.")
            }
            if resolveExpenseCategory(
                categoryId: interpretation.categoryId,
                categoryName: interpretation.categoryName,
                transcript: transcript,
                summary: interpretation.summary
            ) == nil {
                return appLocalized("I could not match the spoken category to one of your categories.")
            }
            return appLocalized("I understood the request, but I could not prepare a saveable expense draft.")
        }
    }
}

private final class VoiceExpenseRecorder: @unchecked Sendable {
    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let recognizer = SFSpeechRecognizer(locale: .current)
    private var currentTranscript = ""

    func start(onTranscript: @escaping @MainActor @Sendable (String) -> Void) async throws {
        try await requestPermissions()

        guard let recognizer else {
            throw VoiceExpenseError.transcriptionUnavailable
        }

        guard recognizer.isAvailable else {
            throw VoiceExpenseError.transcriptionUnavailable
        }

        stop()

        currentTranscript = ""
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition {
            request.requiresOnDeviceRecognition = true
        }
        recognitionRequest = request

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.inputFormat(forBus: 0)
        let outputFormat = inputNode.outputFormat(forBus: 0)
        let format = validRecordingFormat(primary: inputFormat, secondary: outputFormat)
        guard let format else {
            throw VoiceExpenseError.microphoneUnavailable
        }
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        try audioEngine.start()

        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else {
                return
            }

            if let result {
                let transcript = result.bestTranscription.formattedString.trimmingCharacters(in: .whitespacesAndNewlines)
                Task { @MainActor in
                    self.currentTranscript = transcript
                    onTranscript(transcript)
                }
            }

            if error != nil {
                Task { @MainActor in
                    self.stop()
                }
            }
        }
    }

    @discardableResult
    func stop() -> String {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionRequest = nil
        recognitionTask = nil

        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            // Keep cleanup best-effort.
        }

        return currentTranscript
    }

    private nonisolated func requestPermissions() async throws {
        let speechAuthorized = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        guard speechAuthorized else {
            throw VoiceExpenseError.speechPermissionDenied
        }

        let micAuthorized = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard micAuthorized else {
            throw VoiceExpenseError.microphonePermissionDenied
        }
    }

    private func validRecordingFormat(
        primary: AVAudioFormat,
        secondary: AVAudioFormat
    ) -> AVAudioFormat? {
        if primary.sampleRate > 0, primary.channelCount > 0 {
            return primary
        }
        if secondary.sampleRate > 0, secondary.channelCount > 0 {
            return secondary
        }
        return nil
    }
}

private enum VoiceExpenseError: LocalizedError {
    case speechPermissionDenied
    case microphonePermissionDenied
    case microphoneUnavailable
    case transcriptionUnavailable

    var errorDescription: String? {
        switch self {
        case .speechPermissionDenied:
            return appLocalized("Speech recognition permission is required.")
        case .microphonePermissionDenied:
            return appLocalized("Microphone permission is required.")
        case .microphoneUnavailable:
            return appLocalized("No usable microphone input is available.")
        case .transcriptionUnavailable:
            return appLocalized("Speech transcription is unavailable on this device.")
        }
    }
}
