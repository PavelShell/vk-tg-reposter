package com.pavelshell.entites

import com.jfposton.ytdlp.YtDlp
import com.jfposton.ytdlp.YtDlpException
import com.jfposton.ytdlp.YtDlpRequest
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.ServiceActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import com.vk.api.sdk.objects.photos.Photo
import com.vk.api.sdk.objects.wall.GetFilter
import com.vk.api.sdk.objects.wall.WallItem
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant

class VkApi(appId: Int, accessToken: String) {

    private val vkApi = VkApiClient(HttpTransportClient())

    private val service = ServiceActor(appId, accessToken)

    /**
     * Returns list of wall posts created after [timePoint] for a user or community with [domain].
     * Returned posts are sorted by creation date ascending.
     */
    fun getWallPostsFrom(timePoint: Instant, domain: String): List<WallItem> {
        logger.info("Fetching wall posts from VK API for $domain starting from $timePoint")
        var offset = 0
        var wallPostsSlice = getWallPostsSlice(domain, offset, AVERAGE_NEW_POSTS_COUNT).also {
            if (it.isEmpty()) logger.warn("0 posts were found for domain $domain")
        }
        val result: MutableList<WallItem> = mutableListOf()
        while (wallPostsSlice.isNotEmpty()) {
            var areAllNewPostsFound = false
            val newPosts = wallPostsSlice
                .filter { !it.isPinned() }
                .takeWhile { (timePoint.epochSecond < it.date).also { areAllNewPostsFound = true } }
            result.addAll(newPosts.filter { !it.isPinned() })
            if (areAllNewPostsFound) {
                break
            }
            offset += newPosts.size
            wallPostsSlice = getWallPostsSlice(domain, offset, MAX_WALL_POSTS_PER_REQUEST)
        }
        return result.reversed()
    }

    private fun getWallPostsSlice(domain: String, offset: Int, count: Int): List<WallItem> = vkApi.wall().get(service)
        .domain(domain)
        .filter(GetFilter.ALL)
        .offset(offset)
        .count(count)
        .execute()
        .items

    /**
     * Returns ULR of the [photo] of size up to 2560x2048px.
     */
    fun getPhotoUrl(photo: Photo): URI = photo.sizes.toMutableList()
        .apply { sortBy { it.height + it.width } }
        .last()
        .url

    /**
     * Tries to download the file located at [vkFileUrl].
     * Returns null in case of failure or if file size is bigger than [maxSizeMb].
     */
    fun tryDownloadFile(vkFileUrl: URI, maxSizeMb: Int = Int.MAX_VALUE): Pair<URL, ByteArray>? {
        if (vkFileUrl.toString().isEmpty()) {
            // file is blocked
            return null
        }
        val url = vkFileUrl.toURL().followRedirect()
        val bytes = url.readBytes()
        return if (bytes.size > maxSizeMb * 1_048_576L) null else url to bytes
    }

    private fun URL.followRedirect(): URL {
        val connection = openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connect()
        val location = connection.getHeaderField("Location")
        connection.disconnect()
        return if (location != null) URI.create(location).toURL() else this
    }

    /**
     * Tries to download the [video].
     * Returns null in case of failure or if video size is bigger than [maxSizeMb].
     */
    fun tryDownloadVideo(id: Long, ownerId: Long, maxSizeMb: Int = Int.MAX_VALUE): File? {
        val url = "https://vk.com/video${ownerId}_${id}"
        val videoFile = File("${ownerId}_${id}").apply { deleteOnExit() }
        val request = YtDlpRequest(url).apply {
            setOption("max-filesize", "${maxSizeMb}M")
            setOption("format", "best[height<=720]")
            setOption("output", videoFile.absolutePath)
            setOption("force-overwrites")
        }
        try {
            YtDlp.execute(request)
            return videoFile.also { if (!it.exists()) return null }
        } catch (e: YtDlpException) {
            logger.warn("Unable to download video from $url", e)
            return null
        }
    }

    private companion object {

        const val MAX_WALL_POSTS_PER_REQUEST = 100

        const val AVERAGE_NEW_POSTS_COUNT = 10

        private val logger = LoggerFactory.getLogger(VkApi::class.java)
    }
}
