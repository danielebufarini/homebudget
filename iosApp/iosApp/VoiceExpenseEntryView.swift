import SwiftUI

// SwiftUI sheet for recording, reviewing, and committing a voice expense draft.

struct VoiceExpenseEntrySheet: View {
    let onClose: () -> Void

    @StateObject private var viewModel = VoiceExpenseEntryViewModel()
    @StateObject private var bannerPresenter = AppGlassBannerPresenter()

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                AppGlassSheetContentScrollView(spacing: 18) {
                    if let statusMessage = viewModel.statusMessage {
                        AppGlassSheetSection(
                            spacing: 0,
                            verticalPadding: 12
                        ) {
                            Text(statusMessage)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    AppGlassSheetSection(spacing: 14) {
                        Button {
                            viewModel.toggleRecording()
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: viewModel.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                                Text(viewModel.isRecording ? appLocalized("Stop Recording") : appLocalized("Start Recording"))
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .disabled(viewModel.isBusy || !viewModel.canStartCapture)
                        .buttonStyle(.glassProminent)
                    }

                    if viewModel.isBusy {
                        ProgressView(viewModel.busyLabel)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    AppGlassSheetSection(title: appLocalized("Transcript")) {
                        Text(viewModel.transcript.isEmpty ? appLocalized("Speak an expense command.") : viewModel.transcript)
                            .foregroundStyle(viewModel.transcript.isEmpty ? .secondary : .primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    if let draft = viewModel.draft {
                        AppGlassSheetSection(
                            title: appLocalized("Ready to Save"),
                            spacing: 12
                        ) {
                            Text(draft.intent == .create ? appLocalized("Ready to save a new expense.") : appLocalized("Ready to update the matched expense."))
                                .frame(maxWidth: .infinity, alignment: .leading)

                            LabeledContent(appLocalized("Action"), value: draft.actionLabel)
                            if let amountLabel = draft.amountLabel {
                                LabeledContent(appLocalized("Amount"), value: amountLabel)
                            }
                            LabeledContent(appLocalized("Category"), value: draft.categoryName)
                            if let dateLabel = draft.dateLabel {
                                LabeledContent(appLocalized("Date"), value: dateLabel)
                            }
                            if let description = draft.description, !description.isEmpty {
                                LabeledContent(appLocalized("Description"), value: description)
                            }
                            LabeledContent(appLocalized("Shared"), value: draft.isShared ? appLocalized("Yes") : appLocalized("No"))
                        }
                    }
                }

                AppGlassBannerOverlay(presenter: bannerPresenter)
            }
            .appGlassSheetChrome()
            .navigationTitle(appLocalized("Voice Expense"))
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                AppGlassSheetActionBar {
                    Button(appLocalized("Close")) {
                        onClose()
                    }
                    .buttonStyle(.glass)

                    if viewModel.draft != nil {
                        Button(viewModel.commitButtonTitle) {
                            viewModel.commit {
                                onClose()
                            }
                        }
                        .buttonStyle(.glassProminent)
                        .disabled(!viewModel.canCommit)
                    }
                }
            }
        }
        .onChange(of: viewModel.errorMessage) { _, message in
            guard let message else {
                return
            }

            bannerPresenter.show(message, style: .error)
            viewModel.errorMessage = nil
        }
        .onDisappear {
            viewModel.dispose()
        }
    }
}
