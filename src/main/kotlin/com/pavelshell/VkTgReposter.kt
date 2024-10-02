package com.pavelshell

import com.jfposton.ytdlp.YtDlp
import com.jfposton.ytdlp.YtDlpRequest
import com.pavelshell.entites.TgApi
import com.pavelshell.entites.VkApi
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import com.vk.api.sdk.objects.wall.PostType
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class VkTgReposter(vkAppId: Int, vkAccessToken: String, tgToken: String) {

    private val vkApi = VkApi(vkAppId, vkAccessToken)

    private val tgBot = TgApi(tgToken)

    fun duplicatePostsFromVkGroup(vkGroupsToTgChannels: List<Pair<String, String>>) =
        vkGroupsToTgChannels.forEach { duplicatePostsFromVkGroup(it.first, it.second) }

    private fun duplicatePostsFromVkGroup(vkGroupDomain: String, tgChannelId: String) {
        val fromDate = Instant.ofEpochSecond(LocalDateTime.of(2024, 10, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC))
        vkApi.getWallPostsFrom(fromDate, vkGroupDomain)
            .asSequence()
            .mapNotNull { it.toPublicationOrNullIfNotSupported() }
            .forEach { tgBot.publish(tgChannelId, it) }
    }

    private fun WallItem.toPublicationOrNullIfNotSupported(): Publication? {
        if (type != PostType.POST || !attachments.all { supportedAttachments.contains(it.type) }) return null
        val domainAttachments = attachments.map { it.toDomainAttachmentOrNullIfNotSupported() ?: return null }
        return Publication(text, domainAttachments)
    }

    private fun WallpostAttachment.toDomainAttachmentOrNullIfNotSupported(): Attachment? {
        return when (type) {
            WallpostAttachmentType.PHOTO -> Attachment.Photo(vkApi.getPhotoUrl(photo).toString())
            WallpostAttachmentType.VIDEO -> Attachment.Video(
                vkApi.downloadVideo(video, TgApi.MAX_FILE_SIZE_MB) ?: return null
            )
            else -> throw IllegalArgumentException("Attachment type $type is not supported.")
        }
    }

    private companion object {
        private val supportedAttachments = listOf(
            WallpostAttachmentType.PHOTO,
            WallpostAttachmentType.VIDEO
        )
    }
}
