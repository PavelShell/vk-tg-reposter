package com.pavelshell.models

import java.io.File

/**
 * [Publication] attachment like photo, video, or audio.
 */
sealed class Attachment {

    data class Photo(val url: String) : Attachment()

    data class Video(val data: File) : Attachment()

    data class Gif(val data: ByteArray, val url: String, val vkId: Int) : Attachment() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Gif

            if (!data.contentEquals(other.data)) return false
            if (url != other.url) return false
            if (vkId != other.vkId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + url.hashCode()
            result = 31 * result + vkId
            return result
        }

        override fun toString(): String {
            return "Gif(data=[...], url='$url', vkId=$vkId)"
        }
    }
}
