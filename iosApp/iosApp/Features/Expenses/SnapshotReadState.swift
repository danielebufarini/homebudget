import Observation

@MainActor
@Observable
final class SnapshotReadState {
    private(set) var hasLoadedData = false
    private(set) var isLoading = true
    var showsError = false

    @ObservationIgnored private(set) var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private(set) var isStopped = true

    var isUnavailable: Bool { !isLoading && !hasLoadedData }

    deinit { task?.cancel() }

    func start(_ collect: @escaping @MainActor (Int) async -> Void) {
        let previous = task
        previous?.cancel()
        generation += 1
        let current = generation
        isStopped = false
        isLoading = !hasLoadedData
        showsError = false
        task = Task { [weak self] in
            await previous?.value
            guard self?.accepts(current) == true else { return }
            await collect(current)
        }
    }

    func wait() async {
        let current = task
        await withTaskCancellationHandler {
            await current?.value
        } onCancel: {
            current?.cancel()
        }
    }

    func accepts(_ current: Int) -> Bool {
        !Task.isCancelled && !isStopped && current == generation
    }

    func receivedData() {
        hasLoadedData = true
        isLoading = false
        showsError = false
    }

    func receivedFailure() {
        isLoading = false
        showsError = true
    }

    func resetForNewSource() {
        hasLoadedData = false
    }

    func stop() {
        isStopped = true
        generation += 1
        task?.cancel()
        showsError = false
    }
}
