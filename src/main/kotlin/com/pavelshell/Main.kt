package com.pavelshell

import com.github.kotlintelegrambot.bot
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

fun main() {
    VkTgReposter(System.getenv("VK_APP_ID").toInt(), System.getenv("VK_SERVICE_ACCESS_TOKEN"), System.getenv("TG_BOT_TOKEN"))
        .duplicatePostsFromVkGroup(listOf("club221726964" to "@AnimeSagesSociety"))
}

