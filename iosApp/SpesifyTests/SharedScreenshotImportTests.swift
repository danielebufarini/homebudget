import Foundation
import Testing
import UIKit
import UniformTypeIdentifiers
@testable import Spesify

@MainActor
struct SharedScreenshotImportTests {
    @Test(arguments: ["image", "data", "file"])
    func receivesImagesWithoutRequiringASavedDocument(representation: String) async throws {
        let container = temporaryContainer()
        defer { try? FileManager.default.removeItem(at: container) }
        let inbox = SharedScreenshotInbox(containerURL: container)
        let image = receipt()
        let png = try #require(image.pngData())
        let provider: NSItemProvider
        switch representation {
        case "image":
            provider = NSItemProvider(object: image)
        case "data":
            provider = NSItemProvider()
            provider.registerDataRepresentation(forTypeIdentifier: UTType.png.identifier, visibility: .all) { completion in
                completion(png, nil)
                return nil
            }
        default:
            let source = container.appendingPathComponent("screenshot.png")
            try FileManager.default.createDirectory(at: container, withIntermediateDirectories: true)
            try png.write(to: source)
            provider = try #require(NSItemProvider(contentsOf: source))
        }
        let file = try await SharedScreenshotReceiver.receive([item(provider)], into: inbox)
        #expect(try Data(contentsOf: file) == png)
        #expect(file.pathExtension == "png")
        #expect(try await inbox.next() == file)
        #expect(try await ContentTransferFiles.isImageDocument(file))
        try await inbox.remove(file)
        #expect(try await inbox.next() == nil)
    }

    @Test
    func acceptsTheArchivedUIImageRepresentationUsedByTheSystemShareSheet() async throws {
        let container = temporaryContainer()
        defer { try? FileManager.default.removeItem(at: container) }
        let inbox = SharedScreenshotInbox(containerURL: container)
        let image = receipt()
        let archive = try NSKeyedArchiver.archivedData(withRootObject: image, requiringSecureCoding: true)
        let provider = NSItemProvider()
        provider.registerDataRepresentation(forTypeIdentifier: UTType.image.identifier, visibility: .all) { completion in
            completion(archive, nil)
            return nil
        }
        let file = try await SharedScreenshotReceiver.receive([item(provider)], into: inbox)
        #expect(try Data(contentsOf: file) == image.pngData())
        #expect(file.pathExtension == "png")
    }

    @Test
    func separateSharesSurviveReopeningTheInboxWithoutOverwritingImages() async throws {
        let container = temporaryContainer()
        defer { try? FileManager.default.removeItem(at: container) }
        let writer = SharedScreenshotInbox(containerURL: container)
        let png = try #require(receipt().pngData())
        let first = try await writer.store(png)
        let second = try await writer.store(png)
        #expect(first != second)
        let reader = SharedScreenshotInbox(containerURL: container)
        #expect(try await reader.next() == first)
        try await reader.remove(first)
        #expect(try await reader.next() == second)
        #expect(try Data(contentsOf: second) == png)
    }

    @Test
    func textAndMultipleImagesAreRejectedWithoutQueuingAPartialShare() async throws {
        let container = temporaryContainer()
        defer { try? FileManager.default.removeItem(at: container) }
        let inbox = SharedScreenshotInbox(containerURL: container)
        let image = NSItemProvider(object: receipt())
        await #expect(throws: SharedScreenshotError.self) {
            try await SharedScreenshotReceiver.receive([item(NSItemProvider(object: "text" as NSString))], into: inbox)
        }
        await #expect(throws: SharedScreenshotError.self) {
            try await SharedScreenshotReceiver.receive([item(image), item(image)], into: inbox)
        }
        #expect(try await inbox.next() == nil)
    }

    @Test
    func invalidImageBytesNeverEnterTheInbox() async throws {
        let container = temporaryContainer()
        defer { try? FileManager.default.removeItem(at: container) }
        let inbox = SharedScreenshotInbox(containerURL: container)
        await #expect(throws: SharedScreenshotError.self) {
            try await inbox.store(Data("not an image".utf8))
        }
        #expect(try await inbox.next() == nil)
    }

    @Test
    func providerFailureNeverEnqueuesAnImage() async throws {
        let container = temporaryContainer()
        defer { try? FileManager.default.removeItem(at: container) }
        let inbox = SharedScreenshotInbox(containerURL: container)
        let provider = NSItemProvider()
        provider.registerDataRepresentation(forTypeIdentifier: UTType.image.identifier, visibility: .all) { completion in
            completion(nil, NSError(domain: "ScreenshotProvider", code: 1))
            return nil
        }
        await #expect(throws: (any Error).self) {
            try await SharedScreenshotReceiver.receive([item(provider)], into: inbox)
        }
        #expect(try await inbox.next() == nil)
    }

    private func item(_ provider: NSItemProvider) -> NSExtensionItem {
        let item = NSExtensionItem()
        item.attachments = [provider]
        return item
    }

    private func temporaryContainer() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    }

    private func receipt() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 320, height: 120)).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 320, height: 120))
            ("Payment €12.34\nCoffee shop" as NSString).draw(
                at: CGPoint(x: 16, y: 20),
                withAttributes: [.font: UIFont.systemFont(ofSize: 24), .foregroundColor: UIColor.black]
            )
        }
    }
}
