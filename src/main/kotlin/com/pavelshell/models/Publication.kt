package com.pavelshell.models

/**
 * Represents a publication that may contain arbitrary [text] and a list of [attachments].
 */
data class Publication(
    val text: String?,
    val attachments: List<Attachment> = listOf()
) {

    init {
        require(attachments.isNotEmpty() || !text.isNullOrBlank()) { "Publication can't be empty" }
        require(attachments.size <= 10) { "Publication attachments must have 10 attachments" }
    }
}
