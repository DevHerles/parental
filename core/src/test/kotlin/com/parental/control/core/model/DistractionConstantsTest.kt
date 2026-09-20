package com.parental.control.core.model

import org.junit.Assert.*
import org.junit.Test

class DistractionConstantsTest {

    @Test
    fun `default blocked packages contains major distractions`() {
        val blocked = DistractionConstants.DEFAULT_BLOCKED_PACKAGES

        // TikTok
        assertTrue(blocked.contains(DistractionConstants.PKG_TIKTOK))
        assertTrue(blocked.contains(DistractionConstants.PKG_TIKTOK_TRIL))

        // YouTube
        assertTrue(blocked.contains(DistractionConstants.PKG_YOUTUBE))

        // Facebook & Instagram
        assertTrue(blocked.contains(DistractionConstants.PKG_FACEBOOK))
        assertTrue(blocked.contains(DistractionConstants.PKG_INSTAGRAM))

        // Games
        assertTrue(blocked.contains(DistractionConstants.PKG_ROBLOX))
    }

    @Test
    fun `default blocked domains covers major web mirrors`() {
        val domains = DistractionConstants.DEFAULT_BLOCKED_DOMAINS

        assertTrue(domains.contains("tiktok.com"))
        assertTrue(domains.contains("youtube.com"))
        assertTrue(domains.contains("facebook.com"))
        assertTrue(domains.contains("instagram.com"))
        assertTrue(domains.contains("roblox.com"))
    }

    @Test
    fun `getFriendlyAppName returns human readable names`() {
        assertEquals("TikTok", DistractionConstants.getFriendlyAppName(DistractionConstants.PKG_TIKTOK))
        assertEquals("YouTube", DistractionConstants.getFriendlyAppName(DistractionConstants.PKG_YOUTUBE))
        assertEquals("Facebook", DistractionConstants.getFriendlyAppName(DistractionConstants.PKG_FACEBOOK))
        assertEquals("Instagram", DistractionConstants.getFriendlyAppName(DistractionConstants.PKG_INSTAGRAM))
        assertEquals("Roblox", DistractionConstants.getFriendlyAppName(DistractionConstants.PKG_ROBLOX))
    }
}
