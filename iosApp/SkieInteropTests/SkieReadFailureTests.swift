import ComposeApp
import Testing

@Suite(.serialized)
@MainActor
struct SkieReadFailureTests {
    @Test func ordinaryReadFailureDoesNotTerminateSwift() async {
        let fixture = SkieFailureFixture()
        var received = 0
        for await state in fixture.observer.states {
            switch onEnum(of: state) {
            case .data: Issue.record("Unexpected snapshot from a failing read")
            case .failed: break
            }
            received += 1
        }
        #expect(received == 1)
    }
}
