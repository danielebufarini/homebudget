import AVFoundation
import Speech

// Speech framework recorder and transcription capture for iOS voice expense input.

@MainActor
final class VoiceExpenseRecorder: VoiceExpenseRecording {
    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let recognizer = SFSpeechRecognizer(locale: .current)
    private var currentTranscript = ""
    private var generation = 0
    private var tapInstalled = false
    private var sessionIsActive = false
    private let requestPermissions: @Sendable () async throws -> Void

    init(requestPermissions: @escaping @Sendable () async throws -> Void = VoiceExpenseRecorder.requestRecordingPermissions) {
        self.requestPermissions = requestPermissions
    }

    func start(
        onTranscript: @escaping @MainActor @Sendable (String) -> Void,
        onFailure: @escaping @MainActor @Sendable (String) -> Void
    ) async throws {
        stop()
        let requestGeneration = generation
        try await requestPermissions()
        try Task.checkCancellation()
        guard requestGeneration == generation else { throw CancellationError() }

        guard let recognizer else {
            throw VoiceExpenseError.transcriptionUnavailable
        }

        guard recognizer.isAvailable else {
            throw VoiceExpenseError.transcriptionUnavailable
        }

        currentTranscript = ""
        do {
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            if recognizer.supportsOnDeviceRecognition {
                request.requiresOnDeviceRecognition = true
            }
            recognitionRequest = request

            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            sessionIsActive = true

            let inputNode = audioEngine.inputNode
            let inputFormat = inputNode.inputFormat(forBus: 0)
            let outputFormat = inputNode.outputFormat(forBus: 0)
            let format = validRecordingFormat(primary: inputFormat, secondary: outputFormat)
            guard let format else {
                throw VoiceExpenseError.microphoneUnavailable
            }
            VoiceExpenseAudioTap.install(on: inputNode, format: format, request: request)
            tapInstalled = true

            audioEngine.prepare()
            try audioEngine.start()

            recognitionTask = recognizer.recognitionTask(with: request) { @Sendable [weak self] result, error in
                // Extract values before crossing executors; Speech result/error objects stay on their callback.
                let transcript = result?.bestTranscription.formattedString.trimmingCharacters(in: .whitespacesAndNewlines)
                let failureMessage = error?.localizedDescription
                Task { @MainActor [weak self] in
                    guard let self, requestGeneration == self.generation else { return }
                    if let transcript {
                        self.currentTranscript = transcript
                        onTranscript(transcript)
                    }
                    if let failureMessage {
                        self.stop()
                        onFailure(failureMessage)
                    }
                }
            }
        } catch {
            stop()
            throw error
        }
    }

    @discardableResult
    func stop() -> String {
        generation += 1
        audioEngine.stop()
        if tapInstalled {
            audioEngine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionRequest = nil
        recognitionTask = nil

        if sessionIsActive {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            sessionIsActive = false
        }

        return currentTranscript
    }

    private nonisolated static func requestRecordingPermissions() async throws {
        try Task.checkCancellation()
        let speechAuthorized = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        guard speechAuthorized else {
            throw VoiceExpenseError.speechPermissionDenied
        }

        try Task.checkCancellation()

        let micAuthorized = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard micAuthorized else {
            throw VoiceExpenseError.microphonePermissionDenied
        }
        try Task.checkCancellation()
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
