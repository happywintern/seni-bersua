package com.durrr.first

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object EnvConfig {
    private val dotenvValues: Map<String, String> by lazy { loadDotenvValues() }

    fun get(key: String, defaultValue: String? = null): String? {
        val systemValue = System.getenv(key)?.trim().orEmpty()
        if (systemValue.isNotBlank()) return systemValue
        val systemPropertyValue = System.getProperty(key)?.trim().orEmpty()
        if (systemPropertyValue.isNotBlank()) return systemPropertyValue
        val dotenvValue = dotenvValues[key]?.trim().orEmpty()
        if (dotenvValue.isNotBlank()) return dotenvValue
        return defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return get(key)?.toIntOrNull() ?: defaultValue
    }

    private fun loadDotenvValues(): Map<String, String> {
        val dotenvPath = findDotenvPath() ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        Files.readAllLines(dotenvPath).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val withoutExport = line.removePrefix("export ").trim()
            val separator = withoutExport.indexOf('=')
            if (separator <= 0) return@forEach
            val key = withoutExport.substring(0, separator).trim()
            if (key.isBlank()) return@forEach
            val valueRaw = withoutExport.substring(separator + 1).trim()
            val value = valueRaw
                .removeSurrounding("\"")
                .removeSurrounding("'")
            result[key] = value
        }
        return result
    }

    private fun findDotenvPath(): Path? {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOf(
            cwd.resolve(".env"),
            cwd.resolve("../.env").normalize(),
            cwd.resolve("../../.env").normalize(),
        )
        return candidates.firstOrNull { Files.exists(it) && Files.isRegularFile(it) }
    }
}
