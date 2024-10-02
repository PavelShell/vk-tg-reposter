package com.pavelshell.entites

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaPhoto
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaVideo
import com.github.kotlintelegrambot.entities.inputmedia.MediaGroup
import com.github.kotlintelegrambot.logging.LogLevel
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication

class TgApi(tgToken: String) {

    private val bot = bot {
        logLevel = LogLevel.Error
        token = tgToken
    }

    fun publish(channelId: String, publication: Publication) {
        val chatId = ChatId.fromChannelUsername(channelId)
        if (publication.text.length > MAX_MESSAGE_SIZE) return
        // check failure status TelegramBotResult
        // send (video) links with preview
        if (publication.attachments.isNotEmpty()) {
            if (publication.text.length > MAX_CAPTION_SIZE) {
                if (publication.attachments.size > 1) {
                    val inputMedias = publication.attachments.map {
                        when (it) {
                            is Attachment.Photo -> InputMediaPhoto(TelegramFile.ByUrl(it.url))
                            is Attachment.Video -> InputMediaVideo(TelegramFile.ByFile(it.file))
                        }
                    }
                    bot.sendMediaGroup(chatId, MediaGroup.from(*inputMedias.toTypedArray()))
                } else {
                    val attachment = publication.attachments.first()
                    when (attachment) {
                        is Attachment.Photo -> bot.sendPhoto(chatId, TelegramFile.ByUrl(attachment.url))
                        is Attachment.Video -> bot.sendVideo(chatId, TelegramFile.ByFile(attachment.file))
                    }
                }
                bot.sendMessage(chatId, publication.text)
            } else {
                if (publication.attachments.size > 1) {
                    val inputMedias = publication.attachments.mapIndexed { index, it ->
                        val caption = if (index == 0) publication.text else null
                        when (it) {
                            is Attachment.Photo -> InputMediaPhoto(TelegramFile.ByUrl(it.url), caption)
                            is Attachment.Video -> InputMediaVideo(TelegramFile.ByFile(it.file), caption)
                        }
                    }
                    bot.sendMediaGroup(chatId, MediaGroup.from(*inputMedias.toTypedArray()))
                } else {
                    val attachment = publication.attachments.first()
                    when (attachment) {
                        is Attachment.Photo -> bot.sendPhoto(chatId, TelegramFile.ByUrl(attachment.url), publication.text)
                        is Attachment.Video -> bot.sendVideo(chatId, TelegramFile.ByFile(attachment.file), caption = publication.text)
                    }
                }
            }
        } else if (publication.text.isEmpty()) {
            bot.sendMessage(chatId, publication.text)
        }
    }

    companion object {

        private const val MAX_CAPTION_SIZE = 1024

        private const val MAX_MESSAGE_SIZE = 4096

        const val MAX_FILE_SIZE_MB = 50
    }
}
