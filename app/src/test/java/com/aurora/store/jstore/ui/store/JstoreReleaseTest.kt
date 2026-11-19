/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JstoreReleaseTest {

    private val sha = "a".repeat(64)

    private fun releaseJson(tag: String = "v1.1.0", apk: String = "https://x/jstore.apk") = """{
        "tag_name":"$tag",
        "assets":[
          {"name":"jstore.apk","browser_download_url":"$apk"},
          {"name":"jstore.apk.sha256","browser_download_url":"https://x/jstore.apk.sha256"}
        ]}"""

    @Test
    fun parsesTagAndAssets() {
        val parsed = JstoreReleases.parse(releaseJson())!!
        assertThat(parsed.versionName).isEqualTo("1.1.0")
        assertThat(parsed.apkUrl).isEqualTo("https://x/jstore.apk")
    }

    @Test
    fun releaseWithoutShaAssetIsIncomplete() {
        assertThat(JstoreReleases.parse("""{"tag_name":"v1","assets":[]}""")).isNull()
    }

    @Test
    fun insecureApkUrlIsRejected() {
        assertThat(JstoreReleases.parse(releaseJson(apk = "http://x/jstore.apk"))).isNull()
    }

    @Test
    fun latestJoinsReleaseAndChecksum() {
        val release = JstoreReleases.latest { url ->
            if (url.endsWith(".sha256")) "$sha  jstore.apk\n" else releaseJson()
        }
        assertThat(release).isEqualTo(JstoreRelease("1.1.0", "https://x/jstore.apk", sha))
    }

    @Test
    fun badChecksumMeansNoRelease() {
        assertThat(
            JstoreReleases.latest { url ->
                if (url.endsWith(".sha256")) "nothex" else releaseJson()
            }
        ).isNull()
    }

    @Test
    fun unreachableGithubMeansNoRelease() {
        assertThat(JstoreReleases.latest { null }).isNull()
    }
}
