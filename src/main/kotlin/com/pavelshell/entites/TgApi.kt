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
        timeout = 45
    }

    /**
     * Sends [publication] to the [specified][channelId] channel.
     *
     * @throws TelegramApiException
     */
    fun publish(channelId: String, publication: Publication) {
        // TODO: delete publication if one of messages wasn't delivered
        // TODO: can't post if chat ID changes
        // TODO: Improve tracking of what was posted and what not
        // TODO: Move image to top of publication
        logger.debug("publishing {}", publication)
        if (publication.text != null && publication.text.length > MAX_MESSAGE_TEXT_SIZE) {
            return
        }
        val chatId = ChatId.fromChannelUsername(channelId)
        if (publication.attachments.isEmpty()) {
            sendMessage(chatId, publication.text)
        }
        var textToSend = publication.text
        sendPhotoOrVideo(chatId, textToSend, publication.attachments).also { isSent -> if (isSent) textToSend = null }
        sendGif(chatId, textToSend, publication.attachments).also { isSent -> if (isSent) textToSend = null }
        sendAudio(chatId, textToSend, publication.attachments).also { isSent -> if (isSent) textToSend = null }
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
            sendMediaGroup(chatId, textToSend, photosAndVideos)
        }
        return true
    }

    private fun sendMediaGroup(chatId: ChatId, text: String?, photosAndVideos: List<Attachment>) {
        val mediaGroup = photosAndVideos.mapIndexed { index, it ->
            val caption = if (index == 0) text else null
            when (it) {
                is Attachment.Photo -> InputMediaPhoto(TelegramFile.ByUrl(it.url), caption)
                is Attachment.Video -> InputMediaVideo(TelegramFile.ByFile(it.data), caption, duration = it.duration)
                else -> throw IllegalArgumentException("Unsupported attachment type: ${it.javaClass.name}")
            }
        }.let { MediaGroup.from(*it.toTypedArray()) }
        handleError(bot.sendMediaGroup(chatId, mediaGroup))
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

    private fun sendGif(chatId: ChatId, gif: Attachment.Gif) {
        try {
            handleError(bot.sendAnimation(chatId, TelegramFile.ByUrl(gif.url)))
        } catch (e: TelegramApiException) {
            logger.debug("Can't send gif with id={} as animation, will send as video", gif.vkId)
            handleError(bot.sendVideo(chatId, TelegramFile.ByByteArray(gif.data)))
        }
    }

    private fun sendAudio(chatId: ChatId, text: String?, attachments: List<Attachment>): Boolean {
        val audios = attachments.filterIsInstance<Attachment.Audio>()
        if (audios.isEmpty()) {
            return false
        }
        if (text != null) {
            sendMessage(chatId, text)
        }
        audios.forEach { sendAudio(chatId, it) }
        return true
    }

    private fun sendAudio(chatId: ChatId, audio: Attachment.Audio) = handleError(
        bot.sendAudio(chatId, TelegramFile.ByByteArray(audio.data), audio.duration, audio.artist, audio.title)
    )

    private fun sendMessage(chatId: ChatId, text: String?) {
        if (!text.isNullOrBlank()) {
            handleError(bot.sendMessage(chatId, text))
        }
    }

    private fun handleError(callResult: TelegramBotResult<Any>) {
        callResult.onError {
            val exception = when (it) {
                is TelegramBotResult.Error.Unknown -> TelegramApiException(cause = it.exception)
                else -> {
                    // This error happens sometimes and can persist for more than several hours.
                    // I assume it caused by TG servers misbehavior.
                    val isTgUnableToAccessResource = it.toString().contains("WEBPAGE_MEDIA_EMPTY")
                    if (isTgUnableToAccessResource) {
                        logger.warn("Unable to send message with the following error: $it}")
                        null
                    } else {
                        TelegramApiException(it.toString())
                    }
                }
            }
            exception?.let { e -> throw e }
        }
    }

    private fun sendPhoto(chatId: ChatId, photo: Attachment.Photo, text: String?) {
        try {
            handleError(bot.sendPhoto(chatId, TelegramFile.ByUrl(photo.url), text))
        } catch (e: TelegramApiException) {
            logger.debug("Can't send photo with id={} as animation, will send as video", photo.vkId)
            handleError(bot.sendPhoto(chatId, TelegramFile.ByByteArray(photo.data), text))
        }
    }

    private fun sendVideo(chatId: ChatId, video: Attachment.Video, caption: String?) =
        handleError(bot.sendVideo(chatId, TelegramFile.ByFile(video.data), video.duration, caption = caption))

    private fun handleError(callResult: Pair<Any?, Exception?>) {
        val errorBody: String? = if (callResult.first == null) {
            null
        } else {
            // I know this is unwise, but another solution will be importing of the whole Retrofit library.
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

    private fun callFunctionWithName(instance: Any?, name: String): Any? =
        if (instance == null) {
            null
        } else {
            instance::class.memberFunctions.find { it.name == name }?.call(instance)
        }

    /**
     * Exception that is thrown when request to Telegram API is failed.
     */
    class TelegramApiException(msg: String? = null, cause: Throwable? = null) : Exception(msg, cause)

    companion object {

        /**
         * Maximum size of file that can be uploaded by API.
         */
        const val MAX_FILE_SIZE_MB = 50

        /**
         * Maximum text length that can be sent in a single message.
         */
        const val MAX_MESSAGE_TEXT_SIZE = 4096

        private const val MAX_CAPTION_SIZE = 1024

        private val logger = LoggerFactory.getLogger(TgApi::class.java)
    }
}
