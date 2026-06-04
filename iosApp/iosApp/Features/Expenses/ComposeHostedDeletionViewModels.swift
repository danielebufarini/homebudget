@preconcurrency import ComposeApp
import SwiftUI
import Observation

@MainActor
@Observable
final class ExpenseEditorDeletionViewModel {
    var pendingSeriesId: String?

    private let controller = IosEditItemDeletionController()

    func requestDelete(
        expenseId: String,
        onClose: @escaping () -> Void
    ) {
        Task { [weak self, controller] in
            guard let self,
                  let metadata = try? await controller.loadExpenseMetadata(id: expenseId) else {
                return
            }

            if let seriesId = metadata.recurringSeriesId, !seriesId.isEmpty {
                self.pendingSeriesId = seriesId
            } else {
                self.deleteExpense(expenseId: metadata.id, onClose: onClose)
            }
        }
    }

    func deleteExpense(
        expenseId: String,
        onClose: @escaping () -> Void
    ) {
        Task { [controller] in
            guard (try? await controller.deleteExpense(id: expenseId))?.isSuccess == true else {
                return
            }

            onClose()
        }
    }

    func deleteWholeSeries(onClose: @escaping () -> Void) {
        guard let pendingSeriesId else {
            return
        }

        Task { [weak self, controller, pendingSeriesId] in
            guard let self,
                  (try? await controller.deleteRecurringExpenseSeries(seriesId: pendingSeriesId))?.isSuccess == true else {
                return
            }

            self.pendingSeriesId = nil
            onClose()
        }
    }
}

@MainActor
@Observable
final class IncomeEditorDeletionViewModel {
    var pendingSeriesId: String?

    private let controller = IosEditItemDeletionController()

    func requestDelete(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        Task { [weak self, controller] in
            guard let self,
                  let metadata = try? await controller.loadIncomeMetadata(id: incomeId) else {
                return
            }

            if let seriesId = metadata.recurringSeriesId, !seriesId.isEmpty {
                self.pendingSeriesId = seriesId
            } else {
                self.deleteIncome(incomeId: metadata.id, onClose: onClose)
            }
        }
    }

    func deleteIncome(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        Task { [controller] in
            guard (try? await controller.deleteIncome(id: incomeId))?.isSuccess == true else {
                return
            }

            onClose()
        }
    }

    func deleteWholeSeries(onClose: @escaping () -> Void) {
        guard let pendingSeriesId else {
            return
        }

        Task { [weak self, controller, pendingSeriesId] in
            guard let self,
                  (try? await controller.deleteRecurringIncomeSeries(seriesId: pendingSeriesId))?.isSuccess == true else {
                return
            }

            self.pendingSeriesId = nil
            onClose()
        }
    }
}
