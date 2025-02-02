package com.pavelshell.logic

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

/**
 * Represents API for working with VK.com platform.
 */
class VkApi {

    private val service: ServiceActor

    private val apiClient: VkApiClient

    /**
     * Creates VK API that is accessed by [accessToken] and [appId].
     * To get these values go to [VK auth service page](https://id.vk.com/about/business/go).
     */
    constructor(appId: Int, accessToken: String) {
        service = ServiceActor(appId, accessToken)
        apiClient = VkApiClient(HttpTransportClient())
    }

    /**
     * Default constructor with all dependencies.
     */
    // created for testing
    constructor(service: ServiceActor, apiClient: VkApiClient) {
        this.service = service
        this.apiClient = apiClient
    }

    /**
     * Returns list of wall posts created after [timePoint] for a user or community with [domain].
     * Returned posts are sorted by creation date ascending.
     */
    fun getWallPostsFrom(timePoint: Instant, domain: String): List<WallItem> {
        return apiClient.wall().getById(service, "-229183781_3").execute().items
        logger.info("Fetching wall posts from VK API for $domain starting from $timePoint")
        var offset = 0
        var wallPostsSlice = getWallPostsSlice(domain, offset)
            .toMutableList()
            .also {
                if (it.isEmpty()) logger.warn("0 posts were found for domain $domain")
                if (it.isNotEmpty() && it[0].isPinned()) {
                    it.removeFirst()
                    offset += 1
                }
            }
            .toList()
        val result: MutableList<WallItem> = mutableListOf()
        while (wallPostsSlice.isNotEmpty()) {
            var areAllNewPostsFound = false
            val newPosts = wallPostsSlice.takeWhile { post ->
                (timePoint.epochSecond < post.date).also { areAllNewPostsFound = !it }
            }
            result.addAll(newPosts)
            if (areAllNewPostsFound) {
                break
            }
            offset += newPosts.size
            wallPostsSlice = getWallPostsSlice(domain, offset)
        }
        return result.reversed()
    }

    private fun getWallPostsSlice(domain: String, offset: Int): List<WallItem> = apiClient.wall()
        .get(service)
        .domain(domain)
        .filter(GetFilter.ALL)
        .offset(offset)
        .count(100)
        .execute()
        .items
        // rate limit for VK API is 3 requests per second --- 1000 / 3 == 333
        .also { Thread.sleep(333) }

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
            logger.warn("URL is empty, can't download the file.")
            return null
        }
        val url = vkFileUrl.toURL().followRedirect()
        val bytes = url.readBytes()
        return if (bytes.size > maxSizeMb * BYTES_IN_MB) null else url to bytes
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
     * Tries to download the video identified by [ownerId] and [id].
     * Returns null in case of failure or if video size is bigger than [maxSizeMb].
     */
    fun tryDownloadVideo(id: Long, ownerId: Long, maxSizeMb: Int = Int.MAX_VALUE): File? {
        val url = "https://vk.com/video${ownerId}_${id}"
        // todo autodelete files on garbage collection
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
            logger.warn("Unable to download video from $url.", e)
            return null
        }
    }

    private companion object {

        private const val BYTES_IN_MB = 1_048_576L

        private val logger = LoggerFactory.getLogger(VkApi::class.java)
    }
}
