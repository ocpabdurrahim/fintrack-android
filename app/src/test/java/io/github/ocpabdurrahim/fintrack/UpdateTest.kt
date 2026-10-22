package io.github.ocpabdurrahim.fintrack

import io.github.ocpabdurrahim.fintrack.update.ReleaseParser
import io.github.ocpabdurrahim.fintrack.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Versions.compare("1.10.0", "1.9.9") > 0)
        assertTrue(Versions.compare("v1.1.1", "1.1.0") > 0)
        assertEquals(0, Versions.compare("1.1.0", "1.1.0-debug"))
        assertEquals(0, Versions.compare("1.1", "1.1.0"))
        assertTrue(Versions.compare("1.0.9", "1.1.0") < 0)
        assertTrue(Versions.compare("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun readsTheApkFromGithubsLatestRelease() {
        val json = """
            {"tag_name": "v1.2.0", "name": "FinTrack v1.2.0", "body": "Faster sync",
             "assets": [
               {"name": "notes.txt", "size": 10, "browser_download_url": "https://example.test/notes.txt"},
               {"name": "FinTrack-1.2.0.apk", "size": 3926880,
                "digest": "sha256:ABCDEF0123",
                "browser_download_url": "https://github.com/ocpabdurrahim/fintrack-android/releases/download/v1.2.0/FinTrack-1.2.0.apk"}
             ]}
        """.trimIndent()
        val release = ReleaseParser.parse(json)!!
        assertEquals("1.2.0", release.version)
        assertEquals(3926880L, release.size)
        assertEquals("abcdef0123", release.sha256)
        assertTrue(release.apkUrl.endsWith("/FinTrack-1.2.0.apk"))
        assertEquals("Faster sync", release.notes)
    }

    @Test
    fun aReleaseWithoutAnApkOrDigestIsHandled() {
        assertNull(ReleaseParser.parse("""{"tag_name": "v1.2.0", "assets": []}"""))
        assertNull(ReleaseParser.parse("""{"assets": []}"""))
        val noDigest = ReleaseParser.parse(
            """{"tag_name": "v1.2.0", "assets": [{"name": "a.apk", "browser_download_url": "https://x.test/a.apk"}]}"""
        )!!
        assertNull(noDigest.sha256)
        assertEquals(-1L, noDigest.size)
    }
}
