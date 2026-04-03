package tech.justdev.domain.shared.valueobject

@JvmInline
value class GroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "groupId must not be blank" }
    }

    override fun toString(): String {
        return this.value
    }
}
