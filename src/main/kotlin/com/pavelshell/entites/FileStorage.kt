package com.pavelshell.entites

import java.io.File
import kotlin.io.path.createParentDirectories

/**
 * Simple file-based storage.
 */
object FileStorage {

    private val lastPublicationDatesFile = File("vk-tg-reposter-storage/storage")
        .also {
            it.toPath().createParentDirectories()
            it.createNewFile()
        }

    /**
     * Returns value for a [key] if it exists.
     */
    fun get(key: String): String? = readLines()[key]

    /**
     * Saves [value] to storage.
     */
    fun set(key: String, value: String) {
        val newFileContent = readLines()
            .also { it[key] = value }
            .entries
            .joinToString(separator = "\n") { "${it.key} ${it.value}" }
        lastPublicationDatesFile.writeText(newFileContent)
    }

    private fun readLines(): MutableMap<String, String> = try {
        lastPublicationDatesFile.readLines()
            .associate {
                val (key, value) = it.split(" ")
                key to value
            }
            .toMutableMap()
    } catch (e: Exception) {
        throw IllegalStateException("Can't read file ${lastPublicationDatesFile.absoluteFile}", e)
    }
}
