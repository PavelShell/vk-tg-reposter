package com.pavelshell.logic

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaPhoto
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaVideo
import com.github.kotlintelegrambot.entities.inputmedia.MediaGroup
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.pavelshell.models.Attachment
import retrofit2.Response
import java.io.File

/**
 * Adapter class for telegram bot API.
 * Used to externalize Telegram API implementation details from the application logic.
 */
class TelegramBotAdapter(token: String) {

    private val bot = bot {
        this.token = token
        timeout = 45
    }

    fun deleteMessage(chatId: Long, messageId: Long) {
        bot.deleteMessage(ChatId.fromId(chatId), messageId)
    }

    fun sendPhoto(chatId: Long, url: String, caption: String? = null): Long =
        bot.sendPhoto(ChatId.fromId(chatId), TelegramFile.ByUrl(url), caption).also(::throwIfError).messageId

    fun sendPhoto(chatId: Long, data: ByteArray, caption: String? = null): Long =
        bot.sendPhoto(ChatId.fromId(chatId), TelegramFile.ByByteArray(data), caption).also(::throwIfError).messageId

    fun sendVideo(chatId: Long, file: File, duration: Int? = null, caption: String? = null): Long =
        bot.sendVideo(ChatId.fromId(chatId), TelegramFile.ByFile(file), duration, caption = caption)
            .also(::throwIfError)
            .messageId

    fun sendVideo(chatId: Long, data: ByteArray, duration: Int? = null, caption: String? = null): Long =
        bot.sendVideo(ChatId.fromId(chatId), TelegramFile.ByByteArray(data), duration, caption = caption)
            .also(::throwIfError)
            .messageId

    fun sendMediaGroup(chatId: Long, photosAndVideos: List<Attachment>, text: String?): List<Long> {
        val mediaGroup = photosAndVideos.mapIndexed { index, it ->
            val caption = if (index == 0) text else null
            when (it) {
                is Attachment.Photo -> InputMediaPhoto(TelegramFile.ByUrl(it.url), caption)
                is Attachment.Video -> InputMediaVideo(TelegramFile.ByFile(it.data), caption, duration = it.duration)
                else -> throw IllegalArgumentException("Unsupported attachment type: ${it.javaClass.name}")
            }
        }.let { MediaGroup.from(*it.toTypedArray()) }
        return bot.sendMediaGroup(ChatId.fromId(chatId), mediaGroup)
            .also(::throwIfError)
            .get()
            .map { it.messageId }
    }

    fun sendAnimation(chatId: Long, url: String): Long =
        bot.sendAnimation(ChatId.fromId(chatId), TelegramFile.ByUrl(url)).also(::throwIfError).messageId

    fun sendAudio(chatId: Long, data: ByteArray, duration: Int, artist: String, title: String): Long =
        bot.sendAudio(ChatId.fromId(chatId), TelegramFile.ByByteArray(data), duration, artist, title)
            .also(::throwIfError)
            .messageId

    private fun throwIfError(callResult: TelegramBotResult<Any>) = callResult.onError {
        throw when (it) {
            is TelegramBotResult.Error.Unknown -> TelegramApiException(cause = it.exception)
            else -> {
                // This error happens sometimes and can persist for more than several hours.
                // I assume it caused by TG servers misbehavior.
                val isTgUnableToAccessResource = it.toString().contains("WEBPAGE_MEDIA_EMPTY")
                if (isTgUnableToAccessResource) {
                    ResourceUnavailableException(it.toString())
                } else {
                    TelegramApiException(it.toString())
                }
            }
        }
    }

    fun sendText(chatId: Long, text: String): Long =
        bot.sendMessage(ChatId.fromId(chatId), text).also(::throwIfError).get().messageId

    private fun throwIfError(callResult: Pair<Response<out Any?>?, Exception?>) {
        callResult.first?.errorBody()?.string()?.let { throw TelegramApiException(it) }
        callResult.second?.let { throw TelegramApiException(cause = it) }
    }

    private val Pair<Response<com.github.kotlintelegrambot.network.Response<Message>?>?, Exception?>.messageId: Long
        get() {
            return this.first?.body()?.result?.messageId ?: throw IllegalStateException("Body is empty in $this")
        }

    /**
     * Exception that is thrown when request to Telegram API is failed.
     */
    open class TelegramApiException(msg: String? = null, cause: Throwable? = null) : Exception(msg, cause)

    class ResourceUnavailableException(msg: String? = null, cause: Throwable? = null) : TelegramApiException(msg, cause)
}
