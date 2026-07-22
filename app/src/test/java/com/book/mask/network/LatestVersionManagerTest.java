package com.book.mask.network;

import com.book.mask.config.Share;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LatestVersionManagerTest {
    private String originalVersion;
    private long originalTimestamp;

    @Before
    public void saveLatestVersionCache() {
        originalVersion = Share.latestVersion;
        originalTimestamp = Share.latestVersionTimestamp;
    }

    @After
    public void restoreLatestVersionCache() {
        Share.latestVersion = originalVersion;
        Share.latestVersionTimestamp = originalTimestamp;
    }

    @Test
    public void freshVersionIsReturned() {
        Share.latestVersion = "2.8.0";
        Share.latestVersionTimestamp = System.currentTimeMillis();

        assertEquals("2.8.0", LatestVersionManager.getFreshLatestVersion());
    }

    @Test
    public void versionOlderThanOneMinuteIsRejected() {
        Share.latestVersion = "2.8.0";
        Share.latestVersionTimestamp = System.currentTimeMillis() - 60_001L;

        assertNull(LatestVersionManager.getFreshLatestVersion());
    }

    @Test
    public void downloadUrlContainsLatestVersion() {
        assertEquals(
                "https://gitee.com/interest2/anti-addiction/releases/download/v2.8.0/app-release.apk",
                LatestVersionManager.buildLatestApkDownloadUrl("2.8.0")
        );
    }
}
