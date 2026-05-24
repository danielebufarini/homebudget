@preconcurrency import ComposeApp
import SwiftUI

struct GroupedExpenseRowModel: Identifiable {
    let id: String
    let title: String
    let subtitleText: String
    let amountText: String
    let categoryColorKey: String?
    let categoryIconKey: String?
    let recurringSeriesId: String?

    var isRecurring: Bool {
        recurringSeriesId?.isEmpty == false
    }
}

struct GroupedExpenseSectionModel: Identifiable {
    let id: String
    let title: String
    let categoryColorKey: String?
    let categoryIconKey: String?
    let totalAmountText: String
    let rows: [GroupedExpenseRowModel]
}

extension GroupedExpenseSectionModel {
    init(_ section: IosGroupedExpenseSection) {
        self.init(
            id: section.id,
            title: section.title,
            categoryColorKey: section.categoryColorKey,
            categoryIconKey: section.categoryIconKey,
            totalAmountText: section.totalAmountText,
            rows: section.rows.map(GroupedExpenseRowModel.init)
        )
    }

    init(_ section: IosIncomeSection, showsCategoryMetadata: Bool = true) {
        self.init(
            id: section.id,
            title: section.title,
            categoryColorKey: showsCategoryMetadata ? section.categoryColorKey : nil,
            categoryIconKey: showsCategoryMetadata ? section.categoryIconKey : nil,
            totalAmountText: section.totalAmountText,
            rows: section.rows.map {
                GroupedExpenseRowModel($0, showsCategoryMetadata: showsCategoryMetadata)
            }
        )
    }
}

extension GroupedExpenseRowModel {
    init(_ row: IosGroupedExpenseRow) {
        self.init(
            id: row.id,
            title: row.title,
            subtitleText: row.subtitleText,
            amountText: row.amountText,
            categoryColorKey: row.categoryColorKey,
            categoryIconKey: row.categoryIconKey,
            recurringSeriesId: row.recurringSeriesId
        )
    }

    init(_ row: IosIncomeRow, showsCategoryMetadata: Bool = true) {
        self.init(
            id: row.id,
            title: row.title,
            subtitleText: row.subtitleText,
            amountText: row.amountText,
            categoryColorKey: showsCategoryMetadata ? row.categoryColorKey : nil,
            categoryIconKey: showsCategoryMetadata ? row.categoryIconKey : nil,
            recurringSeriesId: row.recurringSeriesId
        )
    }
}

struct TransactionDeleteConfirmationDialog: View {
    let row: GroupedExpenseRowModel
    let deleteItem: (String) -> Void
    let deleteSeries: (String) -> Void
    let clearSelection: () -> Void

    var body: some View {
        if row.isRecurring {
            AppGlassRecurringDeleteConfirmationDialog(
                message: appLocalized("Choose whether to delete only this instance of \"%@\" or the whole series.", row.title),
                onDeleteInstance: {
                    deleteItem(row.id)
                    clearSelection()
                },
                onDeleteSeries: {
                    if let seriesID = row.recurringSeriesId {
                        deleteSeries(seriesID)
                    }
                    clearSelection()
                },
                onCancel: clearSelection
            )
        } else {
            AppGlassDeleteConfirmationDialog(
                message: appLocalized("\"%@\" will be permanently deleted.", row.title),
                onDelete: {
                    deleteItem(row.id)
                    clearSelection()
                },
                onCancel: clearSelection
            )
        }
    }
}

extension Set {
    mutating func toggleMembership(of element: Element) {
        if contains(element) {
            remove(element)
        } else {
            insert(element)
        }
    }
}

extension Array where Element == GroupedExpenseSectionModel {
    func row(withID rowID: String?) -> GroupedExpenseRowModel? {
        guard let rowID else {
            return nil
        }

        return lazy
            .flatMap(\.rows)
            .first { $0.id == rowID }
    }
}
