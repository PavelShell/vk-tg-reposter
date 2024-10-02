package com.pavelshell.models

sealed class Attachment {
    data class Photo(val url: String) : Attachment()
}
