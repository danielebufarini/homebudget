import PhotosUI
import SwiftUI

struct PaymentScreenshotImportSheet: View {
    let onCandidates: ([NativeExpenseEditorPrefill]) -> Void
    let onClose: () -> Void

    @State private var viewModel = PaymentScreenshotImportViewModel()
    @State private var bannerPresenter = AppGlassBannerPresenter()
    @State private var selectedItem: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                AppGlassSheetContentScrollView(spacing: 18) {
                    AppGlassSheetSection(spacing: 14) {
                        Text(appLocalized("Select a payment screenshot. Spesify reads it on this device and opens an editable expense draft."))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        PhotosPicker(
                            selection: $selectedItem,
                            matching: .images,
                            photoLibrary: .shared()
                        ) {
                            HStack(spacing: 10) {
                                Image(systemName: "photo.on.rectangle.angled")
                                Text(appLocalized("Select Screenshot"))
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.glassProminent)
                        .disabled(viewModel.isBusy)
                    }

                    if viewModel.isBusy {
                        ProgressView(viewModel.busyLabel)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    AppGlassSheetSection(title: appLocalized("Status")) {
                        Text(viewModel.statusMessage)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                AppGlassBannerOverlay(presenter: bannerPresenter)
            }
            .appGlassSheetChrome()
            .navigationTitle(appLocalized("Payment Screenshot"))
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                AppGlassSheetActionBar {
                    Button(appLocalized("Close")) {
                        onClose()
                    }
                    .buttonStyle(.glass)
                    .disabled(viewModel.isBusy)
                }
            }
        }
        .onChange(of: selectedItem) { _, item in
            viewModel.processSelectedItem(item, onCandidates: onCandidates)
        }
        .onChange(of: viewModel.errorMessage) { _, message in
            guard let message else {
                return
            }

            bannerPresenter.show(message, style: .error)
            viewModel.errorMessage = nil
        }
    }
}
