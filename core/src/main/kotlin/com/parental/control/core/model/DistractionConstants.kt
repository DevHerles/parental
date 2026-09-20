package com.parental.control.core.model

/**
 * Constantes y paquetes predeterminados de aplicaciones distractoras, navegadores y ajustes del sistema.
 */
object DistractionConstants {

    // Redes Sociales y Videos
    const val PKG_TIKTOK = "com.zhiliaoapp.musically"
    const val PKG_TIKTOK_TRIL = "com.ss.android.ugc.trill"
    const val PKG_TIKTOK_LITE = "com.zhiliaoapp.musically.go"

    const val PKG_YOUTUBE = "com.google.android.youtube"
    const val PKG_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    const val PKG_FACEBOOK = "com.facebook.katana"
    const val PKG_FACEBOOK_MESSENGER = "com.facebook.orca"
    const val PKG_FACEBOOK_LITE = "com.facebook.lite"

    const val PKG_INSTAGRAM = "com.instagram.android"
    const val PKG_INSTAGRAM_LITE = "com.instagram.lite"

    const val PKG_SNAPCHAT = "com.snapchat.android"
    const val PKG_ROBLOX = "com.roblox.client"
    const val PKG_TWITCH = "tv.twitch.android.app"
    const val PKG_NETFLIX = "com.netflix.mediaclient"
    const val PKG_TWITTER_X = "com.twitter.android"
    const val PKG_DISCORD = "com.discord"

    // Conjunto de paquetes bloqueados por defecto para una niña de 11 años
    val DEFAULT_BLOCKED_PACKAGES = setOf(
        PKG_TIKTOK,
        PKG_TIKTOK_TRIL,
        PKG_TIKTOK_LITE,
        PKG_YOUTUBE,
        PKG_YOUTUBE_MUSIC,
        PKG_FACEBOOK,
        PKG_FACEBOOK_MESSENGER,
        PKG_FACEBOOK_LITE,
        PKG_INSTAGRAM,
        PKG_INSTAGRAM_LITE,
        PKG_SNAPCHAT,
        PKG_ROBLOX,
        PKG_TWITCH,
        PKG_NETFLIX,
        PKG_TWITTER_X,
        PKG_DISCORD
    )

    // Paquetes críticos del sistema que se protegen contra desinstalación y manipulación
    val CRITICAL_SYSTEM_SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.app.telephonyui",
        "com.miui.securitycenter",
        "com.zui.safecenter",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.iqoo.secure",
        "com.vivo.permissionmanager",
        "com.huawei.systemmanager"
    )

    // Clases específicas dentro de Ajustes que intentan desinstalar, alterar accesibilidad o borrar datos
    val SETTINGS_TAMPER_TARGETS = listOf(
        "uninstall",
        "clear_data",
        "delete",
        "installedapp",
        "appdetail",
        "manageapp",
        "application",
        "appinfo",
        "accessibility",
        "deviceadmin",
        "device_admin",
        "specialaccess",
        "manage_applications",
        "spaactivity",
        "appbuttons",
        "appsinfo",
        "appdetails",
        "runningapplications",
        "usageaccess"
    )

    /**
     * Comprueba si un paquete corresponde a TikTok en cualquiera de sus variantes globales o regionales.
     */
    fun isTikTokPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p.contains("musically") || p.contains("tiktok") || p.contains("trill") || p.contains("aweme")
    }

    /**
     * Comprueba si un paquete corresponde a YouTube o YouTube Music.
     */
    fun isYouTubePackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p.contains("youtube") && !p.contains("kids")
    }

    /**
     * Comprueba si un paquete corresponde a Facebook o Messenger.
     */
    fun isFacebookPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p.contains("facebook") || p.contains("katana") || p.contains("orca")
    }

    /**
     * Comprueba si un paquete corresponde a Instagram.
     */
    fun isInstagramPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p.contains("instagram")
    }

    // Paquetes de Navegadores Web comunes para inspección de URL
    val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.duckduckgo.mobile.android"
    )

    // Dominios web que deben bloquearse para evitar evadir la app desde el navegador
    val DEFAULT_BLOCKED_DOMAINS = setOf(
        "tiktok.com",
        "youtube.com",
        "youtu.be",
        "facebook.com",
        "fb.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "roblox.com",
        "twitch.tv",
        "netflix.com"
    )

    /**
     * Devuelve una descripción legible por humanos de una app conocida.
     */
    fun getFriendlyAppName(packageName: String): String {
        return when {
            packageName.contains("musically") || packageName.contains("trill") -> "TikTok"
            packageName.contains("youtube") -> "YouTube"
            packageName.contains("facebook") -> "Facebook"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("snapchat") -> "Snapchat"
            packageName.contains("roblox") -> "Roblox"
            packageName.contains("twitch") -> "Twitch"
            packageName.contains("netflix") -> "Netflix"
            packageName.contains("twitter") -> "X / Twitter"
            packageName.contains("discord") -> "Discord"
            packageName.contains("settings") -> "Ajustes del Sistema"
            else -> packageName
        }
    }
}
