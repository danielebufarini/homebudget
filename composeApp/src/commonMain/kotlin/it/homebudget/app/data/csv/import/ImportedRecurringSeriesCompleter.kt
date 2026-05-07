package it.homebudget.app.data.csv.import

import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES

internal abstract class ImportedRecurringSeriesCompleter<Item, ImportKey> {
    fun complete(
        itemsToInsert: List<Item>,
        existingKeys: MutableSet<ImportKey>,
        targetOccurrencesPerSeries: Int = RECURRING_MONTHLY_OCCURRENCES
    ): List<Item> {
        val completed = itemsToInsert.toMutableList()

        val recurringGroups = itemsToInsert
            .filter { item -> recurringSeriesIdOf(item) != null }
            .groupBy { item -> recurringSeriesIdOf(item)!! }

        recurringGroups.forEach { (seriesId, seriesRows) ->
            val distinctExistingDates = seriesRows
                .map { item -> dateOf(item) }
                .toMutableSet()

            if (distinctExistingDates.size >= targetOccurrencesPerSeries) {
                return@forEach
            }

            val latestImportedOccurrence = seriesRows.maxBy { item ->
                dateOf(item)
            }

            val missingOccurrences = targetOccurrencesPerSeries - distinctExistingDates.size

            val generatedFutureRows = buildRecurringMonthlyItems(
                latestImportedOccurrence = latestImportedOccurrence,
                recurringSeriesId = seriesId,
                occurrences = missingOccurrences + 1
            )
                .drop(1)
                .filter { generatedItem ->
                    distinctExistingDates.add(dateOf(generatedItem)) &&
                        existingKeys.add(importKeyOf(generatedItem))
                }

            completed += generatedFutureRows
        }

        return completed
    }

    protected abstract fun recurringSeriesIdOf(item: Item): String?

    protected abstract fun dateOf(item: Item): Long

    protected abstract fun importKeyOf(item: Item): ImportKey

    protected abstract fun buildRecurringMonthlyItems(
        latestImportedOccurrence: Item,
        recurringSeriesId: String,
        occurrences: Int
    ): List<Item>
}
