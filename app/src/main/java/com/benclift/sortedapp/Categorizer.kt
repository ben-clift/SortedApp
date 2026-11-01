package com.benclift.sortedapp

import android.content.pm.ApplicationInfo

/** Category names, in the order they appear in the sidebar. */
object Categories {
    const val ALL = "All"
    const val OTHER = "Other"

    val order = listOf(
        "Games", "Betting", "Social", "Communication", "Music & Audio", "Video", "Photos",
        "News", "Maps & Travel", "AI", "Finance", "Shopping", "Food & Drink",
        "Sport", "Health", "Work", "Productivity", "Tools", OTHER
    )
}

/**
 * Decides which category an app belongs to.
 *
 * Order of precedence:
 *  1. Your own manual choice (long-press an app to move it)
 *  2. "Priority" keyword rules (Betting, Health): these beat everything below, because
 *     developers often tag betting apps as Games/Sports and health apps as Productivity
 *  3. The category the app developer declared to Android, except "Productivity", which
 *     is so over-used by developers that it only counts if no keyword rule matches
 *  4. Keyword rules below, matched against the app name + package name
 *  5. "Other"
 *
 * To fix a miscategorised app for everyone, add a keyword to the right list in [rules].
 * Rules are checked top to bottom and the first match wins, so put specific ones first.
 */
object Categorizer {

    /** Categories whose keyword rules win even over the developer's own category tag. */
    private val priorityCategories = setOf("Betting", "Health")

    /** Words stripped before matching, so they can't trigger a rule by accident. */
    private val ignoredWords = listOf("passport")

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Betting" to listOf(
            "betfair", "boylesports", "paddypower", "paddy power", "skybet", "sky bet",
            "bet365", "williamhill", "william hill", "ladbrokes", "coral", "betfred",
            "betway", "betvictor", "unibet", "bwin", "888", "kwiff", "fanduel",
            "draftkings", "geesports", "sportsbook", "betting", "betslip", "oddschecker",
            "bookmaker", "casino", "poker", "bingo", "tipster", "livescorebet", "spreadex",
            "quinnbet", "talksportbet", "virginbet", "betmgm", "sportingbet", "betano",
            "10bet", "mrq", "grosvenor"
        ),
        "Health" to listOf(
            "health", "covid", "nhs", "fitness", "fitbit", "sleep", "meditat", "calm",
            "headspace", "pharmacy", "doctor", "gym", "zoe", "vaccin"
        ),
        "AI" to listOf(
            "chatgpt", "openai", "claude", "anthropic", "gemini", "copilot", "chaton",
            "perplexity", "grok", "deepseek", "mistral"
        ),
        "Finance" to listOf(
            "bank", "pay", "money", "credit", "coinbase", "crypto", "binance", "invest",
            "wallet", "revolut", "monzo", "starling", "barclays", "lloyds", "natwest",
            "hsbc", "santander", "halifax", "clearpay", "klarna", "clearscore",
            "finance", "trading212", "experian"
        ),
        "Food & Drink" to listOf(
            "costa", "starbucks", "mcdonald", "deliveroo", "ubereats", "just eat", "justeat",
            "greggs", "domino", "pizza", "kfc", "nando", "burger", "coffee", "recipe",
            "restaurant", "opentable"
        ),
        "Shopping" to listOf(
            "amazon", "ebay", "tesco", "sainsbury", "asda", "waitrose", "morrisons", "aldi",
            "lidl", "argos", "shop", "vinted", "depop", "etsy", "temu", "shein", "asos",
            "currys", "ikea", "jdsports", "jd sports"
        ),
        "Sport" to listOf(
            "football", "soccer", "cricket", "nfl", "nba", "premier", "palace", "chargers",
            "fifa", "sofascore", "fotmob", "espn", "sport", "golf", "tennis", "rugby",
            "strava", "formula1", "flashscore", "ecb", "utilita", "supporters"
        ),
        "Maps & Travel" to listOf(
            "uber", "bolt", "trainline", "nationalrail", "tfl", "citymapper", "booking",
            "airbnb", "expedia", "skyscanner", "kiwi", "easyjet", "ryanair", "flight",
            "hotel", "travel", "airline"
        ),
        "Games" to listOf("chess", "wordle", "puzzle", "solitaire", "sudoku", "game"),
        "Video" to listOf(
            "cineworld", "netflix", "iplayer", "primevideo", "disney", "youtube", "itvx",
            "nowtv", "skygo", "cinema", "plex"
        ),
        "Music & Audio" to listOf(
            "podcast", "spotify", "audible", "sounds", "radio", "shazam", "music", "djay",
            "soundcloud"
        ),
        "Communication" to listOf(
            "whatsapp", "telegram", "signal", "messages", "messenger", "dialer", "phone",
            "contacts", "gmail", "mail", "sms", "discord"
        ),
        "Work" to listOf(
            "companyportal", "company portal", "outlook", "teams", "slack", "zoom", "office",
            "sharepoint", "jira", "confluence", "citrix", "workday", "codespaces", "github",
            "com.microsoft."
        ),
        "Photos" to listOf("camera", "gallery", "photos", "photo"),
        "Tools" to listOf(
            "calculator", "clock", "calendar", "files", "settings", "keyboard", "launcher",
            "vpn", "scanner", "wallpaper", "torch", "flashlight", "compass", "weather",
            "backup", "password", "authenticator", "security", "update"
        )
    )

    /** Maps Android's built-in app category tag to one of our category names. */
    private fun fromSystemCategory(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps & Travel"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Tools"
        // Productivity is deliberately NOT mapped here: it is handled as a last resort below.
        else -> null // CATEGORY_UNDEFINED: the developer didn't say, use keywords
    }

    private fun matchRules(haystack: String, onlyPriority: Boolean): String? {
        for ((category, keywords) in rules) {
            if (onlyPriority && category !in priorityCategories) continue
            if (keywords.any { haystack.contains(it) }) return category
        }
        return null
    }

    fun categorize(app: AppItem, override: String?): String {
        if (override != null) return override

        var haystack = (app.label + " " + app.pkg).lowercase()
        ignoredWords.forEach { haystack = haystack.replace(it, "") }

        // 2. Betting and Health beat the developer's own tag.
        matchRules(haystack, onlyPriority = true)?.let { return it }

        // 3. The developer's declared category (except the over-used Productivity tag).
        fromSystemCategory(app.systemCategory)?.let { return it }

        // 4. Keyword rules.
        matchRules(haystack, onlyPriority = false)?.let { return it }

        // 5. Last resort: a Productivity tag is better than nothing.
        if (app.systemCategory == ApplicationInfo.CATEGORY_PRODUCTIVITY) return "Productivity"

        return Categories.OTHER
    }
}
