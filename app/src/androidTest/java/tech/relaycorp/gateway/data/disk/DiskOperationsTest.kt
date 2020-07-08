package tech.relaycorp.gateway.data.disk

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tech.relaycorp.gateway.App
import java.io.File
import java.nio.charset.Charset
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class DiskOperationsTest {

    private lateinit var diskOperations: DiskOperations

    private val folder by lazy {
        File(getApplicationContext<App>().filesDir, "test").also { it.mkdirs() }
    }

    @Before
    fun setUp() {
        diskOperations = DiskOperations(getApplicationContext<App>())
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun writeMessage() {
        runBlocking {
            val size = Random.nextLong(1, 10)
            val message = ByteArray(size.toInt())
            diskOperations.writeMessage(folder.name, "file_", message)

            val files = folder.listFiles()!!
            assertEquals(1, files.size)
            assertEquals(size, files.first().length())
        }
    }

    @Test
    fun listMessages() {
        runBlocking {
            assertEquals(0, diskOperations.listMessages(folder.name).size)
            diskOperations.writeMessage(folder.name, "file_", ByteArray(1))
            assertEquals(1, diskOperations.listMessages(folder.name).size)
        }
    }

    @Test
    fun writeAndReadMessage() {
        runBlocking {
            val message = "123456"
            val path = diskOperations.writeMessage(folder.name, "file_", message.toByteArray())
            val result = diskOperations.readMessage(folder.name, path)()
                .readBytes().toString(Charset.defaultCharset())
            assertEquals(message, result)
        }
    }

    @Test
    fun deleteMessage() {
        runBlocking {
            val path = diskOperations.writeMessage(folder.name, "file_", ByteArray(1))
            diskOperations.deleteMessage(folder.name, path)
            assertFalse(File(folder, path).exists())
        }
    }

    @Test
    fun deleteAllMessages() {
        runBlocking {
            repeat(3) {
                diskOperations.writeMessage(folder.name, "file_", ByteArray(1))
            }
            diskOperations.deleteAllMessages(folder.name)
            assertEquals(0, folder.list()?.size ?: 0)
        }
    }
}
