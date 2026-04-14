package tech.justdev.domain.group.entity

import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant

data class Member(
    val email: MemberEmail,
    val createdAt: Instant,
)
