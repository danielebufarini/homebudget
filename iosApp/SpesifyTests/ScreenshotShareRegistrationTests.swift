import Foundation
import Testing
@testable import Spesify

struct ScreenshotShareRegistrationTests {
    @Test
    func installedAppOffersAShareExtensionForOneImage() throws {
        let plugins = try #require(Bundle.main.builtInPlugInsURL)
        let bundles = try FileManager.default.contentsOfDirectory(at: plugins, includingPropertiesForKeys: nil)
            .compactMap(Bundle.init(url:))
        let share = try #require(bundles.first {
            ($0.infoDictionary?["NSExtension"] as? [String: Any])?["NSExtensionPointIdentifier"] as? String
                == "com.apple.share-services"
        })
        #expect(share.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String == "Spesify")
        let configuration = try #require(share.infoDictionary?["NSExtension"] as? [String: Any])
        #expect(configuration["NSExtensionPrincipalClass"] as? String == "SpesifyShareExtension.ShareViewController")
        let attributes = try #require(configuration["NSExtensionAttributes"] as? [String: Any])
        let activation = try #require(attributes["NSExtensionActivationRule"] as? [String: Any])
        #expect(activation["NSExtensionActivationSupportsImageWithMaxCount"] as? Int == 1)
        #expect(activation["NSExtensionActivationSupportsText"] == nil)
    }
}
