import Testing
import UIKit
@testable import Spesify

@MainActor
struct VisionPaymentScreenshotOCRTests {
    @Test
    func readsMerchantAndAmountFromRenderedReceipt() async throws {
        let image = receipt(text: "CAFE ROMA\nCARD PAYMENT\nTOTAL EUR 12.50")
        let text = try await VisionPaymentScreenshotOCRService().recognizeText(in: image)
        #expect(text.contains("CAFE ROMA"))
        #expect(text.contains("12.50"))
    }

    @Test
    func blankImageKeepsTheNoTextError() async {
        do {
            _ = try await VisionPaymentScreenshotOCRService().recognizeText(in: receipt(text: ""))
            Issue.record("Blank image produced recognized text")
        } catch PaymentScreenshotOCRError.noText {
            // The actual Vision result retains the application's existing error contract.
        } catch {
            Issue.record("Unexpected OCR error: \(error)")
        }
    }

    @Test
    func cancelledCallerDoesNotStartRecognition() async {
        let service = VisionPaymentScreenshotOCRService()
        let image = receipt(text: "TOTAL EUR 12.50")
        let task = Task { try await service.recognizeText(in: image) }
        task.cancel()
        do {
            _ = try await task.value
            Issue.record("Cancelled OCR returned text")
        } catch {
            #expect(error is CancellationError)
        }
    }

    private func receipt(text: String) -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 1000, height: 500)).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1000, height: 500))
            (text as NSString).draw(
                in: CGRect(x: 50, y: 50, width: 900, height: 400),
                withAttributes: [.font: UIFont.systemFont(ofSize: 56), .foregroundColor: UIColor.black]
            )
        }
    }
}
