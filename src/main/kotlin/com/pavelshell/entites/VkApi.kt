package com.pavelshell.entites

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.ServiceActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import com.vk.api.sdk.objects.photos.Photo
import com.vk.api.sdk.objects.wall.GetFilter
import com.vk.api.sdk.objects.wall.WallItem
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.Instant

class VkApi(appId: Int, accessToken: String) {

    private val vkApi = VkApiClient(HttpTransportClient())

    private val service = ServiceActor(appId, accessToken)

    private val logger = LoggerFactory.getLogger(VkApi::class.java)

    /**
     * Returns list of wall posts created after [timePoint] for user or community with [domain].
     * Returned posts are sorted by creation date ascending.
     */
    fun getWallPostsFrom(timePoint: Instant, domain: String): List<WallItem> {
        var offset = 0
        var wallPostsSlice = getWallPostsSlice(domain, offset, AVERAGE_NEW_POSTS_COUNT).also {
            if (it.isEmpty()) logger.warn("0 posts were found for domain $domain")
        }
        val result: MutableList<WallItem> = mutableListOf()
        while (wallPostsSlice.isNotEmpty()) {
            val newPosts = wallPostsSlice.takeWhile { timePoint.epochSecond < it.date }
            result.addAll(newPosts)
            val areAllNePostsFound = newPosts.size != wallPostsSlice.size
            if (areAllNePostsFound) {
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

    fun getPhotoUrl(photo: Photo): URL {
        return photo.sizes.toMutableList()
            .apply { sortBy { it.height + it.width } }
            .last()
            .url.toURL()
    }

    private companion object {

        const val MAX_WALL_POSTS_PER_REQUEST = 100

        const val AVERAGE_NEW_POSTS_COUNT = 10
    }
}
