package com.pavelshell.logic

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaPhoto
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaVideo
import com.github.kotlintelegrambot.entities.inputmedia.MediaGroup
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.pavelshell.logic.TgApi.TelegramApiException
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.io.File
import com.github.kotlintelegrambot.network.Response as TgBotResponse
import retrofit2.Response as RetrofitResponse

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class TgApiTest {

    @MockK
    private lateinit var bot: Bot

    @InjectMockKs
    private lateinit var tgApi: TgApi

    @Test
    fun `send plain text without attachments`() {
        val publication = Publication("some text")

        every { bot.sendMessage(chatIdEq(), publication.text!!) } returns buildTelegramBotSuccessResult()
        publish(publication)

        verify { bot.sendMessage(chatIdEq(), publication.text!!) }
    }

    @Test
    fun `send text with attachments`() {
        val publication = Publication(
            "9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1),
            listOf(
                Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 1),
                Attachment.Audio("iddqd".toByteArray(), "a", "b", 1),
                Attachment.Video(File("path"), 1),
                Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 1)
            )
        )

        every { bot.sendMediaGroup(chatIdEq(), any<MediaGroup>()) } returns
                TelegramBotResult.Success(listOf(buildTestResponseMessage()))
        every { bot.sendMessage(chatIdEq(), any<String>()) } returns buildTelegramBotSuccessResult()
        every { bot.sendAnimation(chatIdEq(), any<TelegramFile>()) } returns buildRetrofitSuccessResult()
        every { bot.sendAudio(chatIdEq(), any<TelegramFile>(), any<Int>(), any<String>(), any<String>()) } returns
                buildRetrofitSuccessResult()
        publish(publication)

        verifySequence {
            bot.sendMediaGroup(chatIdEq(), any<MediaGroup>())
            bot.sendMessage(chatIdEq(), any<String>())
            bot.sendAnimation(chatIdEq(), any<TelegramFile>())
            bot.sendAudio(chatIdEq(), any<TelegramFile>(), any<Int>(), any<String>(), any<String>())
        }
    }

    @Test
    fun `delete sent messages if part of the publication wasn't sent successfully`() {
        val publication = Publication(
            "9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1),
            listOf(
                Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 1),
                Attachment.Audio("iddqd".toByteArray(), "a", "b", 1),
                Attachment.Video(File("path"), 1),
                Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 1)
            )
        )

        every { bot.sendMediaGroup(chatIdEq(), any<MediaGroup>()) } returns
                TelegramBotResult.Success(listOf(buildTestResponseMessage(1), buildTestResponseMessage(2)))
        every { bot.sendMessage(chatIdEq(), any<String>()) } returns
                TelegramBotResult.Success(buildTestResponseMessage(3))
        every { bot.sendAnimation(chatIdEq(), any<TelegramFile>()) } returns
                (RetrofitResponse.success(TgBotResponse(buildTestResponseMessage(4), true)) to null)
        every { bot.sendAudio(chatIdEq(), any<TelegramFile>(), any<Int>(), any<String>(), any<String>()) } returns
                (null to IllegalArgumentException("bad data"))
        every { bot.deleteMessage(chatIdEq(), any<Long>()) } returns TelegramBotResult.Success(true)

        assertThrows<TelegramApiException> { publish(publication) }
        (1..4).forEach { messageId ->
            verify { bot.deleteMessage(chatIdEq(), messageId.toLong()) }
        }
    }

    @Nested
    inner class `send photo or video` {

        @Test
        fun `send a single photo by URL`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
            val publication = Publication("some text", listOf(photoAttachment))

            every { bot.sendPhoto(chatIdEq(), TelegramFile.ByUrl(photoAttachment.url), publication.text) } returns
                    buildRetrofitSuccessResult()
            publish(publication)

            verify { bot.sendPhoto(chatIdEq(), TelegramFile.ByUrl(photoAttachment.url), publication.text) }
        }

        @Test
        fun `send a single photo by byte array`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", ByteArray(0), 0)
            val publication = Publication("some text", listOf(photoAttachment))

            every { bot.sendPhoto(chatIdEq(), TelegramFile.ByUrl(photoAttachment.url), publication.text) } returns
                    (null to IllegalArgumentException("bad url"))
            every {
                bot.sendPhoto(
                    chatIdEq(),
                    TelegramFile.ByByteArray(photoAttachment.data),
                    publication.text
                )
            } returns buildRetrofitSuccessResult()
            publish(publication)

            verifySequence {
                bot.sendPhoto(chatIdEq(), TelegramFile.ByUrl(photoAttachment.url), publication.text)
                bot.sendPhoto(chatIdEq(), TelegramFile.ByByteArray(photoAttachment.data), publication.text)
            }
        }

        @Test
        fun `delete sent photo if part of the publication wasn't sent successfully`() {
            val publication = Publication(
                "9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1),
                listOf(Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0))
            )

            val sentPhotoMessageId = 1L
            val sendPhotoResponse =
                RetrofitResponse.success(TgBotResponse(buildTestResponseMessage(sentPhotoMessageId), true)) to null
            every { bot.sendPhoto(chatIdEq(), any<TelegramFile>(), anyNullable()) } returns sendPhotoResponse
            every { bot.sendMessage(chatIdEq(), any()) } returns TelegramBotResult.Error.TelegramApi(404, "desc")
            every { bot.deleteMessage(chatIdEq(), sentPhotoMessageId) } returns TelegramBotResult.Success(true)
            runCatching { publish(publication) }

            verifySequence {
                bot.sendPhoto(chatIdEq(), any<TelegramFile>(), anyNullable())
                bot.sendMessage(chatIdEq(), any())
                bot.deleteMessage(chatIdEq(), sentPhotoMessageId)
            }
        }

        @Test
        fun `send a single video`() {
            val videoAttachment = Attachment.Video(File("path"), 12)
            val publication = Publication("some text", listOf(videoAttachment))

            every {
                bot.sendVideo(
                    chatIdEq(),
                    TelegramFile.ByFile(videoAttachment.data),
                    videoAttachment.duration,
                    caption = publication.text
                )
            } returns buildRetrofitSuccessResult()
            publish(publication)

            verify {
                bot.sendVideo(
                    chatIdEq(),
                    TelegramFile.ByFile(videoAttachment.data),
                    videoAttachment.duration,
                    caption = publication.text
                )
            }
        }

        @Test
        fun `delete sent video if part of the publication wasn't sent successfully`() {
            val publication = Publication(
                "9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1),
                listOf(Attachment.Video(File("path"), 0))
            )

            val sentVideoMessageId = 1L
            val sentVideoResponse =
                RetrofitResponse.success(TgBotResponse(buildTestResponseMessage(sentVideoMessageId), true)) to null
            every { bot.sendVideo(chatIdEq(), any<TelegramFile>(), anyNullable()) } returns sentVideoResponse
            every { bot.sendMessage(chatIdEq(), any()) } returns TelegramBotResult.Error.TelegramApi(404, "desc")
            every { bot.deleteMessage(chatIdEq(), sentVideoMessageId) } returns TelegramBotResult.Success(true)
            runCatching { publish(publication) }

            verifySequence {
                bot.sendVideo(chatIdEq(), any<TelegramFile>(), anyNullable())
                bot.sendMessage(chatIdEq(), any())
                bot.deleteMessage(chatIdEq(), sentVideoMessageId)
            }
        }

        @Test
        fun `send photos and videos list as media group`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
            val videoAttachment = Attachment.Video(File("path"), 12)
            val publication = Publication("some text", listOf(videoAttachment, photoAttachment))
            val mediaGroupSlot = slot<MediaGroup>()

            every { bot.sendMediaGroup(chatIdEq(), capture(mediaGroupSlot)) } returns
                    TelegramBotResult.Success(listOf(buildTestResponseMessage(), buildTestResponseMessage()))
            publish(publication)

            verify { bot.sendMediaGroup(chatIdEq(), any<MediaGroup>()) }
            val capturedMedias = mediaGroupSlot.captured.medias
            assertEquals(2, capturedMedias.size)
            assertInstanceOf(InputMediaVideo::class.java, capturedMedias[0])
            val capturedVideo = capturedMedias[0] as InputMediaVideo
            assertEquals(
                InputMediaVideo(
                    TelegramFile.ByFile(videoAttachment.data),
                    publication.text,
                    duration = videoAttachment.duration
                ),
                capturedVideo
            )
            assertInstanceOf(InputMediaPhoto::class.java, capturedMedias[1])
            val capturedPhoto = capturedMedias[1] as InputMediaPhoto
            assertEquals(InputMediaPhoto(TelegramFile.ByUrl(photoAttachment.url), null), capturedPhoto)
        }

        @Test
        fun `delete sent media group if part of the publication wasn't sent successfully`() {
            val publication = Publication(
                "9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1),
                listOf(
                    Attachment.Video(File("path"), 0),
                    Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
                )
            )

            val messageIds = listOf(1L, 2L)
            every { bot.sendMediaGroup(chatIdEq(), any<MediaGroup>()) } returns
                    TelegramBotResult.Success(messageIds.map { buildTestResponseMessage(it) })
            every { bot.sendMessage(chatIdEq(), any()) } returns TelegramBotResult.Error.TelegramApi(404, "desc")
            every { bot.deleteMessage(chatIdEq(), any<Long>()) } returns TelegramBotResult.Success(true)
            runCatching { publish(publication) }

            verifySequence {
                bot.sendMediaGroup(chatIdEq(), any<MediaGroup>())
                bot.sendMessage(chatIdEq(), any())
                messageIds.forEach { bot.deleteMessage(chatIdEq(), it) }
            }
        }

        @Test
        fun `send text separately if caption length limit is exceeded`() {
            val photoAttachment = Attachment.Photo("https://localhost:4200", "iddqd".toByteArray(), 0)
            val publication = Publication("9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1), listOf(photoAttachment))

            every { bot.sendPhoto(chatIdEq(), any<TelegramFile>(), null) } returns buildRetrofitSuccessResult()
            every { bot.sendMessage(chatIdEq(), publication.text!!) } returns buildTelegramBotSuccessResult()
            publish(publication)

            verifySequence {
                bot.sendPhoto(chatIdEq(), any<TelegramFile>(), null)
                bot.sendMessage(chatIdEq(), publication.text!!)
            }
        }
    }

    @Nested
    inner class `send a GIF file` {

        @Test
        fun `send as animation`() {
            val gifAttachment = Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 0)
            val publication = Publication("text", listOf(gifAttachment))

            every { bot.sendMessage(chatIdEq(), publication.text!!) } returns buildTelegramBotSuccessResult()
            every { bot.sendAnimation(chatIdEq(), TelegramFile.ByUrl(gifAttachment.url)) } returns
                    buildRetrofitSuccessResult()
            publish(publication)

            verifySequence {
                bot.sendMessage(chatIdEq(), publication.text!!)
                bot.sendAnimation(chatIdEq(), TelegramFile.ByUrl(gifAttachment.url))
            }
        }

        @Test
        fun `send as video`() {
            val gifAttachment = Attachment.Gif("iddqd".toByteArray(), "https://localhost:4200", 0)
            val publication = Publication("text", listOf(gifAttachment))

            every { bot.sendMessage(chatIdEq(), publication.text!!) } returns buildTelegramBotSuccessResult()
            every { bot.sendAnimation(chatIdEq(), TelegramFile.ByUrl(gifAttachment.url)) } throws TelegramApiException()
            every { bot.sendVideo(chatIdEq(), TelegramFile.ByByteArray(gifAttachment.data)) } returns
                    buildRetrofitSuccessResult()
            publish(publication)

            verifySequence {
                bot.sendMessage(chatIdEq(), publication.text!!)
                bot.sendAnimation(chatIdEq(), TelegramFile.ByUrl(gifAttachment.url))
                bot.sendVideo(chatIdEq(), TelegramFile.ByByteArray(gifAttachment.data))
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
            val successResponse =
                RetrofitResponse.success(TgBotResponse(buildTestResponseMessage(sentMessageId), true)) to null
            val failureResponse = null to IllegalArgumentException("bad data")
            every { bot.sendAnimation(chatIdEq(), any<TelegramFile>()) } returns successResponse andThen failureResponse
            every { bot.sendVideo(chatIdEq(), any<TelegramFile>()) } throws TelegramApiException()
            every { bot.deleteMessage(chatIdEq(), any<Long>()) } returns TelegramBotResult.Success(true)
            runCatching { publish(publication) }

            verify(exactly = 2) { bot.sendAnimation(chatIdEq(), any<TelegramFile>()) }
            verify { bot.sendVideo(chatIdEq(), any<TelegramFile>()) }
            verify { bot.deleteMessage(chatIdEq(), sentMessageId) }
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
                    chatIdEq(),
                    TelegramFile.ByByteArray(audioAttachment.data),
                    audioAttachment.duration,
                    audioAttachment.artist,
                    audioAttachment.title
                )
            } returns buildRetrofitSuccessResult()
            publish(publication)

            verify {
                bot.sendAudio(
                    chatIdEq(),
                    TelegramFile.ByByteArray(audioAttachment.data),
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
            val successResponse =
                RetrofitResponse.success(TgBotResponse(buildTestResponseMessage(sentMessageId), true)) to null
            val failureResponse = null to IllegalArgumentException("bad data")
            every { bot.sendAudio(chatIdEq(), any<TelegramFile>(), any<Int>(), any<String>(), any<String>()) } returns
                    successResponse andThen failureResponse
            every { bot.deleteMessage(chatIdEq(), any<Long>()) } returns TelegramBotResult.Success(true)
            runCatching { publish(publication) }

            verify(exactly = 2) {
                bot.sendAudio(
                    chatIdEq(),
                    any<TelegramFile>(),
                    any<Int>(),
                    any<String>(),
                    any<String>()
                )
            }
            verify { bot.deleteMessage(chatIdEq(), sentMessageId) }
        }
    }

    @Nested
    inner class `error handling` {

        @Test
        fun `should throw exception if http call failed`() {
            val publication = Publication(null, listOf(Attachment.Video(File("path"), 0)))
            val exception = RuntimeException("something went wrong")

            every { bot.sendVideo(chatIdEq(), any<TelegramFile>(), anyNullable()) } returns (null to exception)
            val thrownException = runCatching { publish(publication) }.exceptionOrNull()
                ?: fail("exception expected")

            assertInstanceOf(TelegramApiException::class.java, thrownException)
            assertEquals(exception, thrownException.cause)
        }

        @Test
        fun `should throw exception if http response contains error`() {
            val publication = Publication(null, listOf(Attachment.Video(File("path"), 0)))
            val errorDescription = "error description"
            val errorResponseBody = ResponseBody.create(null, errorDescription)

            every { bot.sendVideo(chatIdEq(), any<TelegramFile>(), anyNullable()) } returns
                    (RetrofitResponse.error<TgBotResponse<Message>>(400, errorResponseBody) to null)
            val thrownException = runCatching { publish(publication) }.exceptionOrNull()
                ?: fail("exception expected")

            assertInstanceOf(TelegramApiException::class.java, thrownException)
            assertEquals(errorDescription, thrownException.message)
        }

        @Test
        fun `should throw exception if telegram bot returns unknown error`() {
            val publication = Publication("text", listOf())
            val exception = RuntimeException("something went wrong")

            every { bot.sendMessage(chatIdEq(), any<String>()) } returns TelegramBotResult.Error.Unknown(exception)
            val thrownException = runCatching { publish(publication) }.exceptionOrNull()
                ?: fail("exception expected")

            assertInstanceOf(TelegramApiException::class.java, thrownException)
            assertEquals(exception, thrownException.cause)
        }

        @Test
        fun `should throw exception if telegram bot returns error`() {
            val publication = Publication("text", listOf())
            val tgBotError = TelegramBotResult.Error.TelegramApi(400, "description")

            every { bot.sendMessage(chatIdEq(), any<String>()) } returns tgBotError
            val thrownException = runCatching { publish(publication) }.exceptionOrNull()
                ?: fail("exception expected")

            assertInstanceOf(TelegramApiException::class.java, thrownException)
            assertEquals(tgBotError.toString(), thrownException.message)
        }

        @Test
        fun `should ignore telegram bot error when telegram server is unable to access resource`() {
            val publication = Publication(
                null,
                listOf(Attachment.Video(File("path"), 0), Attachment.Video(File("path"), 0))
            )
            val tgBotError = TelegramBotResult.Error.TelegramApi(400, "foo WEBPAGE_MEDIA_EMPTY bar")

            every { bot.sendMediaGroup(chatIdEq(), any<MediaGroup>()) } returns tgBotError
            publish(publication)

            verify(ordering = Ordering.SEQUENCE) { bot.sendMediaGroup(chatIdEq(), any<MediaGroup>()) }
        }
    }

    private fun publish(publication: Publication) = tgApi.publish(CHANNEL_ID, publication)

    private fun buildTelegramBotSuccessResult() = TelegramBotResult.Success(buildTestResponseMessage())

    private fun buildRetrofitSuccessResult() =
        (RetrofitResponse.success(TgBotResponse(buildTestResponseMessage(), true)) to null)

    private fun buildTestResponseMessage(messageId: Long = 1L) =
        Message(messageId = messageId, date = 1L, chat = Chat(1L, "Test"))

    private fun MockKMatcherScope.chatIdEq(chatId: Long = CHANNEL_ID) = match<ChatId> {
        (it as ChatId.Id).id == chatId
    }

    companion object {
        private const val CHANNEL_ID = -7788L
    }
}
