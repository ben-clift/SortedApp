package com.benclift.sortedapp

import android.content.pm.ApplicationInfo

/** Category names, in the order they appear in the sidebar. */
object Categories {
    const val ALL = "All"
    const val OTHER = "Other"

    val order = listOf(
        "Games", "Social", "Communication", "Music & Audio", "Video", "Photos",
        "News", "Maps & Travel", "AI", "Finance", "Shopping", "Food & Drink",
        "Sport", "Health", "Work", "Productivity", "Tools", OTHER
    )
}

/**
 * Decides which category an app belongs to.
 *
 * Order of precedence:
 *  1. Your own manual choice (long-press an app to move it)
 *  2. The category the app developer declared to Android
 *  3. Keyword rules below, matched against the app name + package name
 *  4. "Other"
 *
 * To fix a miscategorised app for everyone, add a keyword to the right list in [rules].
 * Rules are checked top to bottom and the first match wins, so put specific ones first.
 */
object Categorizer {

    private val rules: List<Pair<String, List<String>>> = listOf(
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
        "Sport" to listOf(
            "football", "soccer", "cricket", "nfl", "nba", "premier", "palace", "chargers",
            "fifa", "sofascore", "fotmob", "espn", "sport", "golf", "tennis", "rugby",
            "strava", "formula1"
        ),
        "Food & Drink" to listOf(
            "costa", "starbucks", "mcdonald", "deliveroo", "ubereats", "just eat", "justeat",
            "greggs", "domino", "pizza", "kfc", "nando", "burger", "coffee", "recipe",
            "restaurant", "opentable"
        ),
        "Shopping" to listOf(
            "amazon", "ebay", "tesco", "sainsbury", "asda", "waitrose", "morrisons", "aldi",
            "lidl", "argos", "shop", "vinted", "depop", "etsy", "temu", "shein", "asos",
            "currys", "ikea"
        ),
        "Maps & Travel" to listOf(
            "uber", "bolt", "trainline", "nationalrail", "tfl", "citymapper", "booking",
            "airbnb", "expedia", "skyscanner", "kiwi", "easyjet", "ryanair", "flight",
            "hotel", "travel", "airline"
        ),
        "Health" to listOf(
            "health", "fitness", "fitbit", "sleep", "meditat", "calm", "headspace", "nhs",
            "pharmacy", "doctor", "gym"
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
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Tools"
        else -> null // CATEGORY_UNDEFINED: the developer didn't say, use keywords
    }

    fun categorize(app: AppItem, override: String?): String {
        if (override != null) return override

        fromSystemCategory(app.systemCategory)?.let { return it }

        val haystack = (app.label + " " + app.pkg).lowercase()
        for ((category, keywords) in rules) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        return Categories.OTHER
    }
}
