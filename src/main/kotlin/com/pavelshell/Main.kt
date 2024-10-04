package com.pavelshell

import kotlin.system.exitProcess

fun main() {
    val vkAppId = System.getenv("VK_APP_ID")?.toInt() ?: throw IllegalArgumentException("VK_APP_ID is not set")

    val vkAccessToken = System.getenv("VK_SERVICE_ACCESS_TOKEN")
        ?: throw IllegalArgumentException("VK_SERVICE_ACCESS_TOKEN is not set")

    val tgToken = System.getenv("TG_BOT_TOKEN") ?: throw IllegalArgumentException("TG_BOT_TOKEN is not set")

    val (vkGroup, tgChannel) = (System.getenv("VK_GROUP_TO_TG_CHANNEL")
        ?: throw IllegalArgumentException("VK_GROUP_TO_TG_CHANNEL is not set"))
        .split(" ")
        .also {
            if (it.size != 2) {
                throw IllegalArgumentException("VK_GROUP_TO_TG_CHANNEL should be in format 'vk_group_id tg_channel_id'")
            }
        }

    VkTgReposter(vkAppId, vkAccessToken, tgToken).duplicatePostsFromVkGroup(listOf(vkGroup to tgChannel))
    exitProcess(0)
}
