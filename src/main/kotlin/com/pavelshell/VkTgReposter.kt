package com.pavelshell

import com.pavelshell.logic.FileBasedStorage
import com.pavelshell.logic.TgApi
import com.pavelshell.logic.VkApi
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import com.vk.api.sdk.objects.base.Link
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import org.slf4j.LoggerFactory
import java.time.Instant

class VkTgReposter {

    private val vkApi: VkApi

    private val tgBot: TgApi

    /**
     * Initializes the script by instantiating [VkApi] and [TgApi] with provided credentials.
     */
    constructor(vkAppId: Int, vkAccessToken: String, tgToken: String) {
        vkApi = VkApi(vkAppId, vkAccessToken)
        tgBot = TgApi(tgToken)
    }

    /**
     * Default constructor with all dependencies.
     */
    // created for testing
    constructor(vkApi: VkApi, tgBot: TgApi) {
        this.vkApi = vkApi
        this.tgBot = tgBot
    }

    fun duplicatePostsFromVkGroup(vkGroupsToTgChannels: List<Pair<String, Long>>) =
        vkGroupsToTgChannels.forEach { duplicatePostsFromVkGroup(it.first, it.second) }

    private fun duplicatePostsFromVkGroup(vkGroupDomain: String, tgChannelId: Long) {
        logger.info("Starting the job for [vkGroupDomain=$vkGroupDomain, tgChannelId=$tgChannelId]")
        var lastPublicationTimestamp: Int? = null
        try {
            vkApi.getWallPostsFrom(getTimeOfLastPublishedPost(vkGroupDomain), vkGroupDomain).forEach { wallItem ->
                logger.info("Preparing wall item {} for publication", wallItem)
                wallItem.toPublicationOrNullIfNotSupported()?.let {
                    tgBot.publish(tgChannelId, it)
                }
                lastPublicationTimestamp = wallItem.date
            }
        } catch (e: Exception) {
            if (e.message?.contains("Too Many Requests") == true) {
                logger.info("Telegram rate limit reached", e)
            } else {
                logger.error("Job failed with the following error", e)
            }
        } finally {
            lastPublicationTimestamp?.let { FileBasedStorage.set(vkGroupDomain, it.toString()) }
        }
        logger.info("Job finished successfully for [vkGroupDomain=$vkGroupDomain, tgChannelId=$tgChannelId]")
    }

    private fun getTimeOfLastPublishedPost(vkGroupDomain: String): Instant {
        return FileBasedStorage.get(vkGroupDomain)?.let { Instant.ofEpochSecond(it.toLong()) }
            ?: getTimeOfLastPublishedPostFromEnv(vkGroupDomain)
            ?: Instant.MIN
    }

    private fun getTimeOfLastPublishedPostFromEnv(vkGroupDomain: String): Instant? {
        val key = "LAST_PUBLICATION_UNIX_TIMESTAMP_$vkGroupDomain"
        return getEnv(key)
            ?.let {
                runCatching { it.toLong() }
                    .getOrNull()
                    ?: throw IllegalArgumentException("$key value $it is not a number")
            }
            ?.let { Instant.ofEpochSecond(it) }
    }

    // created for testing
    private fun getEnv(key: String): String? = System.getenv(key)

    private fun WallItem.toPublicationOrNullIfNotSupported(): Publication? {
        copyHistory.isNullOrEmpty().also { isNotRepost ->
            if (!isNotRepost) {
                logger.info("Skipping conversion of repost publication: {}.", this)
                return null
            }
        }
        val publicationText = formatPublicationText(
            text,
            attachments.find { it.type === WallpostAttachmentType.LINK }?.link
        )
        (TgApi.MAX_MESSAGE_TEXT_SIZE < publicationText.length).also { isMaxTextLengthExceeded ->
            if (isMaxTextLengthExceeded) {
                logger.info("Skipping conversion of publication due to text limit excess: {}.", this)
                return null
            }
        }
        val publicationAttachments = attachments
            .filter { it.type !== WallpostAttachmentType.LINK }
            .map { it.toDomainAttachmentOrNullIfNotSupported() ?: return null }
        return Publication(publicationText.ifBlank { null }, publicationAttachments)
    }

    private fun WallpostAttachment.toDomainAttachmentOrNullIfNotSupported(): Attachment? {
        // TODO: implement Link attachment when link_preview_option will be implemented by Telegram bot library we use
        return when {
            WallpostAttachmentType.PHOTO == type -> {
                val (url, bytes) = vkApi.tryDownloadFile(vkApi.getPhotoUrl(photo), TgApi.MAX_FILE_SIZE_MB)
                    ?: return null
                Attachment.Photo(url.toString(), bytes, photo.id)
            }

            WallpostAttachmentType.VIDEO == type -> {
                val file = vkApi.tryDownloadVideo(video.id.toLong(), video.ownerId, TgApi.MAX_FILE_SIZE_MB)
                    ?: return null
                Attachment.Video(file, video.duration)
            }

            WallpostAttachmentType.DOC == type && GIF_DOCUMENT_CODE == doc.type -> {
                val (url, bytes) = vkApi.tryDownloadFile(doc.url, TgApi.MAX_FILE_SIZE_MB)
                    ?: return null
                Attachment.Gif(bytes, url.toString(), doc.id)
            }

            WallpostAttachmentType.AUDIO == type -> {
                val (_, bytes) = vkApi.tryDownloadFile(audio.url, TgApi.MAX_FILE_SIZE_MB)
                    ?: return null
                Attachment.Audio(bytes, audio.artist, audio.title, audio.duration)
            }

            else -> {
                logger.warn("Skipping conversion of unsupported attachment: {}.", this)
                null
            }
        }
    }

    private fun formatPublicationText(text: String, attachedLink: Link?): String {
        val vkLinkTemplate = """\[(.*?)(?:\|(.*?))?]""".toRegex()
        val textWithLinksFormatted = vkLinkTemplate.replace(text) { linkToDisplayedText ->
            val link = linkToDisplayedText.groups[1]?.value.orEmpty()
            val linkText = linkToDisplayedText.groups[2]?.value.orEmpty()
            val isExternalLink = link.matches(Regex("""https?://\S+"""))
            if (isExternalLink) "$linkText ($link)" else linkText
        }
        return if (attachedLink != null && !text.contains(attachedLink.url)) {
            "$textWithLinksFormatted\n\n${attachedLink.url}"
        } else {
            textWithLinksFormatted
        }
    }

    companion object {

        private const val GIF_DOCUMENT_CODE = 3

        private val logger = LoggerFactory.getLogger(VkTgReposter::class.java)

    }
}
