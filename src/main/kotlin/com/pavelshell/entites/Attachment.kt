package com.pavelshell.entites

data class Attachment(
    val data: ByteArray,
    val type: Type

) {
    enum class Type {
        PHOTO
    }
}

