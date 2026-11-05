import UIKit
import OSLog

final class ShareViewController: UIViewController {
    private let statusLabel = UILabel()
    private let spinner = UIActivityIndicatorView(style: .medium)
    private var receiveTask: Task<Void, Never>?
    private var receiveError: Error?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        statusLabel.text = NSLocalizedString("Receiving screenshot…", comment: "")
        statusLabel.font = .preferredFont(forTextStyle: .body)
        statusLabel.adjustsFontForContentSizeCategory = true
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 0
        let done = UIButton(type: .system)
        done.setTitle(NSLocalizedString("Done", comment: ""), for: .normal)
        done.isHidden = true
        done.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            if let receiveError {
                extensionContext?.cancelRequest(withError: receiveError)
            } else {
                extensionContext?.completeRequest(returningItems: nil)
            }
        }, for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [spinner, statusLabel, done])
        stack.axis = .vertical
        stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor)
        ])
        spinner.startAnimating()
        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        receiveTask = Task { [weak self] in
            do {
                let inbox = try SharedScreenshotInbox.appGroup()
                _ = try await SharedScreenshotReceiver.receive(items, into: inbox)
                guard !Task.isCancelled else { return }
                self?.statusLabel.text = NSLocalizedString(
                    "Screenshot received. Open Spesify to review the expense.", comment: ""
                )
            } catch {
                guard !Task.isCancelled else { return }
                self?.receiveError = error
                let reason = (error as? SharedScreenshotError).map { String(describing: $0) }
                    ?? String(describing: type(of: error))
                Logger(subsystem: "it.danielebufarini.spesify.share", category: "ScreenshotImport")
                    .error("Unable to receive screenshot: \(reason, privacy: .public)")
                self?.statusLabel.text = NSLocalizedString(
                    "Unable to receive this screenshot. Please try sharing it again.", comment: ""
                )
            }
            self?.spinner.stopAnimating()
            done.isHidden = false
        }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        receiveTask?.cancel()
    }

    deinit { receiveTask?.cancel() }
}
