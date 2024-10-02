package com.pavelshell.models

import java.io.File

sealed class Attachment {
    data class Photo(val url: String) : Attachment()
    data class Video(val file: File) : Attachment()
}
