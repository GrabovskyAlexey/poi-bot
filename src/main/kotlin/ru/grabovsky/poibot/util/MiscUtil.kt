package ru.grabovsky.poibot.util



fun String?.escapeMarkdown(): String? {
    this ?: return null
    val regex = Regex("""([_*\[\]()~`>#+\-=|{}!])""")
    return this.replace(regex, """\\$1""")
}