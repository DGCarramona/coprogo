package tech.justdev.domain.shared.valueobject

@JvmInline
value class MemberId(val value: String) {
    init {
        require(value.isNotBlank()) { "memberId must not be blank" }
    }

    override fun toString(): String {
        return this.value
    }
}
