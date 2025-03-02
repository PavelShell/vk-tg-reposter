package com.pavelshell.logic

import com.pavelshell.logic.TelegramBotAdapter.ResourceUnavailableException
import com.pavelshell.logic.TelegramBotAdapter.TelegramApiException
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class TgApiFacadeTest {

    @MockK
    private lateinit var bot: TelegramBotAdapter

    @InjectMockKs
    private lateinit var tgApiFacade: TgApiFacade

    @Test
    fun `send plain text without attachments`() {
        val publication = Publication("some text")

        every { bot.sendText(CHANNEL_ID, publication.text!!) } returns 1L
        publish(publication)

        verify { bot.sendText(CHANNEL_ID, publication.text!!) }
    }

    @Test
    fun `send text with attachments`() {
        val publication = Publication(
            "9".repeat(MAX_CAPTION_SIZE + 1),
            listOf(
                Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 1),
                Attachment.Audio("iddqd".toByteArray(), "a", "b", 1),
                Attachment.Video(File("path"), 1),
                Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 1)
            )
        )

        every { bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), null) } returns listOf(1L)
        every { bot.sendText(CHANNEL_ID, any<String>()) } returns 1L
        every { bot.sendAnimation(CHANNEL_ID, any<String>()) } returns 1L
        every { bot.sendAudio(CHANNEL_ID, any<ByteArray>(), any<Int>(), any<String>(), any<String>()) } returns 1L
        publish(publication)

        verifySequence {
            bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), null)
            bot.sendText(CHANNEL_ID, any<String>())
            bot.sendAnimation(CHANNEL_ID, any<String>())
            bot.sendAudio(CHANNEL_ID, any<ByteArray>(), any<Int>(), any<String>(), any<String>())
        }
    }

    @Test
    fun `delete sent messages if part of the publication wasn't sent successfully`() {
        val publication = Publication(
            "9".repeat(MAX_CAPTION_SIZE + 1),
            listOf(
                Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 1),
                Attachment.Audio("iddqd".toByteArray(), "a", "b", 1),
                Attachment.Video(File("path"), 1),
                Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 1)
            )
        )

        every { bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), null) } returns listOf(1L, 2L)
        every { bot.sendText(CHANNEL_ID, any<String>()) } returns 3L
        every { bot.sendAnimation(CHANNEL_ID, any<String>()) } returns 4L
        every { bot.sendAudio(CHANNEL_ID, any<ByteArray>(), any<Int>(), any<String>(), any<String>()) }
            .throws(TelegramApiException("Bad data"))
        every { bot.deleteMessage(any(), any()) } just runs

        assertThrows<TelegramApiException> { publish(publication) }
        (1L..4L).forEach { messageId ->
            verify { bot.deleteMessage(CHANNEL_ID, messageId) }
        }
    }

    @Nested
    inner class `send photo or video` {

        @Test
        fun `send a single photo by URL`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
            val publication = Publication("some text", listOf(photoAttachment))

            every { bot.sendPhoto(CHANNEL_ID, photoAttachment.url, publication.text) } returns 1L
            publish(publication)

            verify { bot.sendPhoto(CHANNEL_ID, photoAttachment.url, publication.text) }
        }

        @Test
        fun `send a single photo by byte array`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", ByteArray(0), 0)
            val publication = Publication("some text", listOf(photoAttachment))

            every { bot.sendPhoto(CHANNEL_ID, photoAttachment.url, publication.text) }
                .throws(TelegramApiException("bad url"))
            every { bot.sendPhoto(CHANNEL_ID, photoAttachment.data, publication.text) } returns 1L
            publish(publication)

            verifySequence {
                bot.sendPhoto(CHANNEL_ID, photoAttachment.url, publication.text)
                bot.sendPhoto(CHANNEL_ID, photoAttachment.data, publication.text)
            }
        }

        @Test
        fun `delete sent photo if part of the publication wasn't sent successfully`() {
            val publication = Publication(
                "9".repeat(MAX_CAPTION_SIZE + 1),
                listOf(Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0))
            )

            val sentPhotoMessageId = 1L
            every { bot.sendPhoto(CHANNEL_ID, any<String>(), anyNullable()) } returns sentPhotoMessageId
            every { bot.sendText(CHANNEL_ID, any()) } throws TelegramApiException()
            every { bot.deleteMessage(any(), any()) } just runs
            runCatching { publish(publication) }

            verifySequence {
                bot.sendPhoto(CHANNEL_ID, any<String>(), anyNullable())
                bot.sendText(CHANNEL_ID, any())
                bot.deleteMessage(CHANNEL_ID, sentPhotoMessageId)
            }
        }

        @Test
        fun `send a single video`() {
            val videoAttachment = Attachment.Video(File("path"), 12)
            val publication = Publication("some text", listOf(videoAttachment))

            every {
                bot.sendVideo(CHANNEL_ID, videoAttachment.data, videoAttachment.duration, caption = publication.text)
            } returns 1L
            publish(publication)

            verify {
                bot.sendVideo(CHANNEL_ID, videoAttachment.data, videoAttachment.duration, caption = publication.text)
            }
        }

        @Test
        fun `delete sent video if part of the publication wasn't sent successfully`() {
            val publication = Publication(
                "9".repeat(MAX_CAPTION_SIZE + 1),
                listOf(Attachment.Video(File("path"), 0))
            )

            val sentVideoMessageId = 1L
            every { bot.sendVideo(CHANNEL_ID, any<File>(), anyNullable()) } returns sentVideoMessageId
            every { bot.sendText(CHANNEL_ID, any()) } throws TelegramApiException()
            every { bot.deleteMessage(any(), any()) } just runs
            runCatching { publish(publication) }

            verifySequence {
                bot.sendVideo(CHANNEL_ID, any<File>(), anyNullable())
                bot.sendText(CHANNEL_ID, any())
                bot.deleteMessage(CHANNEL_ID, sentVideoMessageId)
            }
        }

        @Test
        fun `send photos and videos list as media group`() {
            val publication = Publication(
                "some text",
                listOf(
                    Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0),
                    Attachment.Video(File("path"), 12)
                )
            )

            every { bot.sendMediaGroup(CHANNEL_ID, publication.attachments, publication.text) } returns listOf(1L)
            publish(publication)

            verify { bot.sendMediaGroup(CHANNEL_ID, publication.attachments, publication.text) }
        }

        @Test
        fun `delete sent media group if part of the publication wasn't sent successfully`() {
            val publication = Publication(
                "9".repeat(MAX_CAPTION_SIZE + 1),
                listOf(
                    Attachment.Video(File("path"), 0),
                    Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
                )
            )

            val messageIds = listOf(1L, 2L)
            every { bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), anyNullable<String?>()) } returns messageIds
            every { bot.sendText(CHANNEL_ID, any()) } throws TelegramApiException()
            every { bot.deleteMessage(any(), any()) } just runs
            runCatching { publish(publication) }

            verifySequence {
                bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), anyNullable<String?>())
                bot.sendText(CHANNEL_ID, any())
                messageIds.forEach { bot.deleteMessage(CHANNEL_ID, it) }
            }
        }

        @Test
        fun `send text separately if caption length limit is exceeded`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
            val publication = Publication("9".repeat(MAX_CAPTION_SIZE + 1), listOf(photoAttachment))

            every { bot.sendPhoto(CHANNEL_ID, any<String>(), null) } returns 1L
            every { bot.sendText(CHANNEL_ID, publication.text!!) } returns 1L
            publish(publication)

            verifySequence {
                bot.sendPhoto(CHANNEL_ID, any<String>(), null)
                bot.sendText(CHANNEL_ID, publication.text!!)
            }
        }
    }

    @Nested
    inner class `send a GIF file` {

        @Test
        fun `send as animation`() {
            val gifAttachment = Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 0)
            val publication = Publication("text", listOf(gifAttachment))

            every { bot.sendText(CHANNEL_ID, publication.text!!) } returns 1L
            every { bot.sendAnimation(CHANNEL_ID, gifAttachment.url) } returns 1L
            publish(publication)

            verifySequence {
                bot.sendText(CHANNEL_ID, publication.text!!)
                bot.sendAnimation(CHANNEL_ID, gifAttachment.url)
            }
        }

        @Test
        fun `send as video`() {
            val gifAttachment = Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 0)
            val publication = Publication("text", listOf(gifAttachment))

            every { bot.sendText(CHANNEL_ID, publication.text!!) } returns 1L
            every { bot.sendAnimation(CHANNEL_ID, gifAttachment.url) } throws TelegramApiException()
            every { bot.sendVideo(CHANNEL_ID, gifAttachment.data) } returns 1L
            publish(publication)

            verifySequence {
                bot.sendText(CHANNEL_ID, publication.text!!)
                bot.sendAnimation(CHANNEL_ID, gifAttachment.url)
                bot.sendVideo(CHANNEL_ID, gifAttachment.data)
            }
        }

        @Test
        fun `delete sent GIFs if one of them wasn't sent successfully`() {
            val publication = Publication(
                null,
                listOf(
                    Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 1),
                    Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 2)
                )
            )

            val sentMessageId = 1L
            every { bot.sendAnimation(CHANNEL_ID, any<String>()) }
                .returns(sentMessageId)
                .andThenThrows(TelegramApiException())
            every { bot.sendVideo(CHANNEL_ID, any<ByteArray>()) } throws TelegramApiException()
            runCatching { publish(publication) }

            verify(exactly = 2) { bot.sendAnimation(CHANNEL_ID, any<String>()) }
            verify { bot.sendVideo(CHANNEL_ID, any<ByteArray>()) }
            verify { bot.deleteMessage(CHANNEL_ID, sentMessageId) }
        }
    }

    @Nested
    inner class `send an audio file` {

        @Test
        fun `send an audio`() {
            val audioAttachment = Attachment.Audio("iddqd".toByteArray(), "Imagine Dragons", "Radioactive", 186)
            val publication = Publication(null, listOf(audioAttachment))

            every {
                bot.sendAudio(
                    CHANNEL_ID,
                    audioAttachment.data,
                    audioAttachment.duration,
                    audioAttachment.artist,
                    audioAttachment.title
                )
            } returns 1L
            publish(publication)

            verify {
                bot.sendAudio(
                    CHANNEL_ID,
                    audioAttachment.data,
                    audioAttachment.duration,
                    audioAttachment.artist,
                    audioAttachment.title
                )
            }
        }

        @Test
        fun `delete sent audios if one of them wasn't sent successfully`() {
            val publication = Publication(
                null,
                listOf(
                    Attachment.Audio("iddqd".toByteArray(), "a", "b", 0),
                    Attachment.Audio("iddqd".toByteArray(), "a", "b", 1)
                )
            )

            val sentMessageId = 1L
            every { bot.sendAudio(CHANNEL_ID, any<ByteArray>(), any<Int>(), any<String>(), any<String>()) }
                .returns(1L)
                .andThenThrows(TelegramApiException())
            runCatching { publish(publication) }

            verify(exactly = 2) {
                bot.sendAudio(
                    CHANNEL_ID,
                    any<ByteArray>(),
                    any<Int>(),
                    any<String>(),
                    any<String>()
                )
            }
            verify { bot.deleteMessage(CHANNEL_ID, sentMessageId) }
        }
    }

    @Nested
    inner class `error handling` {

        @Test
        fun `should ignore telegram bot error when telegram server is unable to access resource`() {
            val publication = Publication(
                null,
                listOf(Attachment.Video(File("path"), 0), Attachment.Video(File("path"), 0))
            )

            every { bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), anyNullable()) }
                .throws(ResourceUnavailableException())
            publish(publication)

            verify { bot.sendMediaGroup(CHANNEL_ID, any<List<Attachment>>(), anyNullable()) }
        }
    }

    private fun publish(publication: Publication) = tgApiFacade.publish(CHANNEL_ID, publication)

    companion object {
        private const val CHANNEL_ID = -7788L
        private const val MAX_CAPTION_SIZE = 1024
    }
}
