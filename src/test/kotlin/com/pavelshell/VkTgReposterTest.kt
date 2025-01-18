package com.pavelshell

import com.pavelshell.logic.FileStorage
import com.pavelshell.logic.TgApi
import com.pavelshell.logic.TgApi.TelegramApiException
import com.pavelshell.logic.VkApi
import com.pavelshell.models.Attachment
import com.pavelshell.models.Publication
import com.vk.api.sdk.objects.audio.Audio
import com.vk.api.sdk.objects.base.Link
import com.vk.api.sdk.objects.docs.Doc
import com.vk.api.sdk.objects.photos.Photo
import com.vk.api.sdk.objects.video.VideoFull
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import com.vk.api.sdk.objects.wall.WallpostFull
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.net.URI
import java.net.URL
import java.time.Instant

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class VkTgReposterTest {

    @MockK
    private lateinit var vkApi: VkApi

    @MockK
    private lateinit var tgBot: TgApi

    @InjectMockKs
    private lateinit var vkTgReposter: VkTgReposter

    @BeforeEach
    fun `stub static methods`() {
        mockkObject(FileStorage)
    }

    @Nested
    inner class `get posts starting certain time` {

        @Test
        fun `do nothing if no new publications found`() {
            every { vkApi.getWallPostsFrom(any<Instant>(), any<String>()) } returns listOf()
            vkTgReposter.duplicatePostsFromVkGroup(listOf("123" to 123, "321" to 321))

            verify(exactly = 2) { vkApi.getWallPostsFrom(any<Instant>(), any<String>()) }
            verify(exactly = 0) { tgBot.publish(any<Long>(), any<Publication>()) }
        }

        @Test
        fun `get posts newer than last processed post`() {
            val time = Instant.ofEpochSecond(778899)
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf()
            }

            every { FileStorage.get(VK_DOMAIN) } returns time.epochSecond.toString()
            every { vkApi.getWallPostsFrom(time, VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verifyOrder {
                FileStorage.get(VK_DOMAIN)
                vkApi.getWallPostsFrom(time, VK_DOMAIN)
                tgBot.publish(TG_ID, any<Publication>())
            }
        }

        @Test
        fun `get posts after time defined by env variable`() {
            val time = Instant.ofEpochSecond(778899)
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf()
            }
            val vkTgReposterSpy = spyk(vkTgReposter, recordPrivateCalls = true)

            every { FileStorage.get(VK_DOMAIN) } returns null
            every { vkTgReposterSpy["getEnv"]("LAST_PUBLICATION_UNIX_TIMESTAMP_$VK_DOMAIN") } returns
                    time.epochSecond.toString()
            every { vkApi.getWallPostsFrom(time, VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposterSpy.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verifyOrder {
                FileStorage.get(VK_DOMAIN)
                vkTgReposterSpy["getEnv"]("LAST_PUBLICATION_UNIX_TIMESTAMP_$VK_DOMAIN")
                vkApi.getWallPostsFrom(time, VK_DOMAIN)
                tgBot.publish(TG_ID, any<Publication>())
            }
        }

        @Test
        fun `get all posts if no time specified`() {
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf()
            }
            val vkTgReposterSpy = spyk(vkTgReposter, recordPrivateCalls = true)

            every { FileStorage.get(VK_DOMAIN) } returns null
            every { vkTgReposterSpy["getEnv"]("LAST_PUBLICATION_UNIX_TIMESTAMP_$VK_DOMAIN") } returns null
            every { vkApi.getWallPostsFrom(Instant.MIN, VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposterSpy.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verifyOrder {
                FileStorage.get(VK_DOMAIN)
                vkTgReposterSpy["getEnv"]("LAST_PUBLICATION_UNIX_TIMESTAMP_$VK_DOMAIN")
                vkApi.getWallPostsFrom(Instant.MIN, VK_DOMAIN)
                tgBot.publish(TG_ID, any<Publication>())
            }
        }
    }

    @Nested
    inner class `convert VK wall post to Telegram publication` {

        @Test
        fun `should skip publication if it is reposted publication`() {
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf(WallpostAttachment().apply { type = WallpostAttachmentType.PHOTO })
                copyHistory = listOf(WallpostFull())
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }

        @Test
        fun `convert to publication with text only`() {
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf()
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify { tgBot.publish(TG_ID, Publication(newWallPost.text)) }
        }

        @Test
        fun `should skip publication if message length exceeded`() {
            val newWallPost = WallItem().apply {
                text = "9".repeat(TgApi.MAX_MESSAGE_TEXT_SIZE + 1)
                attachments = listOf(WallpostAttachment().apply { type = WallpostAttachmentType.PHOTO })
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }

        @Test
        fun `should append link to the text of the publication`() {
            val linkAttachment = Link().apply { url = "some-url://" }
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf(WallpostAttachment().apply {
                    type = WallpostAttachmentType.LINK
                    link = linkAttachment
                })
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify { tgBot.publish(TG_ID, Publication("${newWallPost.text}\n\n${linkAttachment.url}")) }
        }

        @Test
        fun `should not append link to the text of publication if text already includes link`() {
            val linkAttachment = Link().apply { url = "some-url://" }
            val newWallPost = WallItem().apply {
                text = "wall post text with the link ${linkAttachment.url} somewhere"
                attachments = listOf(WallpostAttachment().apply {
                    type = WallpostAttachmentType.LINK
                    link = linkAttachment
                })
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify { tgBot.publish(TG_ID, Publication(newWallPost.text)) }
        }

        @Test
        fun `should convert VK attachment of PHOTO type`() {
            val photoAttachment = Photo().apply { id = 7788 }
            val photoUri = URI.create("http://localhost:1707")
            val downloadedPhotoUrl = URI.create("http://localhost:1707").toURL()
            val downloadedPhotoData = "iddqd".toByteArray()
            val newWallPost = WallItem().apply {
                text = "wall post text"
                attachments = listOf(WallpostAttachment().apply {
                    type = WallpostAttachmentType.PHOTO
                    photo = photoAttachment
                })
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.getPhotoUrl(photoAttachment) } returns photoUri
            every { vkApi.tryDownloadFile(photoUri, TgApi.MAX_FILE_SIZE_MB) } returns
                    (downloadedPhotoUrl to downloadedPhotoData)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            val expectedAttachment = Attachment.Photo(
                downloadedPhotoUrl.toString(),
                downloadedPhotoData,
                photoAttachment.id
            )
            verify { tgBot.publish(TG_ID, Publication(newWallPost.text, listOf(expectedAttachment))) }
        }

        @Test
        fun `should skip publication if unable to download attached photo`() {
            val photoAttachment = Photo().apply { id = 7788 }
            val photoUri = URI.create("http://localhost:1707")
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.PHOTO
                        photo = photoAttachment
                    },
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.VIDEO
                        video = VideoFull()
                    }
                )
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.getPhotoUrl(photoAttachment) } returns photoUri
            every { vkApi.tryDownloadFile(photoUri, TgApi.MAX_FILE_SIZE_MB) } returns null
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }

        @Test
        fun `should convert VK attachment of VIDEO type`() {
            val videoAttachment = VideoFull().apply {
                id = 7788
                ownerId = 8877
                duration = 1234
            }
            val downloadedVideo = File("")
            val newWallPost = WallItem().apply {
                attachments = listOf(WallpostAttachment().apply {
                    type = WallpostAttachmentType.VIDEO
                    video = videoAttachment
                })
                text = ""
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every {
                vkApi.tryDownloadVideo(videoAttachment.id.toLong(), videoAttachment.ownerId, TgApi.MAX_FILE_SIZE_MB)
            } returns downloadedVideo
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            val expectedAttachment = Attachment.Video(downloadedVideo, videoAttachment.duration)
            verify { tgBot.publish(TG_ID, Publication(null, listOf(expectedAttachment))) }
        }

        @Test
        fun `should skip publication if unable to download video`() {
            val videoAttachment = VideoFull().apply {
                id = 7788
                ownerId = 8877
                duration = 1234
            }
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.VIDEO
                        video = videoAttachment
                    },
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.PHOTO
                        photo = Photo()
                    }
                )
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every {
                vkApi.tryDownloadVideo(videoAttachment.id.toLong(), videoAttachment.ownerId, TgApi.MAX_FILE_SIZE_MB)
            } returns null
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }

        @Test
        fun `should convert VK attachment of DOC type`() {
            val docAttachment = Doc().apply {
                id = 7788
                url = URI.create("http://localhost:1707")
                type = 3
            }
            val downloadedDocUrl = URI.create("http://localhost:1234").toURL()
            val downloadedDocData = "iddqd".toByteArray()
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(WallpostAttachment().apply {
                    type = WallpostAttachmentType.DOC
                    doc = docAttachment
                })
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.tryDownloadFile(docAttachment.url, TgApi.MAX_FILE_SIZE_MB) } returns
                    (downloadedDocUrl to downloadedDocData)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            val expectedAttachment = Attachment.Gif(
                downloadedDocData,
                downloadedDocUrl.toString(),
                docAttachment.id
            )
            verify { tgBot.publish(TG_ID, Publication(null, listOf(expectedAttachment))) }
        }

        @Test
        fun `should skip publication if unable to download attached doc`() {
            val docAttachment = Doc().apply {
                id = 7788
                url = URI.create("http://localhost:1707")
                type = 3
            }
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.DOC
                        doc = docAttachment
                    },
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.VIDEO
                        video = VideoFull()
                    }
                )
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.tryDownloadFile(docAttachment.url, TgApi.MAX_FILE_SIZE_MB) } returns null
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }

        @Test
        fun `should convert VK attachment of AUDIO type`() {
            val audioAttachment = Audio().apply {
                id = 7788
                url = URI.create("http://localhost:1707")
                artist = "artist"
                title = "title"
                duration = 1234
            }
            val downloadedAudioData = "iddqd".toByteArray()
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(WallpostAttachment().apply {
                    type = WallpostAttachmentType.AUDIO
                    audio = audioAttachment
                })
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.tryDownloadFile(audioAttachment.url, TgApi.MAX_FILE_SIZE_MB) } returns
                    (mockk<URL>() to downloadedAudioData)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            val expectedAttachment = Attachment.Audio(
                downloadedAudioData,
                audioAttachment.artist,
                audioAttachment.title,
                audioAttachment.duration
            )
            verify { tgBot.publish(TG_ID, Publication(null, listOf(expectedAttachment))) }
        }

        @Test
        fun `should skip publication if unable to download attached audio`() {
            val audioAttachment = Audio().apply {
                id = 7788
                url = URI.create("http://localhost:1707")
                artist = "artist"
                title = "title"
                duration = 1234
            }
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.AUDIO
                        audio = audioAttachment
                    },
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.VIDEO
                        video = VideoFull()
                    }
                )
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.tryDownloadFile(audioAttachment.url, TgApi.MAX_FILE_SIZE_MB) } returns null
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }

        @Test
        fun `should skip publication if attachment is unsupported`() {
            val audioAttachment = Audio().apply {
                id = 7788
                url = URI.create("http://localhost:1707")
                artist = "artist"
                title = "title"
                duration = 1234
            }
            val downloadedAudioData = "iddqd".toByteArray()
            val newWallPost = WallItem().apply {
                text = ""
                attachments = listOf(
                    WallpostAttachment().apply {
                        type = WallpostAttachmentType.AUDIO
                        audio = audioAttachment
                    },
                    WallpostAttachment().apply { type = WallpostAttachmentType.ARTICLE }
                )
            }

            every { FileStorage.get(VK_DOMAIN) } returns "778899"
            every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(newWallPost)
            every { vkApi.tryDownloadFile(audioAttachment.url, TgApi.MAX_FILE_SIZE_MB) } returns
                    (mockk<URL>() to downloadedAudioData)
            vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

            verify(exactly = 0) { tgBot.publish(TG_ID, any<Publication>()) }
        }
    }

    @Test
    fun `should save date of last processed publication`() {
        val post1 = WallItem().apply {
            text = "wall post text1"
            attachments = listOf()
            date = 1
        }
        val post2 = WallItem().apply {
            text = "wall post text2"
            attachments = listOf()
            date = 2
        }
        val post3 = WallItem().apply {
            text = "wall post text3"
            attachments = listOf()
            date = 3
        }

        every { FileStorage.get(VK_DOMAIN) } returns "778899"
        every { vkApi.getWallPostsFrom(any<Instant>(), VK_DOMAIN) } returns listOf(post1, post2, post3)
        every { tgBot.publish(TG_ID, any<Publication>()) } returns Unit andThenThrows TelegramApiException()
        vkTgReposter.duplicatePostsFromVkGroup(listOf(VK_DOMAIN to TG_ID))

        verifyOrder {
            tgBot.publish(TG_ID, any<Publication>())
            tgBot.publish(TG_ID, any<Publication>())
            FileStorage.set(VK_DOMAIN, post1.date.toString())
        }
    }

    companion object {

        private const val VK_DOMAIN = "iddqd"

        private const val TG_ID = -7788L
    }
}
