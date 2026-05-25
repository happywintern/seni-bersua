package com.durrr.first.network.security

object OpaqueBearerTokenCodec {
    private const val VERSION = "v2"
    private const val LEGACY_VERSION = "v1"
    private const val PAYLOAD_SEPARATOR = "|"

    data class Claims(
        val role: String,
        val pin: String,
        val outletId: String?,
        val version: String,
    )

    fun issue(
        secret: String,
        role: String,
        pin: String,
        outletId: String = "default",
    ): String? {
        val normalizedSecret = secret.trim()
        val normalizedRole = role.trim().uppercase()
        val normalizedPin = pin.trim()
        val normalizedOutletId = normalizeOutletId(outletId)
        if (normalizedSecret.isBlank()) return null
        if (normalizedRole !in setOf("OWNER", "CASHIER")) return null
        if (!isValidPin(normalizedPin)) return null
        if (normalizedOutletId.isBlank()) return null

        val payload = listOf(
            normalizedRole,
            normalizedPin,
            normalizedOutletId,
        ).joinToString(PAYLOAD_SEPARATOR)
        val encryptedPayload = xorWithSecret(payload.encodeToByteArray(), normalizedSecret.encodeToByteArray())
        val payloadHex = encryptedPayload.toHexString()
        val signature = computeSignatureHex(VERSION, payloadHex, normalizedSecret)
        return "$VERSION.$payloadHex.$signature"
    }

    fun decode(
        secret: String,
        token: String,
    ): Claims? {
        val normalizedSecret = secret.trim()
        if (normalizedSecret.isBlank()) return null
        val parts = token.trim().split('.')
        if (parts.size != 3) return null
        val version = parts[0]
        val payloadHex = parts[1]
        val signature = parts[2]
        if (version != VERSION && version != LEGACY_VERSION) return null
        if (payloadHex.isBlank() || signature.isBlank()) return null

        val expectedSignature = computeSignatureHex(version, payloadHex, normalizedSecret)
        if (!expectedSignature.equals(signature, ignoreCase = true)) return null

        val encryptedPayload = payloadHex.hexToByteArray() ?: return null
        val payloadBytes = xorWithSecret(encryptedPayload, normalizedSecret.encodeToByteArray())
        val payload = payloadBytes.decodeToString()
        val payloadParts = payload.split(PAYLOAD_SEPARATOR)
        if (version == VERSION && payloadParts.size != 3) return null
        if (version == LEGACY_VERSION && payloadParts.size != 2) return null
        val role = payloadParts[0].trim().uppercase()
        val pin = payloadParts[1].trim()
        if (role !in setOf("OWNER", "CASHIER")) return null
        if (!isValidPin(pin)) return null
        val outletId = if (version == VERSION) {
            normalizeOutletId(payloadParts[2])
        } else {
            null
        }
        if (version == VERSION && outletId.isNullOrBlank()) return null
        return Claims(
            role = role,
            pin = pin,
            outletId = outletId,
            version = version,
        )
    }

    private fun xorWithSecret(input: ByteArray, secret: ByteArray): ByteArray {
        if (secret.isEmpty()) return input.copyOf()
        val output = ByteArray(input.size)
        var stream = 0x9E3779B9u
        input.indices.forEach { index ->
            val secretByteInt = secret[index % secret.size].toInt() and 0xFF
            stream = stream xor ((secretByteInt + index) and 0xFF).toUInt()
            stream = stream * 1664525u + 1013904223u
            val mask = ((stream shr ((index % 4) * 8)) and 0xFFu).toInt()
            val mixed = (input[index].toInt() and 0xFF) xor secretByteInt xor mask
            output[index] = mixed.toByte()
        }
        return output
    }

    private fun computeSignatureHex(version: String, payloadHex: String, secret: String): String {
        val source = "$version.$payloadHex.$secret".encodeToByteArray()
        var hash = 0xCBF29CE484222325uL
        source.forEach { byte ->
            hash = hash xor (byte.toInt() and 0xFF).toULong()
            hash *= 0x100000001B3uL
        }
        return hash.toString(16).padStart(16, '0')
    }

    private fun isValidPin(pin: String): Boolean {
        return pin.length in 4..6 && pin.all(Char::isDigit)
    }

    private fun normalizeOutletId(value: String?): String {
        return value.orEmpty()
            .trim()
            .replace(Regex("\\s+"), "-")
            .ifBlank { "default" }
            .take(64)
    }

    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        var outIndex = 0
        forEach { byte ->
            val value = byte.toInt() and 0xFF
            chars[outIndex++] = HEX_CHARS[value ushr 4]
            chars[outIndex++] = HEX_CHARS[value and 0x0F]
        }
        return chars.concatToString()
    }

    private fun String.hexToByteArray(): ByteArray? {
        if (length % 2 != 0) return null
        val out = ByteArray(length / 2)
        var index = 0
        while (index < length) {
            val high = hexCharToInt(this[index])
            val low = hexCharToInt(this[index + 1])
            if (high < 0 || low < 0) return null
            out[index / 2] = ((high shl 4) + low).toByte()
            index += 2
        }
        return out
    }

    private fun hexCharToInt(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char.code - '0'.code
            in 'a'..'f' -> char.code - 'a'.code + 10
            in 'A'..'F' -> char.code - 'A'.code + 10
            else -> -1
        }
    }

    private val HEX_CHARS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
