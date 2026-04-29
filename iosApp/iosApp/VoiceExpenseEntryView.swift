import SwiftUI

struct VoiceExpenseEntrySheet: View {
    let onClose: () -> Void

    @StateObject private var viewModel = VoiceExpenseEntryViewModel()

    var body: some View {
        NavigationStack {
            Form {
                if let statusMessage = viewModel.statusMessage {
                    Section {
                        Text(statusMessage)
                            .font(.footnote)
                    }
                }

                Section {
                    Button {
                        viewModel.toggleRecording()
                    } label: {
                        HStack {
                            Image(systemName: viewModel.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                            Text(viewModel.isRecording ? "Stop Recording" : "Start Recording")
                        }
                    }
                    .disabled(viewModel.isBusy || !viewModel.canStartCapture)

                    if viewModel.isBusy {
                        ProgressView(viewModel.busyLabel)
                    }
                }

                Section("Transcript") {
                    Text(viewModel.transcript.isEmpty ? "Speak an expense command." : viewModel.transcript)
                        .foregroundStyle(viewModel.transcript.isEmpty ? .secondary : .primary)
                }

                if let draft = viewModel.draft {
                    Section("Ready to Save") {
                        Text(draft.intent == .create ? "Ready to save a new expense." : "Ready to update the matched expense.")
                        LabeledContent("Action", value: draft.actionLabel)
                        if let amountLabel = draft.amountLabel {
                            LabeledContent("Amount", value: amountLabel)
                        }
                        LabeledContent("Category", value: draft.categoryName)
                        if let dateLabel = draft.dateLabel {
                            LabeledContent("Date", value: dateLabel)
                        }
                        if let description = draft.description, !description.isEmpty {
                            LabeledContent("Description", value: description)
                        }
                        LabeledContent("Shared", value: draft.isShared ? "Yes" : "No")
                    }

                    Section {
                        Button(viewModel.commitButtonTitle) {
                            viewModel.commit {
                                onClose()
                            }
                        }
                        .disabled(!viewModel.canCommit)
                    }
                }
            }
            .navigationTitle("Voice Expense")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") {
                        onClose()
                    }
                }
            }
        }
        .onDisappear {
            viewModel.dispose()
        }
    }
}
