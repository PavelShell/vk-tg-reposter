package com.pavelshell.entites

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaPhoto
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
        if (publication.attachments.isNotEmpty()) {
            if (publication.text.length > MAX_CAPTION_SIZE) {
                if (publication.attachments.size > 1) {
                    val photos = publication.attachments.map { InputMediaPhoto(TelegramFile.ByUrl((it as Attachment.Photo).url)) }
                    bot.sendMediaGroup(chatId, MediaGroup.from(*photos.toTypedArray()))
                } else {
                    bot.sendPhoto(chatId, TelegramFile.ByUrl((publication.attachments.first() as Attachment.Photo).url))
                }
                bot.sendMessage(chatId, publication.text)
            } else {
                if (publication.attachments.size > 1) {
                    val photos = publication.attachments.map { InputMediaPhoto(TelegramFile.ByUrl((it as Attachment.Photo).url)) }.toMutableList()
                    photos[0] = InputMediaPhoto(photos[0].media, publication.text)
                    bot.sendMediaGroup(chatId, MediaGroup.from(*photos.toTypedArray()))
                } else {
                    bot.sendPhoto(chatId, TelegramFile.ByUrl((publication.attachments.first() as Attachment.Photo).url), publication.text)
                }
            }
        }
    }

    private companion object {

        private const val MAX_CAPTION_SIZE = 1024

        private const val MAX_MESSAGE_SIZE = 4096
    }
}
