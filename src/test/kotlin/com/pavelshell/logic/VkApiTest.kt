package com.pavelshell.logic

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.jfposton.ytdlp.YtDlp
import com.jfposton.ytdlp.YtDlpException
import com.jfposton.ytdlp.YtDlpRequest
import com.jfposton.ytdlp.YtDlpResponse
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.ServiceActor
import com.vk.api.sdk.objects.photos.Photo
import com.vk.api.sdk.objects.photos.PhotoSizes
import com.vk.api.sdk.objects.wall.GetFilter
import com.vk.api.sdk.objects.wall.WallItem
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.time.Instant


@WireMockTest
@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class VkApiTest {

    @MockK
    private lateinit var serviceActor: ServiceActor

    @MockK
    private lateinit var apiClient: VkApiClient

    @InjectMockKs
    private lateinit var vkApi: VkApi

    @RepeatedTest(value = 10, failureThreshold = 1)
    fun `get URL of the provided photo`() {
        val biggerPhoto = PhotoSizes().apply {
            width = 200
            height = 100
            url = URI.create("https://big-photo.eu")
        }
        val lesserPhoto = PhotoSizes().apply {
            width = 100
            height = 50
            url = URI.create("https://little-photo.eu")
        }
        val photo = Photo().apply { sizes = mutableListOf(lesserPhoto, biggerPhoto).shuffled() }

        val url = vkApi.getPhotoUrl(photo)

        assertEquals(biggerPhoto.url, url)
    }

    @Nested
    inner class `download file` {

        @Test
        fun `by URL`(wm: WireMockRuntimeInfo) {
            val file = File.createTempFile("prefix", "suffix").apply { writeText("vk api test file") }
            val filePath = "/vk-api-test-file"

            stubFor(get(filePath).willReturn(ok().withBody(file.readBytes())))
            val downloadedFile = vkApi.tryDownloadFile(URI.create("${wm.httpBaseUrl}${filePath}"))
                ?: fail("Failed to download file")

            assertEquals("${wm.httpBaseUrl}${filePath}", downloadedFile.first.toString())
            assertArrayEquals(file.readBytes(), downloadedFile.second)
        }

        @Test
        fun `by URL with redirect`(wm: WireMockRuntimeInfo) {
            val file = File.createTempFile("prefix", "suffix").apply { writeText("vk api test file") }
            val filePath = "/vk-api-test-file"
            val fileRedirectPath = "/vk-api-test-file-redirect"

            stubFor(
                get(filePath).willReturn(ok().withHeader("Location", "${wm.httpBaseUrl}${fileRedirectPath}"))
            )
            stubFor(get(fileRedirectPath).willReturn(ok().withBody(file.readBytes())))
            val downloadedFile = vkApi.tryDownloadFile(URI.create("${wm.httpBaseUrl}${filePath}"))
                ?: fail("Failed to download file")

            assertEquals("${wm.httpBaseUrl}${fileRedirectPath}", downloadedFile.first.toString())
            assertArrayEquals(file.readBytes(), downloadedFile.second)
        }

        @Test
        fun `return null if file size is larger than allowed maximum`(wm: WireMockRuntimeInfo) {
            val bytesInMb = 1_048_576L
            val file = File.createTempFile("prefix", "suffix").also {
                RandomAccessFile(it, "rw").use { raf -> raf.setLength(bytesInMb * 2) }
            }
            val filePath = "/vk-api-test-file"

            stubFor(get(filePath).willReturn(ok().withBody(file.readBytes())))
            val downloadedFile = vkApi.tryDownloadFile(URI.create("${wm.httpBaseUrl}${filePath}"), 1)

            assertNull(downloadedFile)
        }

        @Test
        fun `return null if URL is empty`() {
            val downloadedFile = vkApi.tryDownloadFile(URI.create(""))

            assertNull(downloadedFile)
        }
    }

    @Nested
    inner class `download video` {
        // TODO: write integration test

        @Test
        fun normally() {
            val videoId = 1L
            val ownerId = 2L
            mockkStatic(YtDlp::class)
            val requestSlot = slot<YtDlpRequest>()
            val expectedFileContent = "fileContent".toByteArray()

            every { YtDlp.execute(capture((requestSlot))) } answers {
                val outputFilePath = requestSlot.captured.option["output"] ?: fail("output file doesn't exists")
                File(outputFilePath).writeBytes(expectedFileContent)
                mockk<YtDlpResponse>()
            }
            val downloadedVideFile = vkApi.tryDownloadVideo(videoId, ownerId)

            assertNotNull(downloadedVideFile)
            assertArrayEquals(expectedFileContent, downloadedVideFile!!.readBytes())
            assertEquals("https://vk.com/video${ownerId}_${videoId}", requestSlot.captured.url)
            assertEquals("${Int.MAX_VALUE}M", requestSlot.captured.option["max-filesize"])
            assertEquals("best[height<=720]", requestSlot.captured.option["format"])
            assertNotNull(requestSlot.captured.option.containsKey("force-overwrites"))
        }


        @Test
        fun `when file is too big`() {
            val maxVideoSizeInMb = 3
            mockkStatic(YtDlp::class)
            val requestSlot = slot<YtDlpRequest>()

            every { YtDlp.execute(capture((requestSlot))) } returns mockk<YtDlpResponse>()
            val downloadedVideFile = vkApi.tryDownloadVideo(0L, 0L, maxVideoSizeInMb)

            assertNull(downloadedVideFile)
            assertEquals("${maxVideoSizeInMb}M", requestSlot.captured.option["max-filesize"])
        }


        @Test
        fun `when unable to download`() {
            mockkStatic(YtDlp::class)

            every { YtDlp.execute(any()) } throws YtDlpException("Content is unavailable or something")
            val downloadedVideFile = vkApi.tryDownloadVideo(0L, 0L)

            assertNull(downloadedVideFile)
        }
    }

    @Nested
    inner class `get wall posts` {

        private val domain = "domain"

        @Test
        fun `starting form specific time`() {
            val timePoint = Instant.ofEpochSecond(7788)
            val newWallItems = listOf(
                createWallPost(date = timePoint.epochSecond.toInt() + 3),
                createWallPost(date = timePoint.epochSecond.toInt() + 2),
                createWallPost(date = timePoint.epochSecond.toInt() + 1),
            )
            val oldWallItems = listOf(
                createWallPost(date = timePoint.epochSecond.toInt()),
                createWallPost(date = timePoint.epochSecond.toInt() - 1),
                createWallPost(date = timePoint.epochSecond.toInt() - 2),
                createWallPost(date = timePoint.epochSecond.toInt() - 3),
            )

            every { getWallPostsSlice(0) } returns newWallItems + oldWallItems
            val result = vkApi.getWallPostsFrom(timePoint, domain)

            assertEquals(newWallItems.reversed(), result)
        }

        @Test
        fun `with pagination`() {
            val firstPage = (1..10).map { createWallPost(date = Int.MAX_VALUE) }
            val secondPage = (11..21).map { createWallPost(date = Int.MAX_VALUE) }
            val thirdPage = emptyList<WallItem>()

            every { getWallPostsSlice(0) } returns firstPage
            every { getWallPostsSlice(10) } returns secondPage
            every { getWallPostsSlice(21) } returns thirdPage
            val result = vkApi.getWallPostsFrom(Instant.MIN, domain)

            verify(ordering = Ordering.ORDERED) { getWallPostsSlice(0) }
            verify(ordering = Ordering.ORDERED) { getWallPostsSlice(10) }
            verify(ordering = Ordering.ORDERED) { getWallPostsSlice(21) }
            assertEquals(firstPage.size + secondPage.size + thirdPage.size, result.size)
            assertTrue(result.containsAll(firstPage))
            assertTrue(result.containsAll(secondPage))
        }

        @Test
        fun `skipping pinned publication`() {
            val pinnedPublication = spyk(createWallPost(date = Int.MAX_VALUE))

            every { pinnedPublication.isPinned() } returns true
            every { getWallPostsSlice(0) } returns listOf(pinnedPublication, createWallPost(date = Int.MAX_VALUE))
            every { getWallPostsSlice(2) } returns listOf()
            val result = vkApi.getWallPostsFrom(Instant.now(), domain)

            assertFalse(result.contains(pinnedPublication))
            assertEquals(1, result.size)
        }

        @Test
        fun `if there is no posts for the provided domain`() {
            every { getWallPostsSlice(0) } returns listOf()

            val result = vkApi.getWallPostsFrom(Instant.now(), domain)

            assertEquals(0, result.size)
        }

        private fun createWallPost(
            date: Int = Int.MAX_VALUE,
            isPinned: Boolean = false
        ): WallItem = object : WallItem() {

            override fun isPinned() = isPinned

            override fun getDate() = date

            override fun equals(other: Any?): Boolean {
                return this === other
            }
        }

        private fun getWallPostsSlice(offset: Int): List<WallItem> = apiClient.wall()
            .get(serviceActor)
            .domain(domain)
            .filter(GetFilter.ALL)
            .offset(offset)
            .count(100)
            .execute()
            .items
    }
}
