package it.homebudget.app.ui.screens

// Shared prompt text for Android Gemini and iOS Apple Intelligence voice-expense parsing.

fun buildVoiceExpensePromptInstructions(outputContract: String): String {
    val sharedRules = """
        You convert a spoken household budget command into one structured expense action.

        The input may be in Italian, English, or the user's current app language.

        Rules:
        - Do not explain your reasoning.
        - Treat create as the intent/action when the user is adding a new expense.
        - Treat update as the intent/action only when the user clearly refers to exactly one of the listed existing expenses.
        - Treat needClarification as the intent/action when the transcript is probably an expense command, but important information is missing or ambiguous.
        - Treat ignore as the intent/action when the transcript is not a usable household expense command.
        - Prefer create when the user is adding a new expense.
        - Prefer update only when one listed existing expense is a clear match.
        - Never invent an expense id.
        - For update, copy the matching existing expense id exactly into the platform-specific expense id field.
        - For update, set only the fields explicitly mentioned by the user.
        - For update, leave omitted fields null/nil so the app can preserve the existing value.
        - For create, amount is required. If the amount is missing, return needClarification.
        - For create, category id and category name are required. If the category is missing or cannot be matched, return needClarification.
        - The amount must use a dot decimal separator and exactly two decimals, for example "12.50".
        - The category id and category name must match one of the provided categories exactly.
        - Do not invent categories.
        - If the user gives only a category name, choose the matching category id from the provided category list.
        - The date must be in yyyy-MM-dd format.
        - Resolve relative dates such as today, yesterday, tomorrow, last Monday, and similar expressions using the provided current date.
        - For create, if no date is spoken, use the provided current date.
        - For update, if no date is spoken, leave the date null/nil.
        - The shared flag must be true only when the user clearly says the expense is shared, split, divided, or paid together with someone else.
        - The shared flag must be false only when the user clearly says the expense is not shared.
        - If sharing is not mentioned, use false for create and null/nil for update.
        - The description should be short and useful.
        - Omit unnecessary details from the description.
        - The summary should be a short user-facing summary.
        - For needClarification, the summary must briefly ask for the missing or ambiguous information.
        - For ignore, the summary must briefly say that no usable expense command was found.
        """.trimIndent()

    return buildString {
        appendLine(sharedRules)
        appendLine()
        append(outputContract)
    }
}

fun buildVoiceExpensePromptContext(
    currentDate: String,
    currentLanguageName: String,
    currentLanguageCode: String,
    transcript: String,
    categoriesText: String,
    expensesText: String
): String {
    return buildString {
        appendLine("Current date:")
        appendLine(currentDate)
        appendLine()
        appendLine("Current app language:")
        appendLine("$currentLanguageName ($currentLanguageCode)")
        appendLine()
        appendLine("Transcript:")
        appendLine(transcript)
        appendLine()
        appendLine("Valid categories:")
        appendLine(categoriesText)
        appendLine()
        appendLine("Existing expenses available for updates:")
        append(expensesText)
    }
}

fun buildAndroidVoiceExpenseOutputContract(): String {
    return """
        Android JSON output contract:
        - Return one JSON object only.
        - Do not wrap the response in markdown.
        - Do not add explanations outside the JSON.
        - Use action=create, action=update, action=needClarification, or action=ignore.
        - For update, copy the matching existing expense id exactly into targetExpenseId.
        - Use null for targetExpenseId, amount, categoryId, categoryName, description, date, shared, and summary when not applicable.
        - Return this schema:
        {"action":"create|update|needClarification|ignore","targetExpenseId":"string or null","amount":"12.50 or null","categoryId":"exact category id or null","categoryName":"exact category name or null","description":"string or null","date":"yyyy-MM-dd or null","shared":"true|false|null","summary":"string or null"}
        """.trimIndent()
}

fun buildIosVoiceExpenseOutputContract(): String {
    return """
        iOS typed-generation output contract:
        - Return exactly one VoiceExpenseInterpretation.
        - Use intent=create, intent=update, intent=needClarification, or intent=ignore.
        - For update, copy the matching existing expense id exactly into expenseId.
        - Use nil for expenseId, amount, categoryId, categoryName, description, date, and isShared when not applicable.
        - Fill summary with a short user-facing summary, clarification question, or ignored-command message.
        """.trimIndent()
}
