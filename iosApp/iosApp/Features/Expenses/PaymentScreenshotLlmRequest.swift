@preconcurrency import ComposeApp
import Synchronization

// The foreign closure is installed only by the Kotlin registry, which serializes completion
// and resumes a thread-safe CancellableContinuation. It carries no Swift UI/actor state.
// Sendability is limited to this interop box; the request owns invocation and release.
private struct PaymentScreenshotLlmCompletion: @unchecked Sendable {
    let complete: (String) -> Void
}

final class PaymentScreenshotLlmRequest: IosLocalLlmExpenseTextRequest, Sendable {
    private struct State {
        var completion: PaymentScreenshotLlmCompletion?
        var task: Task<Void, Never>?
        var cancelled = false
    }
    private let state: Mutex<State>

    init(completion: @escaping (String) -> Void) {
        state = Mutex(State(completion: PaymentScreenshotLlmCompletion(complete: completion)))
    }

    func attach(_ task: Task<Void, Never>) {
        let cancel = state.withLock { state in
            if !state.cancelled && state.completion != nil { state.task = task }
            return state.cancelled
        }
        if cancel { task.cancel() }
    }

    func finish(_ json: String) {
        let completion = state.withLock { state in
            let completion = state.completion
            state.completion = nil
            state.task = nil
            return completion
        }
        completion?.complete(json)
    }

    func cancel() {
        let task = state.withLock { state in
            state.cancelled = true
            state.completion = nil
            let task = state.task
            state.task = nil
            return task
        }
        task?.cancel()
    }
}
