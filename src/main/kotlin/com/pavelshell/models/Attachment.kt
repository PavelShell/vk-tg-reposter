package com.pavelshell.models

import java.io.File

/**
 * [Publication] attachment like photo, video, or audio.
 */
sealed class Attachment {

    data class Video(val data: File, val duration: Int) : Attachment()

    class Photo(val url: String, val data: ByteArray, val vkId: Int) : Attachment() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Photo

            if (url != other.url) return false
            if (!data.contentEquals(other.data)) return false
            if (vkId != other.vkId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = url.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + vkId
            return result
        }

        override fun toString(): String {
            return "Photo(url='$url', data=[...], vkId=$vkId)"
        }
    }

    class Gif(val data: ByteArray, val url: String, val vkId: Int) : Attachment() {

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

    class Audio(val data: ByteArray, val artist: String, val title: String, val duration: Int) : Attachment() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Audio

            if (!data.contentEquals(other.data)) return false
            if (artist != other.artist) return false
            if (title != other.title) return false
            if (duration != other.duration) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + artist.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + duration
            return result
        }

        override fun toString(): String {
            return "Audio(data=[...], artist=$artist, title=$title, duration=$duration)"
        }
    }
}
