import AVFoundation
import Speech

// Speech framework recorder and transcription capture for iOS voice expense input.

final class VoiceExpenseRecorder: @unchecked Sendable {
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
