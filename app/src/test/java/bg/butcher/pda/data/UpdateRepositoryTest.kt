package bg.butcher.pda.data

import bg.butcher.pda.model.GithubAsset
import bg.butcher.pda.model.GithubRelease
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var repository: UpdateRepository

    @Before
    fun setUp() {
        server.start()
        repository = UpdateRepository(
            client = OkHttpClient(),
            directory = folder.newFolder("updates"),
            installedVersionCode = 57,
            latestReleaseUrl = server.url("/repos/MLuleci/ButcherPDA/releases/latest"),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun assertUpdateFails(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("Expected UpdateException, got $error", error is UpdateException)
    }

    private fun release(vararg assets: String) = GithubRelease(
        tagName = "x",
        assets = assets.map { GithubAsset(it, "https://example.com/$it", 100) },
    )

    @Test
    fun newerThan_picksTheNewestMatchingApk() {
        val update = release("notes.txt", "ButcherPDA-58-1b641ad.apk", "ButcherPDA-60-db0f460.apk").newerThan(57)

        assertEquals(60, update?.versionCode)
        assertEquals("db0f460", update?.versionName)
        assertEquals("https://example.com/ButcherPDA-60-db0f460.apk", update?.downloadUrl)
    }

    @Test
    fun newerThan_isNullForTheSameOrAnOlderVersion() {
        assertNull(release("ButcherPDA-57-ec2604a.apk").newerThan(57))
        assertNull(release("ButcherPDA-56-ec2604a.apk").newerThan(57))
    }

    @Test
    fun newerThan_ignoresOtherNames() {
        val names = listOf(
            "app-release.apk",
            "ButcherPDA-58.apk",
            "ButcherPDA-x-1b641ad.apk",
            "ButcherPDA-58-1B641AD.apk",
            "ButcherPDA-58-1b641ad.apk.sha256",
        )
        assertNull(release(*names.toTypedArray()).newerThan(0))
    }

    @Test
    fun checkForUpdate_readsTheLatestRelease() = runTest {
        server.enqueue(
            MockResponse(
                body = """
                    {"tag_name": "db0f460", "name": "Release", "draft": false,
                     "assets": [{"name": "ButcherPDA-60-db0f460.apk", "size": 1234, "content_type": "x",
                                 "browser_download_url": "https://github.com/x/ButcherPDA-60-db0f460.apk"}]}
                """.trimIndent()
            )
        )

        val update = repository.checkForUpdate()

        assertEquals(AvailableUpdate(60, "db0f460", "https://github.com/x/ButcherPDA-60-db0f460.apk", 1234), update)
        val request = server.takeRequest()
        assertEquals("/repos/MLuleci/ButcherPDA/releases/latest", request.url.encodedPath)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
    }

    @Test
    fun checkForUpdate_isNullBeforeTheFirstRelease() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"message": "Not Found"}"""))

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun checkForUpdate_failsOnOtherErrorsAndOddBodies() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"message": "API rate limit exceeded"}"""))
        server.enqueue(MockResponse(body = "<html>not json</html>"))

        assertUpdateFails { repository.checkForUpdate() }
        assertUpdateFails { repository.checkForUpdate() }
    }

    @Test
    fun download_writesTheApkAndReplacesOlderOnes() = runTest {
        val directory = folder.root.resolve("updates")
        directory.resolve("ButcherPDA-58.apk").writeText("old")
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
        val update = AvailableUpdate(60, "db0f460", server.url("/asset").toString(), bytes.size.toLong())
        var lastProgress = 0f

        val apk = repository.download(update) { lastProgress = it }

        assertEquals("ButcherPDA-60.apk", apk.name)
        assertArrayEquals(bytes, apk.readBytes())
        assertEquals(1f, lastProgress)
        assertFalse(directory.resolve("ButcherPDA-58.apk").exists())
    }

    @Test
    fun download_failsWhenTheFileIsShort() = runTest {
        server.enqueue(MockResponse(body = "short"))
        val update = AvailableUpdate(60, "db0f460", server.url("/asset").toString(), 1000)

        assertUpdateFails { repository.download(update) }
        assertTrue(folder.root.resolve("updates").listFiles().orEmpty().isEmpty())
    }
}
