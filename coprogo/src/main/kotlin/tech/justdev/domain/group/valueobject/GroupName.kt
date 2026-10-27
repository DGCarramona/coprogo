package tech.justdev.domain.group.valueobject

@JvmInline
value class GroupName private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        fun of(raw: String): GroupName =
            raw
                .trim()
                .also { name ->
                    require(name.isNotEmpty()) { "Le nom du groupe ne peut pas être vide." }
                    require(name.length <= 100) { "Le nom du groupe ne peut pas dépasser 100 caractères." }
                }.let(::GroupName)
    }
}
