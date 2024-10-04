package com.pavelshell.entites

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaPhoto
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaVideo
import com.github.kotlintelegrambot.entities.inputmedia.MediaGroup
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import org.slf4j.LoggerFactory
import kotlin.reflect.full.memberFunctions

class TgApi(tgToken: String) {

    private val bot = bot {
        token = tgToken
    }

    private val logger = LoggerFactory.getLogger(TgApi::class.java)

    /**
     * Sends [publication] to the [specified][channelUsername] channel.
     */
    fun publish(channelId: String, publication: Publication) {
        // TODO: max 20 messages per minute.
        logger.trace("publishing {}", publication)
        if (publication.text != null && publication.text.length > MAX_MESSAGE_SIZE) {
            return
        }
        val chatId = ChatId.fromChannelUsername(channelId)
        if (publication.attachments.isEmpty()) {
            sendMessage(chatId, publication.text)
        }
        var textToSend = publication.text
        sendPhotoOrVideo(chatId, textToSend, publication.attachments).also { isSent -> if (isSent) textToSend = null }
        sendGif(chatId, textToSend, publication.attachments).also { isSent -> if (isSent) textToSend = null }
    }

    private fun sendPhotoOrVideo(chatId: ChatId, text: String?, attachments: List<Attachment>): Boolean {
        val photosAndVideos = attachments.filter { it is Attachment.Photo || it is Attachment.Video }
        if (photosAndVideos.isEmpty()) {
            return false
        }
        var textToSend = text
        if (photosAndVideos.size == 1) {
            if (text != null && text.length > MAX_CAPTION_SIZE) {
                sendMessage(chatId, text).also { textToSend = null }
            }
            when (val attachment = photosAndVideos.first()) {
                is Attachment.Photo -> sendPhoto(chatId, attachment, textToSend)
                is Attachment.Video -> sendVideo(chatId, attachment, textToSend)
                else -> throw IllegalArgumentException("Unsupported attachment type: ${attachment.javaClass.name}")
            }
        } else {
            sendMediaGroup(chatId, textToSend, attachments)
        }
        return true
    }

    private fun sendMediaGroup(chatId: ChatId, text: String?, photosAndVideos: List<Attachment>) {
        val mediaGroup = photosAndVideos.mapIndexed { index, it ->
            val caption = if (index == 0) text else null
            when (it) {
                is Attachment.Photo -> InputMediaPhoto(TelegramFile.ByUrl(it.url), caption)
                is Attachment.Video -> InputMediaVideo(TelegramFile.ByFile(it.data), caption)
                else -> throw IllegalArgumentException("Unsupported attachment type: ${it.javaClass.name}")
            }
        }.let { MediaGroup.from(*it.toTypedArray()) }
        bot.sendMediaGroup(chatId, mediaGroup).onError { throw it.toException() }
    }

    private fun sendMessage(chatId: ChatId, text: String?) {
        if (!text.isNullOrBlank()) {
            bot.sendMessage(chatId, text).onError { throw it.toException() }
        }
    }

    private fun TelegramBotResult.Error.toException() = when (this) {
        is TelegramBotResult.Error.Unknown -> TelegramApiException(cause = exception)
        else -> TelegramApiException(this.toString())
    }

    private fun sendGif(chatId: ChatId, text: String?, attachments: List<Attachment>): Boolean {
        val gifs = attachments.filterIsInstance<Attachment.Gif>()
        if (gifs.isEmpty()) {
            return false
        }
        if (text != null) {
            sendMessage(chatId, text)
        }
        gifs.forEach { sendGif(chatId, it) }
        return true
    }

    private fun sendPhoto(chatId: ChatId, photo: Attachment.Photo, text: String?) =
        handleError(bot.sendPhoto(chatId, TelegramFile.ByUrl(photo.url), text))

    private fun sendVideo(chatId: ChatId, video: Attachment.Video, text: String?) =
        handleError(bot.sendVideo(chatId, TelegramFile.ByFile(video.data), caption = text))

    private fun sendGif(chatId: ChatId, gif: Attachment.Gif) {
        try {
            handleError(bot.sendAnimation(chatId, TelegramFile.ByByteArray(gif.data)))
        } catch (e: TelegramApiException) {
            logger.debug("Can't send gif with id={} as animation, will send as video", gif.vkId)
            handleError(bot.sendVideo(chatId, TelegramFile.ByByteArray(gif.data)))
        }
    }

    private fun handleError(callResult: Pair<Any?, Exception?>) {
        val errorBody: String? = if (callResult.first == null) {
            null
        } else {
            // I know this is unwise, but another solution will be importing of the whole retrofit library.
            val errorBody = callFunctionWithName(callResult.first, "errorBody")
            callFunctionWithName(errorBody, "string")?.toString()
        }
        if (errorBody != null) {
            throw TelegramApiException(errorBody)
        }
        val exception = callResult.second
        if (exception != null) {
            throw TelegramApiException(cause = exception)
        }
    }

    private fun callFunctionWithName(instance: Any?, name: String): Any? {
        return if (instance == null) {
            null
        } else {
            instance::class.memberFunctions.find { it.name == name }?.call(instance)
        }
    }

    /**
     * Exception that is thrown when request to Telegram API is failed.
     */
    class TelegramApiException(msg: String? = null, cause: Throwable? = null) : Exception(msg, cause)

    companion object {

        const val MAX_FILE_SIZE_MB = 50

        private const val MAX_CAPTION_SIZE = 1024

        private const val MAX_MESSAGE_SIZE = 4096
    }
}

