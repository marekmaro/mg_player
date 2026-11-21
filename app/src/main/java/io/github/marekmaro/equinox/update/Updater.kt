package io.github.marekmaro.equinox.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.marekmaro.equinox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Nowa wersja opisana w GitHub Releases. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/**
 * Czysta logika aktualizacji (bez Androida) – porównanie wersji i odczyt odpowiedzi GitHuba.
 */
object UpdateLogic {

    /** „v0.15.0” / „0.15” / „Equinox 0.15.1-beta” → [0, 15, 1]; null, gdy nie ma liczb. */
    fun parseVersion(text: String): List<Int>? {
        val m = Regex("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(text) ?: return null
        return (1..3).map { m.groupValues[it].toIntOrNull() ?: 0 }
    }

    /** Czy [candidate] jest nowsza niż [current] (porównanie liczba po liczbie). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return true
        for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    /**
     * Odpowiedź /releases/latest → [UpdateInfo], o ile wydanie jest nowsze i ma plik APK.
     * Wersje robocze (draft) i próbne (prerelease) pomijamy.
     */
    fun parseRelease(json: String, currentVersion: String): UpdateInfo? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name").ifBlank { o.optString("name") }
        if (!isNewer(tag, currentVersion)) return null
        val assets = o.optJSONArray("assets") ?: return null
        var best: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name").lowercase()
            if (!name.endsWith(".apk")) continue
            // gdyby w wydaniu było kilka plików, bierzemy ten z „github” w nazwie
            if (best == null || name.contains("github")) best = a
        }
        val apk = best ?: return null
        val url = apk.optString("browser_download_url")
        if (!url.startsWith("https://")) return null
        return UpdateInfo(
            version = parseVersion(tag)!!.let { v -> v.joinToString(".") },
            notes = o.optString("body").trim().take(4_000),
            apkUrl = url,
            apkSize = apk.optLong("size"),
            pageUrl = o.optString("html_url"),
        )
    }
}

/**
 * Aktualizacja z GitHub Releases (tylko wersja „github”, zob. [BuildConfig.SELF_UPDATE]):
 * 1. jedno zapytanie do api.github.com o najnowsze wydanie (bez danych o użytkowniku),
 * 2. pobranie pliku APK do pamięci podręcznej aplikacji,
 * 3. sprawdzenie, że to Equinox, nowsza wersja i ten sam klucz podpisu,
 * 4. instalacja przez systemowy PackageInstaller – Android zawsze pyta o potwierdzenie.
 */
object Updater {

    const val MIN_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    val enabled: Boolean get() = BuildConfig.SELF_UPDATE

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Equinox-Android")
        }
        try {
            when (c.responseCode) {
                200 -> UpdateLogic.parseRelease(c.inputStream.bufferedReader().use { it.readText() }, currentVersion)
                404 -> null // jeszcze żadnego wydania
                else -> throw java.io.IOException("HTTP ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    /** Pobiera APK; [onProgress] dostaje 0..1 (albo -1, gdy rozmiar nieznany). */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // stare pobrania
        val out = File(dir, "equinox-${info.version}.apk")
        var url = URL(info.apkUrl)
        var c: HttpURLConnection
        var hops = 0
        // GitHub przekierowuje do serwera z plikami – podążamy tylko za adresami https
        while (true) {
            c = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Equinox-Android")
            }
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) {
                val next = c.getHeaderField("Location") ?: break
                c.disconnect()
                url = URL(url, next)
                require(url.protocol == "https") { "insecure redirect" }
                continue
            }
            break
        }
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            require(total in 1..200_000_000L || total <= 0) { "unexpected size" }
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (done > 200_000_000L) throw java.io.IOException("too large")
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                withContext(Dispatchers.Main) { onProgress(pct / 100f) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            c.disconnect()
        }
        out
    }

    /** Ten sam pakiet, nowsza wersja i ten sam klucz podpisu co zainstalowana aplikacja. */
    fun verify(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName) return false
        if (versionCode(archive) <= versionCode(installed)) return false
        val a = signatureDigests(archive)
        return a.isNotEmpty() && a == signatureDigests(installed)
    }

    /** Czy system pozwala tej aplikacji instalować pakiety (Android 8+: osobna zgoda użytkownika). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Ekran systemowy „Instalowanie nieznanych aplikacji” dla Equinox. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Przekazuje plik do PackageInstaller; okno potwierdzenia pokaże [UpdateInstallReceiver]. */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("equinox.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = PendingIntent.getBroadcast(
                context, id,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
    }

    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()

    private fun signatureDigests(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = p.signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") p.signatures
        } ?: return emptySet()
        return sigs.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
