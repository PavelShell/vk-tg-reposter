package com.pavelshell.logic

import com.pavelshell.logic.TelegramBotAdapter.ResourceUnavailableException
import com.pavelshell.logic.TelegramBotAdapter.TelegramApiException
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import org.slf4j.LoggerFactory

private typealias MessageId = Long

private typealias ChatId = Long

/**
 * Represents Telegram platform API that is based on Telegram bot API.
 * To use it you need to create your bot by @BotFather Telegram bot, and then
 * add it to your channel giving bot a permission for sending messages.
 */
class TgApiFacade {

    private val bot: TelegramBotAdapter

    /**
     * Creates API that is accessed by bot [token].
     */
    constructor(token: String) {
        bot = TelegramBotAdapter(token)
    }

    /**
     * Default constructor with all dependencies.
     */
    // created for testing
    constructor(bot: TelegramBotAdapter) {
        this.bot = bot
    }

    /**
     * Sends [publication] to the [specified][chatId] channel.
     *
     * @throws TelegramApiException
     */
    fun publish(chatId: Long, publication: Publication) {
        // TODO: Send videos with support_streaming after migration to a new library
        // TODO: Support clips
        logger.info("Publishing {}", publication)
        if (publication.attachments.isEmpty()) {
            bot.sendText(chatId, publication.text ?: throw IllegalStateException("Publication is empty"))
        } else {
            sendTextWithAttachments(chatId, publication.text, publication.attachments)
        }
    }

    private fun sendTextWithAttachments(chatId: ChatId, text: String?, attachments: List<Attachment>) {
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            val caption = if (MAX_CAPTION_SIZE >= (text?.length ?: 0)) text else null
            sentMessageIds += sendPhotoOrVideo(chatId, attachments, caption)
            if (text != null && (caption == null || sentMessageIds.isEmpty())) {
                sentMessageIds += bot.sendText(chatId, text)
            }
            sentMessageIds += sendGif(chatId, attachments)
            sentMessageIds += sendAudio(chatId, attachments)
        } catch (e: TelegramApiException) {
            sentMessageIds.forEach { bot.deleteMessage(chatId, it) }
            if (e is ResourceUnavailableException) {
                logger.error("Unable to send attachments [$attachments] with the text [$text]", e)
            } else {
                throw e
            }
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
        return bot.sendMediaGroup(chatId, photosAndVideos, caption)
    }

    private fun sendPhoto(chatId: ChatId, photo: Attachment.Photo, text: String?): MessageId {
        return try {
            bot.sendPhoto(chatId, photo.url, text)
        } catch (e: TelegramApiException) {
            logger.debug("Can't send photo with id={} by URL, will send as bytes array", photo.vkId)
            bot.sendPhoto(chatId, photo.data, text)
        }
    }

    private fun sendVideo(chatId: ChatId, video: Attachment.Video, caption: String?): MessageId =
        bot.sendVideo(chatId, video.data, video.duration, caption = caption)

    private fun sendGif(chatId: ChatId, attachments: List<Attachment>): Collection<MessageId> {
        val gifs = attachments.filterIsInstance<Attachment.Gif>()
        if (gifs.isEmpty()) {
            return emptyList()
        }
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            // TODO: send GIFs as group if gifs.size > 1
            gifs.forEach { sentMessageIds += sendGif(chatId, it) }
        } catch (e: TelegramApiException) {
            sentMessageIds.forEach { bot.deleteMessage(chatId, it) }
            throw e
        }
        return sentMessageIds
    }

    private fun sendGif(chatId: ChatId, gif: Attachment.Gif): MessageId {
        return try {
            bot.sendAnimation(chatId, gif.url)
        } catch (e: TelegramApiException) {
            logger.debug("Can't send gif with id={} as animation, will send as video", gif.vkId)
            bot.sendVideo(chatId, gif.data)
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
        bot.sendAudio(chatId, audio.data, audio.duration, audio.artist, audio.title)

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

        private val logger = LoggerFactory.getLogger(TgApiFacade::class.java)
    }
}
