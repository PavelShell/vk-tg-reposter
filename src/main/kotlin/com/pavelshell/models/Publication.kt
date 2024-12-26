package com.pavelshell.models

/**
 * Represents a publication that may contain arbitrary [text] and a list of [attachments].
 */
data class Publication(
    val text: String?,
    val attachments: List<Attachment> = listOf()
) {

    init {
        if (attachments.isEmpty() && text.isNullOrBlank()) {
            throw IllegalArgumentException("Publication can't be empty")
        }

        if (attachments.size > 10) {
            throw IllegalArgumentException("Publication can't contain more than 10 attachments")
        }
    }
}
