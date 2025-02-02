package com.pavelshell.logic

import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.exceptions.BotException
import dev.inmo.tgbotapi.extensions.api.deleteMessages
import dev.inmo.tgbotapi.extensions.api.send.media.*
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.requests.abstracts.InputFile
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import dev.inmo.tgbotapi.requests.send.media.SendVideo
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.media.TelegramMediaPhoto
import dev.inmo.tgbotapi.types.media.TelegramMediaVideo
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Exception that is thrown when request to Telegram API is failed.
 */
typealias TelegramApiException = BotException

/**
 * Represents Telegram platform API that is based on Telegram bot API.
 * To use it you need to create your bot by @BotFather Telegram bot, and then
 * add it to your channel giving bot a permission for sending messages.
 */
class TgApi {
    // todo: logging
    // todo: refactor
    // todo: add runBlocking or do something to show exact line of code
    private val bot: TelegramBot

    /**
     * Creates API that is accessed by bot [token].
     */
    constructor(token: String) {
        bot = telegramBot(token)
    }

    /**
     * Default constructor with all dependencies.
     */
    // created for testing
    constructor(bot: TelegramBot) {
        this.bot = bot
    }

    /**
     * Sends [publication] to the [specified][channelId] channel.
     *
     * @throws TelegramApiException
     */
    fun publish(channelId: Long, publication: Publication) = runBlocking {
        // TODO: Send videos with support_streaming after migration to a new library
        // TODO: Support clips
        logger.info("Publishing {}", publication)
        val chatId = ChatId(RawChatId(channelId))
        if (publication.attachments.isEmpty()) {
            sendText(chatId, publication.text ?: throw IllegalStateException("Publication is empty"))
        } else {
            sendTextWithAttachments(chatId, publication.text, publication.attachments)
        }
    }

    private suspend fun sendTextWithAttachments(chatId: ChatIdentifier, text: String?, attachments: List<Attachment>) {
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            val caption = if (MAX_CAPTION_SIZE >= (text?.length ?: 0)) text else null
            sentMessageIds += sendPhotoOrVideo(chatId, attachments, caption)
            if (text != null && (caption == null || sentMessageIds.isEmpty())) sentMessageIds += sendText(chatId, text)
            sentMessageIds += sendGif(chatId, attachments)
            sentMessageIds += sendAudio(chatId, attachments)
        } catch (e: TelegramApiException) {
            bot.deleteMessages(chatId, sentMessageIds)
            throw e
        }
    }

    private suspend fun sendPhotoOrVideo(
        chatId: ChatIdentifier,
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
        return listOf(sendMediaGroup(chatId, photosAndVideos, caption))
    }

    private suspend fun sendPhoto(chatId: ChatIdentifier, photo: Attachment.Photo, text: String?): MessageId {
        return try {
            bot.sendPhoto(chatId, InputFile.fromUrl(photo.url), text).messageId
        } catch (e: TelegramApiException) {
            logger.debug("Can't send photo with id={} by URL, will send as bytes array", photo.vkId)
            bot.sendPhoto(chatId, photo.data.asMultipartFile("photo_${photo.vkId}"), text).messageId
        }
    }

    private suspend fun sendVideo(chatId: ChatIdentifier, video: Attachment.Video, caption: String?): MessageId =
        bot.sendVideo(
            chatId,
            InputFile.fromFile(video.data),
            duration = video.duration.toLong(),
            text = caption
        ).messageId

    // RiskFeature warns about illegal combinations of media files in the group like audio + photo
    @OptIn(RiskFeature::class)
    private suspend fun sendMediaGroup(
        chatId: ChatIdentifier,
        photosAndVideos: List<Attachment>,
        text: String?
    ): MessageId {
        val mediaGroup = photosAndVideos.mapIndexed { index, it ->
            val caption = if (index == 0) text else null
            when (it) {
                is Attachment.Photo -> TelegramMediaPhoto(InputFile.fromUrl(it.url), text = caption)
                is Attachment.Video -> TelegramMediaVideo(
                    InputFile.fromFile(it.data), duration = it.duration.toLong(), text = caption
                )

                else -> throw IllegalArgumentException("Unsupported attachment type: ${it.javaClass.name}")
            }
        }
        return bot.sendMediaGroup(chatId, mediaGroup).messageId
    }

    private suspend fun sendText(chatId: ChatIdentifier, text: String): MessageId =
        bot.sendTextMessage(chatId, text).messageId

    private suspend fun sendGif(chatId: ChatIdentifier, attachments: List<Attachment>): Collection<MessageId> {
        val gifs = attachments.filterIsInstance<Attachment.Gif>()
        if (gifs.isEmpty()) {
            return emptyList()
        }
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            // TODO: send GIFs as group if gifs.size > 1
            gifs.forEach { sentMessageIds += sendGif(chatId, it) }
        } catch (e: TelegramApiException) {
            bot.deleteMessages(chatId, sentMessageIds)
            throw e
        }
        return sentMessageIds
    }

    private suspend fun sendGif(chatId: ChatIdentifier, gif: Attachment.Gif): MessageId {
        return try {
            bot.sendAnimation(chatId, InputFile.fromUrl(gif.url)).messageId
        } catch (e: TelegramApiException) {
            logger.debug("Can't send gif with id={} as animation, will send as video", gif.vkId)
            bot.sendVideo(chatId, gif.data.asMultipartFile("")).messageId
        }
    }

    private suspend fun sendAudio(chatId: ChatIdentifier, attachments: List<Attachment>): Collection<MessageId> {
        val audios = attachments.filterIsInstance<Attachment.Audio>()
        if (audios.isEmpty()) {
            return emptyList()
        }
        val sentMessageIds = mutableListOf<MessageId>()
        try {
            audios.forEach { sentMessageIds += sendAudio(chatId, it) }
        } catch (e: TelegramApiException) {
            bot.deleteMessages(chatId, sentMessageIds)
            throw e
        }
        return sentMessageIds
    }

    private suspend fun sendAudio(chatId: ChatIdentifier, audio: Attachment.Audio): MessageId =
        bot.sendAudio(
            chatId,
            audio.data.asMultipartFile(""),
            duration = audio.duration.toLong(),
            performer = audio.artist,
            title = audio.title
        ).messageId

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
