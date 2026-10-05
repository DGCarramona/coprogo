package tech.justdev.domain.document.valueobject

import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant

data class SupportingDocumentAttachmentDeletion(
    val deletedBy: MemberEmail,
    val deletedAt: Instant,
)
