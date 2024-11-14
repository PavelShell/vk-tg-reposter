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

    fun duplicatePostsFromVkGroup(vkGroupsToTgChannels: List<Pair<String, Long>>) =
        vkGroupsToTgChannels.forEach { duplicatePostsFromVkGroup(it.first, it.second) }

    private fun duplicatePostsFromVkGroup(vkGroupDomain: String, tgChannelId: Long) {
        logger.info("Starting the job for [vkGroupDomain=$vkGroupDomain, tgChannelId=$tgChannelId]")
        var lastPublicationTimestamp: Int? = null
        try {
            vkApi.getWallPostsFrom(getTimeOfLastPublishedPost(vkGroupDomain), vkGroupDomain).forEach { wallItem ->
                logger.info("Preparing wall item {} for publication", wallItem)
                wallItem.toPublicationOrNullIfNotSupported()?.let { tgBot.publish(tgChannelId, it) }
                lastPublicationTimestamp = wallItem.date
            }
        } catch (e: Exception) {
            logger.error("Job failed with the following error", e)
        } finally {
            lastPublicationTimestamp?.let { FileStorage.set(vkGroupDomain, it.toString()) }
        }
        logger.info("Job finished successfully for [vkGroupDomain=$vkGroupDomain, tgChannelId=$tgChannelId]")
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
        copyHistory.isNullOrEmpty().also { isNotRepost ->
            if (!isNotRepost) {
                logger.warn("Skipping conversion of repost publication: {}.", this)
                return null
            }
        }
        (TgApi.MAX_MESSAGE_TEXT_SIZE < text.length).also { isMaxTextLengthExceeded ->
            if (isMaxTextLengthExceeded) {
                logger.warn("Skipping conversion of publication due to text limit excess: {}.", this)
                return null
            }
        }

        val publicationAttachments = attachments
            .filter { it.type !== WallpostAttachmentType.LINK }
            .map { it.toDomainAttachmentOrNullIfNotSupported() ?: return null }

        val link = attachments.find { it.type === WallpostAttachmentType.LINK }?.link?.url
        val publicationText = if (link != null && !text.contains(link)) "$text\n$link" else text

        return Publication(publicationText, publicationAttachments)
    }

    private fun WallpostAttachment.toDomainAttachmentOrNullIfNotSupported(): Attachment? {
        // TODO: implement Link attachment when link_preview_option will be implemented by Telegram bot library we use
        val result =  if (WallpostAttachmentType.PHOTO == type) {
            val (url, bytes) = vkApi.tryDownloadFile(vkApi.getPhotoUrl(photo), TgApi.MAX_FILE_SIZE_MB) ?: return null
            Attachment.Photo(url.toString(), bytes, photo.id)
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
            null
        }
        if (result == null) {
            logger.warn("Skipping conversion of unsupported attachment: {}.", this)
        }
        return result
    }

    companion object {

        private const val GIF_DOCUMENT_CODE = 3

        private val logger = LoggerFactory.getLogger(VkTgReposter::class.java)

    }
}
