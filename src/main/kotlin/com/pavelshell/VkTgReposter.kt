package com.pavelshell

import com.pavelshell.entites.TgApi
import com.pavelshell.entites.VkApi
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class VkTgReposter(vkAppId: Int, vkAccessToken: String, tgToken: String) {

    private val vkApi = VkApi(vkAppId, vkAccessToken)

    private val tgBot = TgApi(tgToken)

    private val logger = LoggerFactory.getLogger(VkTgReposter::class.java)

    fun duplicatePostsFromVkGroup(vkGroupsToTgChannels: List<Pair<String, String>>) =
        vkGroupsToTgChannels.forEach { duplicatePostsFromVkGroup(it.first, it.second) }

    private fun duplicatePostsFromVkGroup(vkGroupDomain: String, tgChannelId: String) {
        val fromDate = Instant.ofEpochSecond(LocalDateTime.of(2024, 9, 30, 0, 0, 0).toEpochSecond(ZoneOffset.UTC))
        vkApi.getWallPostsFrom(fromDate, vkGroupDomain)
            .asSequence()
            .mapNotNull { it.toPublicationOrNullIfNotSupported() }
            .forEach { tgBot.publish(tgChannelId, it) }
    }

    private fun WallItem.toPublicationOrNullIfNotSupported(): Publication? =
        Publication(text, attachments.map { it.toDomainAttachmentOrNullIfNotSupported() ?: return null })

    private fun WallpostAttachment.toDomainAttachmentOrNullIfNotSupported(): Attachment? {
        return if (WallpostAttachmentType.PHOTO == type) {
            Attachment.Photo(vkApi.getPhotoUrl(photo).toString())
        } else if (WallpostAttachmentType.VIDEO == type) {
            Attachment.Video(vkApi.tryDownloadVideo(video, TgApi.MAX_FILE_SIZE_MB) ?: return null)
        } else if (WallpostAttachmentType.DOC == type && GIF_DOCUMENT_CODE == doc.type) {
            val (url, bytes) = vkApi.tryDownloadDocument(doc, TgApi.MAX_FILE_SIZE_MB) ?: return null
            Attachment.Gif(bytes, url.toString(), doc.id)
        } else if (WallpostAttachmentType.AUDIO == type) {
            Attachment.Audio(audio.url.toString(), audio.artist, audio.title, audio.duration)
        } else {
            logger.debug("Can't convert unsupported attachment: {}.", this)
            null
        }
    }

    companion object {

        private const val GIF_DOCUMENT_CODE = 3
    }
}
