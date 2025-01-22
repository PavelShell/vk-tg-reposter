package com.pavelshell.logic

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File


class FileBasedStorageTest {

    @BeforeEach
    @AfterEach
    fun `re-create storage file`() {
        storageFile.delete()
        storageFile.createNewFile()
    }

    @Test
    fun `should save string value`() {
        val key = "key"
        val value = "value"

        FileBasedStorage.set("key", "value")

        val savedValue = FileBasedStorage.get(key)
        Assertions.assertEquals(value, savedValue)
        val storageFileLines = storageFile.readLines()
        Assertions.assertEquals(1, storageFileLines.size)
        Assertions.assertEquals("$key $value", storageFileLines.first())
    }

    @Test
    fun `should return saved value`() {
        val key = "key"
        val value = "value"
        storageFile.writeText("$key $value")

        val savedValue = FileBasedStorage.get(key)

        Assertions.assertEquals(value, savedValue)
    }

    companion object {
        private val storageFile = File("vk-tg-reposter-storage/storage")
    }
}
