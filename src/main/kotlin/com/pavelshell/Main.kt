package com.pavelshell

import kotlin.system.exitProcess

fun main() {
    val envVariables = System.getenv()
    val vkAppId = envVariables["VK_APP_ID"]?.toInt() ?: throw IllegalArgumentException("VK_APP_ID is not set")

    val vkAccessToken = envVariables["VK_SERVICE_ACCESS_TOKEN"]
        ?: throw IllegalArgumentException("VK_SERVICE_ACCESS_TOKEN is not set")

    val tgToken = envVariables["TG_BOT_TOKEN"] ?: throw IllegalArgumentException("TG_BOT_TOKEN is not set")

    val channelsToGroups = (envVariables["VK_GROUP_TO_TG_CHANNEL"]
        ?: throw IllegalArgumentException("VK_GROUP_TO_TG_CHANNEL is not set"))
        .split(", ")
        .map {
            val (vkGroup, tgChannel) = it.split(" ")
            vkGroup to tgChannel
        }
    VkTgReposter(vkAppId, vkAccessToken, tgToken).duplicatePostsFromVkGroup(channelsToGroups)

    exitProcess(0)
}
