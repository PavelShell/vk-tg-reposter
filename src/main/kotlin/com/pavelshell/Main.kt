package com.pavelshell

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.ServiceActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.LogLevel
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import org.slf4j.LoggerFactory

suspend fun main() {
    KSLog.default = KSLog { level: LogLevel, tag: String?, message: Any, throwable: Throwable? ->
        val logger = LoggerFactory.getLogger("main")
        val text = (if (!tag.isNullOrEmpty()) "[$tag] " else "") + message.toString()
        when (level) {
            LogLevel.ERROR, LogLevel.ASSERT -> logger.error(text, throwable)
            LogLevel.WARNING -> logger.warn(text, throwable)
            LogLevel.INFO -> logger.info(text, throwable)
            LogLevel.DEBUG, LogLevel.VERBOSE -> logger.debug(text, throwable)
            LogLevel.TRACE -> logger.trace(text, throwable)
        }
    }


    val vkApi = VkApiClient(HttpTransportClient())
    val service = ServiceActor(
        System.getenv("VK_APP_ID").toInt(),
        System.getenv("VK_SERVICE_ACCESS_TOKEN")
    )
//    val response = vkApi.wall().get(service)
//        .domain("club221726964")
//        .filter(GetFilter.ALL)
//        .count(1)
//        .execute()
    val response = vkApi.wall().getById(service, "-221726964_2112").execute()
    val photoAttachment = response.items.first().attachments.first().photo

    println(response)

    val bot = telegramBot(System.getenv("TG_BOT_TOKEN"))

    bot.buildBehaviourWithLongPolling {
        onCommand("start") {
            sendMessage(
                it.chat.id,
                "This bot is specialized tool that is not intended to be used by anyone but it's developer."
            )
        }
    }.join()
}

//private fun extractAttachments(wallItem: WallItem): List<Pair<ByteArray, AttachmentType>> {
//    wallItem.attachments.map {
//        when (wallItem.type) {
//
//        }
//    }
//
//}

//private fun extractAttachment(wallPostAttachment: WallpostAttachment): Attachment {
//    if (wallpostAttachment.type !== WallpostAttachmentType.PHOTO) {
//        throw IllegalArgumentException("Attachment type is unsupported")
//    }
//
//}
