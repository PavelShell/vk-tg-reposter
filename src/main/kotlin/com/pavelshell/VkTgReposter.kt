package com.pavelshell

import com.pavelshell.entites.FileStorage
import com.pavelshell.entites.TgApi
import com.pavelshell.entites.VkApi
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import org.slf4j.LoggerFactory
import java.time.Instant

class VkTgReposter(vkAppId: Int, vkAccessToken: String, tgToken: String) {
    private val vkApi = VkApi(vkAppId, vkAccessToken)

    private val tgBot = TgApi(tgToken)

    fun duplicatePostsFromVkGroup(vkGroupsToTgChannels: List<Pair<String, String>>) =
        vkGroupsToTgChannels.forEach { duplicatePostsFromVkGroup(it.first, it.second) }

    private fun duplicatePostsFromVkGroup(vkGroupDomain: String, tgChannelId: String) {
        logger.info("Starting the job for [vkGroupDomain=$vkGroupDomain, tgChannelId=$tgChannelId]")
        var lastPublicationTimestamp: Int? = null
        try {
            vkApi.getWallPostsFrom(getTimeOfLastPublishedPost(vkGroupDomain), vkGroupDomain).forEach { wallItem ->
                logger.info("Publishing wall item $wallItem")
                wallItem.toPublicationOrNullIfNotSupported()?.let { tgBot.publish(tgChannelId, it) }
                lastPublicationTimestamp = wallItem.date
            }
        } catch (e: Exception) {
            logger.error("Job failed with the following error", e)
            FileStorage.set(vkGroupDomain, lastPublicationTimestamp.toString())
        }
    }

    private fun getTimeOfLastPublishedPost(vkGroupDomain: String): Instant {
        return FileStorage.get(vkGroupDomain)?.let { Instant.ofEpochSecond(it.toLong()) }
            ?: getTimeOfLastPublishedPostFromEnv(vkGroupDomain)
            ?: Instant.MIN
    }

    private fun getTimeOfLastPublishedPostFromEnv(vkGroupDomain: String): Instant? {
        val key = "LAST_PUBLICATION_UNIX_TIMESTAMP_$vkGroupDomain"
        return System.getenv(key)
            ?.let {
                runCatching { it.toLong() }
                    .getOrNull()
                    ?: throw IllegalArgumentException("$key environment variable $it is not a number")
            }
            ?.let { Instant.ofEpochSecond(it) }
    }

    private fun WallItem.toPublicationOrNullIfNotSupported(): Publication? {
        val attachments = attachments.map { it.toDomainAttachmentOrNullIfNotSupported() ?: return null }
        return Publication(text, attachments)
    }

    private fun WallpostAttachment.toDomainAttachmentOrNullIfNotSupported(): Attachment? {
        return if (WallpostAttachmentType.PHOTO == type) {
            Attachment.Photo(vkApi.getPhotoUrl(photo).toString())
        } else if (WallpostAttachmentType.VIDEO == type) {
            val file = vkApi.tryDownloadVideo(video.id.toLong(), video.ownerId, TgApi.MAX_FILE_SIZE_MB) ?: return null
            Attachment.Video(file, video.duration)
        } else if (WallpostAttachmentType.DOC == type && GIF_DOCUMENT_CODE == doc.type) {
            val (url, bytes) = vkApi.tryDownloadFile(doc.url, TgApi.MAX_FILE_SIZE_MB) ?: return null
            Attachment.Gif(bytes, url.toString(), doc.id)
        } else if (WallpostAttachmentType.AUDIO == type) {
            val (_, bytes) = vkApi.tryDownloadFile(audio.url, TgApi.MAX_FILE_SIZE_MB) ?: return null
            Attachment.Audio(bytes, audio.artist, audio.title, audio.duration)
        } else {
            logger.debug("Skipping conversion of unsupported attachment: {}.", this)
            null
        }
    }

    companion object {

        private const val GIF_DOCUMENT_CODE = 3

        private val logger = LoggerFactory.getLogger(VkTgReposter::class.java)

    }
}
