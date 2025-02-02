package com.pavelshell

import com.pavelshell.logic.FileBasedStorage
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private const val LOCK_KEY = "lock"

private val logger = LoggerFactory.getLogger("Main")

// todo: уменьшить количество логов?
// todo: настроить лог ТГ
fun main() {
    if (!acquireLock()) {
        logger.warn("Unable to acquire lock, exiting")
        return
    }
    try {
        run()
    } finally {
        releaseLock()
    }
}

private fun acquireLock(): Boolean = if (!FileBasedStorage.get(LOCK_KEY).toBoolean()) {
    FileBasedStorage.set(LOCK_KEY, true.toString())
    true
} else {
    false
}

private fun releaseLock() = FileBasedStorage.set(LOCK_KEY, false.toString())

private fun run() {
    val envVariables = System.getenv()
    val vkAppId = envVariables["VK_APP_ID"]?.toInt() ?: throw IllegalArgumentException("VK_APP_ID is not set")

    val vkAccessToken = envVariables["VK_SERVICE_ACCESS_TOKEN"]
        ?: throw IllegalArgumentException("VK_SERVICE_ACCESS_TOKEN is not set")

    val tgToken = envVariables["TG_BOT_TOKEN"] ?: throw IllegalArgumentException("TG_BOT_TOKEN is not set")

    val channelsToGroups = (envVariables["VK_GROUP_TO_TG_CHANNEL"]
        ?: throw IllegalArgumentException("VK_GROUP_TO_TG_CHANNEL is not set"))
        .split(", ")
        .map {
            val vkIdToTgId = it.split(" ")
            if (vkIdToTgId.size != 2) {
                throw IllegalArgumentException("VK_GROUP_TO_TG_CHANNEL should be formatted properly")
            }
            vkIdToTgId[0] to (vkIdToTgId[1].toLongOrNull()
                ?: throw IllegalArgumentException("TG channel ID ${vkIdToTgId[1]} is not a proper long number"))
        }
    VkTgReposter(vkAppId, vkAccessToken, tgToken).duplicatePostsFromVkGroup(channelsToGroups)
}
