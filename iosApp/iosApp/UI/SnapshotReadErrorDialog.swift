import SwiftUI

struct SnapshotReadErrorDialog: View {
    let onRetry: () -> Void
    let onClose: () -> Void

    var body: some View {
        AppGlassDialogOverlay {
            AppGlassDialogCard(
                title: appLocalized("Unable to load data"),
                message: appLocalized("Unable to read data. Try again to reload it.")
            ) {
                AppGlassDialogButton(title: appLocalized("Retry"), action: onRetry)
                AppGlassDialogButton(title: appLocalized("Close"), action: onClose)
            }
        }
    }
}
