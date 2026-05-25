package com.durrr.first.data.repo

object CatalogNameRules {
    const val MAX_LENGTH = 50

    fun normalize(value: String?): String {
        val asciiOnly = buildString {
            value.orEmpty().forEach { ch ->
                when {
                    ch.code in 32..126 -> append(ch)
                    ch.isWhitespace() -> append(' ')
                }
            }
        }
        return asciiOnly
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_LENGTH)
    }

    fun normalizeOrFallback(value: String?, fallback: String): String {
        return normalize(value).ifBlank { normalize(fallback).ifBlank { "Item" } }
    }
}
