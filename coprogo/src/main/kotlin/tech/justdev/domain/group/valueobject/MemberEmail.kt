package tech.justdev.domain.group.valueobject

import java.util.Locale

@JvmInline
value class MemberEmail private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        fun of(raw: String): MemberEmail {
            val normalized = raw.trim().lowercase(Locale.ROOT)

            require(normalized.isNotEmpty()) { "member email must not be blank" }
            require(normalized.none(Char::isWhitespace)) { "member email must not contain whitespace" }

            val parts = normalized.split('@')
            require(parts.size == 2) { "member email must contain a single @ separator" }
            require(parts[0].isNotEmpty()) { "member email local part must not be blank" }
            require(parts[1].isNotEmpty()) { "member email domain must not be blank" }

            return MemberEmail(normalized)
        }
    }
}
