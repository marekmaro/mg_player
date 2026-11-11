package io.github.marekmaro.equinox

import io.github.marekmaro.equinox.data.FactsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class LogicTest {

    @Test
    fun fmt_formatsMinutesAndHours() {
        assertEquals("00:00", fmt(0))
        assertEquals("00:00", fmt(-5_000))
        assertEquals("01:05", fmt(65_000))
        assertEquals("59:59", fmt(3_599_000))
        assertEquals("1:01:01", fmt(3_661_000))
    }

    private val songs = listOf(
        183_000L, 241_000, 205_000, 312_000, 154_000, 219_000, 197_000,
        263_000, 228_000, 176_000, 289_000, 201_000, 245_000, 168_000,
    )

    @Test
    fun stopwatch_fitsTargetClosely() {
        for (minutes in listOf(10, 25, 45)) {
            val target = minutes * 60_000L
            repeat(20) { seed ->
                val idx = pickForDuration(songs, target, Random(seed))
                val total = idx.sumOf { songs[it] }
                assertTrue("za długo ($minutes min, seed $seed): $total", total <= target + 20_000)
                assertTrue("za krótko ($minutes min, seed $seed): $total", target - total < 120_000)
                assertEquals("powtórzony utwór", idx.size, idx.toSet().size)
            }
        }
    }

    @Test
    fun stopwatch_emptyWhenNothingFits() {
        assertTrue(pickForDuration(listOf(10 * 60_000L), 5 * 60_000L).isEmpty())
        assertTrue(pickForDuration(emptyList(), 25 * 60_000L).isEmpty())
        // utwory krótsze niż minuta są pomijane
        assertTrue(pickForDuration(listOf(30_000L, 40_000L), 5 * 60_000L).isEmpty())
    }

    @Test
    fun primaryName_takesFirstArtist() {
        assertEquals("Daft Punk", FactsRepository.primaryName("Daft Punk feat. Pharrell Williams"))
        assertEquals("Kult", FactsRepository.primaryName("Kult"))
        assertEquals("Sanah", FactsRepository.primaryName("Sanah & Vito Bambino"))
        assertEquals("Tede", FactsRepository.primaryName("Tede, Sir Michu"))
        assertEquals("Queen", FactsRepository.primaryName("Queen ft. David Bowie"))
    }

    @Test
    fun splitSentences_keepsMeaningfulSentences() {
        val s = FactsRepository.splitSentences(
            "Kult – polski zespół rockowy założony w 1982 roku w Warszawie. " +
                "Liderem grupy od początku jest Kazik Staszewski. Krótko."
        )
        assertEquals(2, s.size)
        assertTrue(s[0].startsWith("Kult"))
    }

    @Test
    fun chimes_renderAtSafeVolume() {
        Chime.entries.forEach { c ->
            val pcm = c.render()
            assertEquals((c.durationMs * 44_100 / 1000).toInt(), pcm.size)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("${c.name}: peak $peak", peak in 20_000..32_767)
            // koniec sygnału wygaszony (bez trzasku)
            val tail = pcm.takeLast(200).maxOf { abs(it.toInt()) }
            assertTrue("${c.name}: tail $tail", tail < 3_000)
            val end = pcm.takeLast(20).maxOf { abs(it.toInt()) }
            assertTrue("${c.name}: end $end", end < 1_000)
        }
    }

    @Test
    fun blocks_layoutRoundTripAndRepair() {
        assertEquals(Block.defaultLayout, Block.parse(null))
        assertEquals(Block.defaultLayout, Block.parse(""))
        val custom = listOf(Block.CLOCK, Block.TITLE, Block.QUEUE)
        assertEquals(custom, Block.parse(Block.serialize(custom)))
        // nieznane nazwy (np. ze starszej wersji) są pomijane, wymagany tytuł zawsze wraca
        val repaired = Block.parse("CLOCK,NIEISTNIEJACY,QUEUE")
        assertTrue(Block.TITLE in repaired)
        assertTrue(Block.CLOCK in repaired && Block.QUEUE in repaired)
        assertEquals(repaired.size, repaired.toSet().size)
    }

    @Test
    fun folderRules_longestPathWins() {
        val rules = mapOf(
            "/sdcard/Music" to io.github.marekmaro.equinox.data.FolderRule.SKIP,
            "/sdcard/Music/Dobre" to io.github.marekmaro.equinox.data.FolderRule.MUSIC,
        )
        val L = io.github.marekmaro.equinox.data.Library
        assertEquals(io.github.marekmaro.equinox.data.FolderRule.SKIP, L.ruleFor("/sdcard/Music/Stare", rules))
        assertEquals(io.github.marekmaro.equinox.data.FolderRule.MUSIC, L.ruleFor("/sdcard/Music/Dobre/Album", rules))
        // „Music2” to inny folder niż „Music”
        assertEquals(io.github.marekmaro.equinox.data.FolderRule.AUTO, L.ruleFor("/sdcard/Music2", rules))
    }

    @Test
    fun radio_parsesPlsAndM3u() {
        val R = io.github.marekmaro.equinox.data.RadioRepository
        assertEquals("http://stream.example.com/live", R.parsePlaylist("[playlist]\nNumberOfEntries=1\nFile1=http://stream.example.com/live\nTitle1=X"))
        assertEquals("https://a.example/s.mp3", R.parsePlaylist("#EXTM3U\n#EXTINF:-1,Radio\nhttps://a.example/s.mp3\n"))
        assertEquals(null, R.parsePlaylist("brak adresu"))
        assertTrue(R.isPlaylistLink("http://x/listen.pls?id=3"))
        assertTrue(!R.isPlaylistLink("http://x/stream.mp3"))
    }

    @Test
    fun letterBubble_firstLetter() {
        val f: (String) -> String = { io.github.marekmaro.equinox.ui.firstLetter(it) }
        assertEquals("Ś", f("świt nad miastem"))
        assertEquals("#", f("2 + 1"))
        assertEquals("#", f(""))
        assertEquals("A", f("  ale"))
    }

    // ---------- rozdziały M4B (znacznik chpl) ----------

    private fun chplPayload(chapters: List<Pair<Long, String>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)
        d.writeInt(0x01000000) // wersja 1, flagi 0 (tak zapisuje ffmpeg)
        d.writeInt(0)          // zarezerwowane
        d.writeByte(chapters.size)
        chapters.forEach { (ms, title) ->
            d.writeLong(ms * 10_000) // jednostki 100 ns
            val t = title.toByteArray(Charsets.UTF_8)
            d.writeByte(t.size)
            d.write(t)
        }
        return out.toByteArray()
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)
        d.writeInt(8 + payload.size)
        d.write(type.toByteArray(Charsets.ISO_8859_1))
        d.write(payload)
        return out.toByteArray()
    }

    @Test
    fun chapters_parseChplPayload() {
        val ch = io.github.marekmaro.equinox.data.Chapters.parseChpl(
            chplPayload(listOf(0L to "Wstęp", 95_000L to "Księga pierwsza", 1_800_000L to "Księga druga"))
        )
        assertEquals(3, ch.size)
        assertEquals("Księga pierwsza", ch[1].title)
        assertEquals(95_000L, ch[1].startMs)
        assertEquals(1, io.github.marekmaro.equinox.data.Chapters.indexAt(ch, 100_000))
        assertEquals(0, io.github.marekmaro.equinox.data.Chapters.indexAt(ch, 10))
    }

    @Test
    fun chapters_findInMp4Structure() {
        // ftyp, potem moov → (mvhd, udta → chpl), na końcu mdat z „dźwiękiem”
        val chpl = box("chpl", chplPayload(listOf(0L to "Jeden", 60_000L to "Dwa")))
        val udta = box("udta", chpl)
        val moov = box("moov", box("mvhd", ByteArray(100)) + udta)
        val file = box("ftyp", "M4B ".toByteArray()) + moov + box("mdat", ByteArray(5000))
        val tmp = java.io.File.createTempFile("test", ".m4b")
        tmp.writeBytes(file)
        val ch = java.io.RandomAccessFile(tmp, "r").channel.use { io.github.marekmaro.equinox.data.Chapters.parse(it) }
        tmp.delete()
        assertEquals(listOf("Jeden", "Dwa"), ch.map { it.title })
        assertEquals(60_000L, ch[1].startMs)
    }

    @Test
    fun chapters_ignoreBrokenData() {
        val C = io.github.marekmaro.equinox.data.Chapters
        assertTrue(C.parseChpl(ByteArray(0)).isEmpty())
        assertTrue(C.parseChpl(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0)).isEmpty()) // obcięte
        val tmp = java.io.File.createTempFile("bad", ".mp3")
        tmp.writeBytes(ByteArray(3000) { (it % 251).toByte() })
        val ch = java.io.RandomAccessFile(tmp, "r").channel.use { C.parse(it) }
        tmp.delete()
        assertTrue(ch.isEmpty())
    }

    // ---------- duże biblioteki ----------

    @Test
    fun largeLibrary_isFast() {
        val rnd = Random(7)
        val durations = List(20_000) { 60_000L + rnd.nextLong(300_000) }
        val t0 = System.nanoTime()
        val idx = pickForDuration(durations, 45 * 60_000L, rnd)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("stoper dla 20 000 utworów trwał $ms ms", ms < 1_500)
        assertTrue(idx.isNotEmpty())

        val rules = (0 until 300).associate { "/sdcard/Music/Folder$it" to io.github.marekmaro.equinox.data.FolderRule.MUSIC }
        val t1 = System.nanoTime()
        repeat(20_000) { io.github.marekmaro.equinox.data.Library.ruleFor("/sdcard/Music/Folder${it % 400}/Album", rules) }
        val ms2 = (System.nanoTime() - t1) / 1_000_000
        assertTrue("reguły folderów dla 20 000 plików trwały $ms2 ms", ms2 < 3_000)
    }

    @Test
    fun sound_presetsInterpolate() {
        SoundPreset.entries.forEach { p ->
            SoundPreset.referenceHz.forEachIndexed { i, hz ->
                assertEquals("${p.name} @ $hz", p.gainsDb[i].toFloat(), SoundPreset.gainAt(p, hz), 0.01f)
            }
            // poza zakresem: wartości skrajne, bez szalonych wzmocnień
            assertEquals(p.gainsDb.first().toFloat(), SoundPreset.gainAt(p, 20), 0.01f)
            assertEquals(p.gainsDb.last().toFloat(), SoundPreset.gainAt(p, 20_000), 0.01f)
            assertTrue(p.gainsDb.all { it in -6..6 })
        }
        // między 60 a 230 Hz wartość leży między sąsiednimi pasmami
        val g = SoundPreset.gainAt(SoundPreset.BASS, 120)
        assertTrue(g in 4f..6f)
    }

    @Test
    fun language_switches() {
        val before = Lang.mode
        Lang.mode = LangMode.PL
        assertEquals("Muzyka", tr("Muzyka", "Music"))
        assertEquals("Przewiń", RingMode.SEEK.label)
        Lang.mode = LangMode.EN
        assertEquals("Music", tr("Muzyka", "Music"))
        assertEquals("Seek", RingMode.SEEK.label)
        assertEquals("Up next", Block.QUEUE.label)
        assertEquals("8-bit fanfare", Chime.CHIP.label)
        Lang.mode = before
    }
}
