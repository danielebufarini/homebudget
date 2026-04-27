import ComposeApp
import SwiftUI
import UIKit

private enum Route: Hashable {
    case categories
    case addExpense(expenseId: String?, readOnly: Bool)
    case monthlyExpenses(year: Int32, month: Int32)
    case sharedExpenses(year: Int32, month: Int32)
    case categoryExpenses(year: Int32, month: Int32, categoryName: String)
}

private struct KotlinViewControllerHost: UIViewControllerRepresentable {
    let makeViewController: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        SafeAreaContainerViewController(contentViewController: makeViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class SafeAreaContainerViewController: UIViewController {
    private let contentViewController: UIViewController

    init(contentViewController: UIViewController) {
        self.contentViewController = contentViewController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .clear

        addChild(contentViewController)
        contentViewController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentViewController.view)

        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            contentViewController.view.topAnchor.constraint(equalTo: guide.topAnchor),
            contentViewController.view.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
            contentViewController.view.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            contentViewController.view.bottomAnchor.constraint(equalTo: guide.bottomAnchor)
        ])

        contentViewController.didMove(toParent: self)
    }
}

struct ContentView: View {
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            DashboardRootView(path: $path)
                .navigationTitle("Dashboard")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Categories") {
                            path.append(Route.categories)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            path.append(Route.addExpense(expenseId: nil, readOnly: false))
                        } label: {
                            Image(systemName: "plus")
                        }
                    }
                }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .categories:
                        CategoriesRootView()
                            .navigationTitle("Categories")
                            .navigationBarTitleDisplayMode(.inline)
                    case let .addExpense(expenseId, readOnly):
                        KotlinViewControllerHost {
                            MainViewControllerKt.AddExpenseViewController(
                                expenseId: expenseId,
                                readOnly: readOnly,
                                onClose: {
                                    if !path.isEmpty {
                                        path.removeLast()
                                    }
                                }
                            )
                        }
                        .navigationTitle(addExpenseTitle(expenseId: expenseId, readOnly: readOnly))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .monthlyExpenses(year, month):
                        GroupedExpensesSectionsScreen(kind: .monthly, year: Int(year), month: Int(month)) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(monthName(month)) Expenses")
                        .navigationBarTitleDisplayMode(.inline)
                    case let .sharedExpenses(year, month):
                        GroupedExpensesSectionsScreen(kind: .shared, year: Int(year), month: Int(month)) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(monthName(month)) Shared Expenses")
                        .navigationBarTitleDisplayMode(.inline)
                    case let .categoryExpenses(year, month, categoryName):
                        GroupedExpensesSectionsScreen(
                            kind: .category(name: categoryName),
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(monthName(month)) \(categoryName)")
                        .navigationBarTitleDisplayMode(.inline)
                    }
                }
        }
    }
}

private struct DashboardRootView: View {
    @Binding var path: NavigationPath

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.DashboardContentViewController(
                onOpenCategories: {
                    path.append(Route.categories)
                },
                onOpenAddExpense: {
                    path.append(Route.addExpense(expenseId: nil, readOnly: false))
                },
                onOpenMonthlyExpenses: { year, month in
                    path.append(Route.monthlyExpenses(year: year.int32Value, month: month.int32Value))
                },
                onOpenSharedExpenses: { year, month in
                    path.append(Route.sharedExpenses(year: year.int32Value, month: month.int32Value))
                },
                onOpenExpenseDetails: { expenseId, readOnly in
                    path.append(Route.addExpense(expenseId: expenseId, readOnly: readOnly.boolValue))
                },
                onOpenCategoryExpenses: { year, month, categoryName in
                    path.append(
                        Route.categoryExpenses(
                            year: year.int32Value,
                            month: month.int32Value,
                            categoryName: categoryName
                        )
                    )
                }
            )
        }
    }
}

private struct CategoriesRootView: View {
    @State private var addCategoryRequestKey = 0

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.CategoriesContentViewController(addCategoryRequestKey: Int32(addCategoryRequestKey))
        }
        .id(addCategoryRequestKey)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    addCategoryRequestKey += 1
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
    }
}

private func addExpenseTitle(expenseId: String?, readOnly: Bool) -> String {
    if readOnly {
        return "Expense Details"
    }

    return expenseId == nil ? "Add Expense" : "Edit Expense"
}

private func monthName(_ month: Int32) -> String {
    let names = [
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    ]

    let index = Int(month) - 1
    guard names.indices.contains(index) else {
        return ""
    }

    return names[index]
}
