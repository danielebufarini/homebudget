@preconcurrency import ComposeApp
import FoundationModels
import SwiftUI

// Main-actor orchestration for the iOS voice expense entry flow.

@MainActor
final class VoiceExpenseEntryViewModel: ObservableObject {
    @Published var transcript = ""
    @Published var statusMessage: String?
    @Published var errorMessage: String?
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
        voiceExpenseCommitButtonTitle(for: draft)
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
        busyLabel = voiceExpenseCommitBusyLabel(for: draft.intent)
        statusMessage = nil
        errorMessage = nil

        Task {
            let result = await controller.persist(draft: draft)
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
                    errorMessage = voiceExpenseCommitFailureMessage(finalResult.message)
                }
            }
        }
    }

    func dispose() {
        recorder.stop()
        controller.dispose()
    }

    private func loadSnapshot() {
        statusMessage = voiceExpenseSnapshotLoadingMessage()
        errorMessage = nil
        Task {
            let snapshotData = await controller.loadSnapshotData()
            await MainActor.run {
                guard let snapshotData else {
                    statusMessage = nil
                    errorMessage = voiceExpenseSnapshotLoadFailureMessage()
                    return
                }

                apply(snapshot: snapshotData)
            }
        }
    }

    private func startRecording() {
        statusMessage = nil
        errorMessage = nil
        draft = nil
        transcript = ""
        isBusy = true
        busyLabel = voiceExpenseRecordingStartMessage()

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
                errorMessage = error.localizedDescription
            }
        }
    }

    private func stopRecording() {
        let capturedTranscript = recorder.stop().trimmingCharacters(in: .whitespacesAndNewlines)
        isRecording = false

        guard !capturedTranscript.isEmpty else {
            errorMessage = voiceExpenseNoSpeechMessage()
            return
        }

        transcript = capturedTranscript
        interpretTranscript(capturedTranscript)
    }

    private func interpretTranscript(_ text: String) {
        guard snapshotLoaded else {
            errorMessage = voiceExpenseSnapshotStillLoadingMessage()
            return
        }

        guard languageModel.isAvailable else {
            errorMessage = availabilityMessage(for: languageModel.availability)
            return
        }

        isBusy = true
        busyLabel = voiceExpenseUnderstandingMessage()
        statusMessage = nil
        errorMessage = nil
        draft = nil

        Task {
            do {
                let interpretation = try await parseExpenseIntent(
                    transcript: text,
                    categories: Array(categoriesById.values).sorted { $0.name < $1.name },
                    expenses: Array(
                        expensesById.values
                        .sorted { $0.date > $1.date }
                        .prefix(80)
                    )
                )

                let nextDraft = buildDraft(from: interpretation, transcript: text)
                await MainActor.run {
                    draft = nextDraft
                    statusMessage = nextDraft == nil
                        ? unresolvedVoiceExpenseDraftMessage(
                            interpretation: interpretation,
                            transcript: text,
                            categoriesById: categoriesById
                        )
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
                        errorMessage = nil
                        isBusy = false
                        busyLabel = ""
                    }
                    return
                }

                await MainActor.run {
                    isBusy = false
                    busyLabel = ""
                    errorMessage = expenseParsingFailureMessage(for: error)
                }
            }
        }
    }

    private func buildDraft(from interpretation: VoiceExpenseInterpretation, transcript: String) -> VoiceExpenseDraft? {
        buildVoiceExpenseDraft(
            from: interpretation,
            transcript: transcript,
            categoriesById: categoriesById,
            expensesById: expensesById
        )
    }

    private func refreshSnapshotAfterSave() async {
        guard let snapshot = await controller.loadSnapshotData() else {
            return
        }

        await MainActor.run {
            apply(snapshot: snapshot)
        }
    }

    private func apply(snapshot: VoiceExpenseSnapshotData) {
        categoriesById = Dictionary(uniqueKeysWithValues: snapshot.categories.lazy.map { ($0.id, $0) })
        expensesById = Dictionary(uniqueKeysWithValues: snapshot.recentExpenses.lazy.map { ($0.id, $0) })
        snapshotLoaded = true
        statusMessage = voiceExpenseAvailabilityStatusMessage(
            snapshotLoaded: snapshotLoaded,
            availability: languageModel.availability
        )
    }
}
