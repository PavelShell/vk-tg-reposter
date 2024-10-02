package com.pavelshell

import com.github.kotlintelegrambot.bot
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
        val fromDate = Instant.ofEpochSecond(LocalDateTime.of(2024, 9, 20, 0, 0, 0).toEpochSecond(ZoneOffset.UTC))
        vkApi.getWallPostsFrom(fromDate, vkGroupDomain)
            .mapNotNull { it.toPublicationOrNullIfNotSupported() }
            .forEach { tgBot.publish(tgChannelId, it) }
    }

    private fun WallItem.toPublicationOrNullIfNotSupported(): Publication? {
        if (type != PostType.POST || !attachments.all { supportedAttachments.contains(it.type) }) return null
        return Publication(text, attachments.map { it.toDomainAttachment() })
    }

    private fun WallpostAttachment.toDomainAttachment(): Attachment {
        return when (type) {
            WallpostAttachmentType.PHOTO -> Attachment.Photo(vkApi.getPhotoUrl(photo).toString())
//            WallpostAttachmentType.PHOTO -> A
            else -> throw IllegalArgumentException("Attachment type $type is not supported.")
        }
    }

    private companion object {
        private val supportedAttachments = listOf(
            WallpostAttachmentType.PHOTO,
//            WallpostAttachmentType.VIDEO
        )
    }
}
