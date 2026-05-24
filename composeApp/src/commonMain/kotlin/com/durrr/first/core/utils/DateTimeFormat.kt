package com.durrr.first.core.utils

private val ISO_DATE_TIME_REGEX =
    Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[Tt\s](\d{2}):(\d{2}))?.*$""")

fun formatReadableDateTime(
    raw: String?,
    fallback: String = "-",
): String {
    if (raw.isNullOrBlank()) return fallback
    val normalized = raw.trim()
    val match = ISO_DATE_TIME_REGEX.matchEntire(normalized) ?: return normalized.take(16)
    val year = match.groupValues[1]
    val month = match.groupValues[2]
    val day = match.groupValues[3]
    val hour = match.groupValues[4]
    val minute = match.groupValues[5]
    val shortYear = year.takeLast(2)
    return if (hour.length == 2 && minute.length == 2) {
        "$day-$month-$shortYear $hour:$minute"
    } else {
        "$day-$month-$shortYear"
    }
}

fun formatReadableDate(
    raw: String?,
    fallback: String = "-",
): String {
    if (raw.isNullOrBlank()) return fallback
    val normalized = raw.trim()
    val match = ISO_DATE_TIME_REGEX.matchEntire(normalized) ?: return normalized.take(10)
    val year = match.groupValues[1]
    val month = match.groupValues[2]
    val day = match.groupValues[3]
    return "$day-$month-${year.takeLast(2)}"
}
