import AVFoundation
import Speech

enum VoiceExpenseAudioTap {
    // AVFAudio owns callback delivery and may invoke the tap away from the main thread.
    // Create its Objective-C block outside actor isolation; it captures only the native request.
    // Recorder lifecycle stays on MainActor and removes the tap before ending that request.
    nonisolated static func install(
        on node: AVAudioNode,
        format: AVAudioFormat,
        request: SFSpeechAudioBufferRecognitionRequest
    ) {
        node.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }
    }
}
