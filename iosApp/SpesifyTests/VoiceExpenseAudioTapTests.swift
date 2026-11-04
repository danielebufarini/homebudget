import AVFoundation
import Dispatch
import Foundation
import Speech
import Synchronization
import Testing
@testable import Spesify

@Suite
@MainActor
struct VoiceExpenseAudioTapTests {
    @Test
    func audioCallbackAcceptsRealPcmBuffersAwayFromTheMainActor() async throws {
        let request = RecordingSpeechAudioRequest()
        let format = try #require(AVAudioFormat(
            commonFormat: .pcmFormatFloat32, sampleRate: 16_000, channels: 1, interleaved: false
        ))
        let buffer = try #require(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 32))
        buffer.frameLength = 32
        let samples = try #require(buffer.floatChannelData?[0])
        samples.initialize(repeating: 0.25, count: 32)
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let node = AudioCallbackNode(buffer: buffer) {
                continuation.resume()
            }
            // The native method dispatch preserves the Objective-C block bridge used by AVFAudio.
            // Calling a returned Swift closure directly would miss the actor check in that bridge.
            VoiceExpenseAudioTap.install(on: node, format: format, request: request)
        }

        let observation = request.observation.withLock { $0 }
        #expect(observation.calls == 1)
        #expect(observation.frames == 32)
        #expect(observation.sample == 0.25)
        #expect(!observation.isMainThread)
    }
}

private struct AudioCallbackObservation: Sendable {
    var calls = 0
    var frames: AVAudioFrameCount = 0
    var sample: Float = 0
    var isMainThread = true
}

private final class RecordingSpeechAudioRequest: SFSpeechAudioBufferRecognitionRequest {
    let observation = Mutex(AudioCallbackObservation())

    override func append(_ audioPCMBuffer: AVAudioPCMBuffer) {
        observation.withLock {
            $0.calls += 1
            $0.frames = audioPCMBuffer.frameLength
            $0.sample = audioPCMBuffer.floatChannelData?[0][0] ?? 0
            $0.isMainThread = Thread.isMainThread
        }
    }
}

// AVAudioMixerNode already inherits unchecked Sendable from AVAudioNode. This spy adds only
// immutable fixture references: the buffer is initialized before handoff and never changed again,
// the completion is Sendable, and all observations live behind the request's Mutex.
private final class AudioCallbackNode: AVAudioMixerNode, @unchecked Sendable {
    private let buffer: AVAudioPCMBuffer
    private let completed: @Sendable () -> Void

    init(buffer: AVAudioPCMBuffer, completed: @escaping @Sendable () -> Void) {
        self.buffer = buffer
        self.completed = completed
        super.init()
    }

    override func installTap(
        onBus bus: AVAudioNodeBus,
        bufferSize: AVAudioFrameCount,
        format: AVAudioFormat?,
        block tapBlock: @escaping AVAudioNodeTapBlock
    ) {
        let work = audioCallbackWorkItem(
            tap: tapBlock, buffer: buffer,
            when: AVAudioTime(sampleTime: 0, atRate: buffer.format.sampleRate), completed: completed
        )
        DispatchQueue(label: "SpesifyTests.audio.callback").async(execute: work)
    }
}

private nonisolated func audioCallbackWorkItem(
    tap: @escaping AVAudioNodeTapBlock,
    buffer: AVAudioPCMBuffer,
    when: AVAudioTime,
    completed: @escaping @Sendable () -> Void
) -> DispatchWorkItem {
    DispatchWorkItem {
        tap(buffer, when)
        completed()
    }
}
