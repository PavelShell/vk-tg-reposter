package com.pavelshell.entites

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaPhoto
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaVideo
import com.github.kotlintelegrambot.entities.inputmedia.MediaGroup
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import org.slf4j.LoggerFactory
import retrofit2.Response
import com.github.kotlintelegrambot.network.Response as TgApiResponse

private typealias MessageId = Long

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
    fun publish(channelId: Long, publication: Publication) {
        // TODO: Send videos with support_streaming after migration to a new library
        // TODO: Support clips
        logger.info("Publishing {}", publication)
        val chatId = ChatId.fromId(channelId)
        if (publication.attachments.isEmpty()) {
            sendText(chatId, publication.text ?: throw IllegalStateException("Publication is empty!"))
        } else {
            sendTextWithAttachments(chatId, publication.text, publication.attachments)
        }
    }

    private fun sendTextWithAttachments(chatId: ChatId, text: String?, attachments: List<Attachment>) {
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            val caption = if (text != null && text.length <= MAX_CAPTION_SIZE) text else null
            sentMessageIds += sendPhotoOrVideo(chatId, attachments, caption)
            if (text != null && caption == null) sentMessageIds += sendText(chatId, text)
            sentMessageIds += sendGif(chatId, attachments)
            sentMessageIds += sendAudio(chatId, attachments)
        } catch (e: TelegramApiException) {
            sentMessageIds.forEach { bot.deleteMessage(chatId, it) }
            throw e
        }
    }

    private fun sendPhotoOrVideo(
        chatId: ChatId,
        attachments: List<Attachment>,
        caption: String?
    ): Collection<MessageId> {
        val photosAndVideos = attachments.filter { it is Attachment.Photo || it is Attachment.Video }
        if (photosAndVideos.isEmpty()) {
            return emptyList()
        }
        if (photosAndVideos.size == 1) {
            val messageId = when (val attachment = photosAndVideos.first()) {
                is Attachment.Photo -> sendPhoto(chatId, attachment, caption)
                is Attachment.Video -> sendVideo(chatId, attachment, caption)
                else -> throw IllegalArgumentException("Unsupported attachment type: ${attachment.javaClass.name}")
            }
            return listOf(messageId)
        }
        return sendMediaGroup(chatId, photosAndVideos, caption)
    }

    private fun sendPhoto(chatId: ChatId, photo: Attachment.Photo, text: String?): MessageId {
        return try {
            bot.sendPhoto(chatId, TelegramFile.ByUrl(photo.url), text).also(::throwIfError).messageId
        } catch (e: TelegramApiException) {
            logger.debug("Can't send photo with id={} by URL, will send as bytes array", photo.vkId)
            bot.sendPhoto(chatId, TelegramFile.ByByteArray(photo.data), text).also(::throwIfError).messageId
        }
    }

    private fun sendVideo(chatId: ChatId, video: Attachment.Video, caption: String?): MessageId =
        bot.sendVideo(chatId, TelegramFile.ByFile(video.data), video.duration, caption = caption)
            .also(::throwIfError)
            .messageId

    private fun sendMediaGroup(
        chatId: ChatId,
        photosAndVideos: List<Attachment>,
        text: String?
    ): Collection<MessageId> {
        val mediaGroup = photosAndVideos.mapIndexed { index, it ->
            val caption = if (index == 0) text else null
            when (it) {
                is Attachment.Photo -> InputMediaPhoto(TelegramFile.ByUrl(it.url), caption)
                is Attachment.Video -> InputMediaVideo(TelegramFile.ByFile(it.data), caption, duration = it.duration)
                else -> throw IllegalArgumentException("Unsupported attachment type: ${it.javaClass.name}")
            }
        }.let { MediaGroup.from(*it.toTypedArray()) }
        return bot.sendMediaGroup(chatId, mediaGroup)
            .also(::throwIfError)
            .get()
            .map { it.messageId }
    }

    private fun sendText(chatId: ChatId, text: String): MessageId =
        bot.sendMessage(chatId, text).also(::throwIfError).get().messageId

    private fun throwIfError(callResult: TelegramBotResult<Any>) {
        callResult.onError {
            val exception = when (it) {
                is TelegramBotResult.Error.Unknown -> TelegramApiException(cause = it.exception)
                else -> {
                    // This error happens sometimes and can persist for more than several hours.
                    // I assume it caused by TG servers misbehavior.
                    val isTgUnableToAccessResource = it.toString().contains("WEBPAGE_MEDIA_EMPTY")
                    if (isTgUnableToAccessResource) {
                        logger.error("Unable to send message with the following error: $it}")
                        null
                    } else {
                        TelegramApiException(it.toString())
                    }
                }
            }
            exception?.let { e -> throw e }
        }
    }

    private fun sendGif(chatId: ChatId, attachments: List<Attachment>): Collection<MessageId> {
        val gifs = attachments.filterIsInstance<Attachment.Gif>()
        if (gifs.isEmpty()) {
            return emptyList()
        }
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            gifs.forEach { sentMessageIds += sendGif(chatId, it) }
        } catch (e: TelegramApiException) {
            sentMessageIds.forEach { bot.deleteMessage(chatId, it) }
            throw e
        }
        return sentMessageIds
    }

    private fun sendGif(chatId: ChatId, gif: Attachment.Gif): MessageId {
        return try {
            bot.sendAnimation(chatId, TelegramFile.ByUrl(gif.url)).also(::throwIfError).messageId
        } catch (e: TelegramApiException) {
            logger.debug("Can't send gif with id={} as animation, will send as video", gif.vkId)
            bot.sendVideo(chatId, TelegramFile.ByByteArray(gif.data)).also(::throwIfError).messageId
        }
    }

    private fun sendAudio(chatId: ChatId, attachments: List<Attachment>): Collection<MessageId> {
        val audios = attachments.filterIsInstance<Attachment.Audio>()
        if (audios.isEmpty()) {
            return emptyList()
        }
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            audios.forEach { sentMessageIds += sendAudio(chatId, it) }
        } catch (e: TelegramApiException) {
            sentMessageIds.forEach { bot.deleteMessage(chatId, it) }
            throw e
        }
        return sentMessageIds
    }

    private fun sendAudio(chatId: ChatId, audio: Attachment.Audio): MessageId =
        bot.sendAudio(chatId, TelegramFile.ByByteArray(audio.data), audio.duration, audio.artist, audio.title)
            .also(::throwIfError)
            .messageId

    private val Pair<Response<TgApiResponse<Message>?>?, Exception?>.messageId: MessageId
        get() {
            return this.first?.body()?.result?.messageId ?: throw IllegalStateException("Body is empty in $this")
        }

    private fun throwIfError(callResult: Pair<Response<out Any?>?, Exception?>) {
        callResult.first?.errorBody()?.string()?.let { throw TelegramApiException(it) }
        callResult.second?.let { throw TelegramApiException(cause = it) }
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
