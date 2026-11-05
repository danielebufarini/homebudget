import Foundation
import UIKit
import UniformTypeIdentifiers

@MainActor
enum SharedScreenshotReceiver {
    static func receive(_ items: [NSExtensionItem], into inbox: SharedScreenshotInbox) async throws -> URL {
        try Task.checkCancellation()
        let images = items.flatMap { $0.attachments ?? [] }
            .filter { $0.hasItemConformingToTypeIdentifier(UTType.image.identifier) }
        guard images.count == 1, let provider = images.first else {
            throw SharedScreenshotError.expectedOneImage
        }
        // Prefer a lossless representation when the screenshot provider offers one.
        let identifier = provider.hasItemConformingToTypeIdentifier(UTType.png.identifier)
            ? UTType.png.identifier : UTType.image.identifier
        let data = try await loadData(from: provider, identifier: identifier)
        try Task.checkCancellation()
        return try await inbox.store(data)
    }

    private static func loadData(from provider: NSItemProvider, identifier: String) async throws -> Data {
        let data: Data = try await withCheckedThrowingContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: identifier) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    do {
                        // UIKit can supply a securely archived UIImage for generic public.image.
                        // Decode only UIImage; ordinary PNG/JPEG/HEIC data stays unchanged.
                        if data.starts(with: Data("bplist00".utf8)) {
                            guard let image = try NSKeyedUnarchiver.unarchivedObject(ofClass: UIImage.self, from: data),
                                  let png = image.pngData() else { throw SharedScreenshotError.invalidImage }
                            continuation.resume(returning: png)
                        } else {
                            continuation.resume(returning: data)
                        }
                    } catch {
                        continuation.resume(throwing: error)
                    }
                } else {
                    continuation.resume(throwing: SharedScreenshotError.invalidImage)
                }
            }
        }
        return data
    }
}
