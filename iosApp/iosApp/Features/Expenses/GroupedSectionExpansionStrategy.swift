import Foundation

protocol SectionExpansionStrategy {
    func nextExpandedSectionIDs(
        current: Set<String>,
        known: Set<String>,
        incoming: Set<String>,
        hasLoadedInitialState: Bool
    ) -> Set<String>
}

struct NewSectionsExpansionStrategy: SectionExpansionStrategy {
    let expandsInitially: Bool

    func nextExpandedSectionIDs(
        current: Set<String>,
        known: Set<String>,
        incoming: Set<String>,
        hasLoadedInitialState: Bool
    ) -> Set<String> {
        guard hasLoadedInitialState else {
            return expandsInitially ? incoming : []
        }

        var expanded = current.intersection(incoming)
        if expandsInitially {
            expanded.formUnion(incoming.subtracting(known))
        }
        return expanded
    }
}

struct GroupedSectionExpansionState {
    private let strategy: any SectionExpansionStrategy
    private var knownSectionIDs = Set<String>()
    private var hasLoadedInitialState = false

    init(strategy: any SectionExpansionStrategy) {
        self.strategy = strategy
    }

    mutating func nextExpandedSectionIDs(
        current: Set<String>,
        sections: [GroupedExpenseSectionModel]
    ) -> Set<String> {
        let incomingSectionIDs = Set(sections.lazy.map(\.id))
        let expandedSectionIDs = strategy.nextExpandedSectionIDs(
            current: current,
            known: knownSectionIDs,
            incoming: incomingSectionIDs,
            hasLoadedInitialState: hasLoadedInitialState
        )

        knownSectionIDs = incomingSectionIDs
        hasLoadedInitialState = true
        return expandedSectionIDs
    }
}
