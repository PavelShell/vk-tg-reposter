package com.pavelshell.models

/**
 * Represents a publication that may contain arbitrary [text] and a list of [attachments].
 */
data class Publication(
    val text: String?,
    val attachments: List<Attachment> = listOf()
)
