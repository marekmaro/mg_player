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

    private fun ints(vararg v: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)
        v.forEach { d.writeInt(it.toInt()) }
        return out.toByteArray()
    }

    private fun quickTimeTrak(id: Long, handler: String, timescale: Long, extra: ByteArray = ByteArray(0), stbl: ByteArray = ByteArray(0)): ByteArray {
        val tkhd = box("tkhd", ints(0, 0, 0, id, 0, 0))       // wersja 0: … track_id na pozycji 12
        val mdhd = box("mdhd", ints(0, 0, 0, timescale, 0, 0)) // timescale na pozycji 12
        val hdlr = box("hdlr", ints(0, 0) + handler.toByteArray(Charsets.ISO_8859_1) + ints(0, 0, 0))
        val minf = box("minf", box("stbl", stbl))
        return box("trak", tkhd + extra + box("mdia", mdhd + hdlr + minf))
    }

    @Test
    fun chapters_appleChapterTrack() {
        // Plik jak z iTunes: brak „chpl”, za to ścieżka tekstowa (id 2) wskazana z dźwięku przez tref/chap.
        fun sample(t: String, utf16: Boolean = false): ByteArray {
            val body = if (utf16) byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + t.toByteArray(Charsets.UTF_16BE) else t.toByteArray(Charsets.UTF_8)
            return byteArrayOf((body.size shr 8).toByte(), body.size.toByte()) + body
        }
        val samples = listOf(sample("Prolog"), sample("Część pierwsza"), sample("Epilog", utf16 = true))
        val ftyp = box("ftyp", "M4B ".toByteArray())
        // dwa kawałki: [0,1] i [2] – sprawdza stsc
        fun moovFor(mdatStart: Long): ByteArray {
            val off1 = mdatStart + 8
            val off2 = off1 + samples[0].size + samples[1].size
            val stbl = box("stts", ints(0, 3, 1, 60_000, 1, 1_200_000, 1, 100_000)) + // timescale 1000: 0 s, 60 s, 1260 s
                box("stsc", ints(0, 2, 1, 2, 1, 2, 1, 1)) +
                box("stsz", ints(0, 0, 3, samples[0].size.toLong(), samples[1].size.toLong(), samples[2].size.toLong())) +
                box("stco", ints(0, 2, off1, off2))
            val audio = quickTimeTrak(1, "soun", 44_100, extra = box("tref", box("chap", ints(2))))
            val text = quickTimeTrak(2, "text", 1_000, stbl = stbl)
            return box("moov", box("mvhd", ByteArray(100)) + audio + text)
        }
        val moovLen = moovFor(0).size
        val moov = moovFor((ftyp.size + moovLen).toLong())
        val file = ftyp + moov + box("mdat", samples.reduce { a, b -> a + b } + ByteArray(2000))
        val tmp = java.io.File.createTempFile("apple", ".m4b")
        tmp.writeBytes(file)
        val ch = java.io.RandomAccessFile(tmp, "r").channel.use { io.github.marekmaro.equinox.data.Chapters.parse(it) }
        tmp.delete()
        assertEquals(listOf("Prolog", "Część pierwsza", "Epilog"), ch.map { it.title })
        assertEquals(listOf(0L, 60_000L, 1_260_000L), ch.map { it.startMs })
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

    // ---------- płynne przejścia ----------

    @Test
    fun crossfade_gain() {
        val cf = 6_000L
        val dur = 200_000L
        // środek utworu – pełna głośność
        assertEquals(1f, fadeGainFor(Kind.MUSIC, cf, 100_000, dur, hasNext = true, fadeIn = true), 0.001f)
        // 3 s przed końcem – połowa
        assertEquals(0.5f, fadeGainFor(Kind.MUSIC, cf, dur - 3_000, dur, hasNext = true, fadeIn = false), 0.001f)
        // ostatni utwór w kolejce nie cichnie
        assertEquals(1f, fadeGainFor(Kind.MUSIC, cf, dur - 1_000, dur, hasNext = false, fadeIn = false), 0.001f)
        // wejście: 1,5 s z 3 s
        assertEquals(0.5f, fadeGainFor(Kind.MUSIC, cf, 1_500, dur, hasNext = true, fadeIn = true), 0.001f)
        // audiobook, radio, wyłączone, krótki utwór – bez zmian
        assertEquals(1f, fadeGainFor(Kind.BOOK, cf, dur - 1_000, dur, true, true), 0.001f)
        assertEquals(1f, fadeGainFor(Kind.RADIO, cf, 0, 0, true, true), 0.001f)
        assertEquals(1f, fadeGainFor(Kind.MUSIC, 0, dur - 1_000, dur, true, true), 0.001f)
        assertEquals(1f, fadeGainFor(Kind.MUSIC, cf, 10_000, 15_000, true, false), 0.001f)
    }

    // ---------- kopia zapasowa ----------

    @Test
    fun backup_roundTripRemapsTracks() {
        val B = io.github.marekmaro.equinox.data.Backup
        val old = listOf(
            io.github.marekmaro.equinox.data.TrackRef(1, "/storage/emulated/0/Music/Rock/a.mp3", "Alfa", "Zespół", 200_000),
            io.github.marekmaro.equinox.data.TrackRef(2, "/storage/emulated/0/Music/Jazz/b.mp3", "Beta", "Łódź Trio", 180_000),
            io.github.marekmaro.equinox.data.TrackRef(3, "/storage/emulated/0/Music/c.mp3", "Gamma", "Ktoś", 100_000),
        )
        val prefs = mapOf<String, Any>(
            "playlists" to """[{"id":7,"name":"Wieczór","tracks":[1,2,3]}]""",
            "track_marks" to """{"1":"HEART","2":"STAR,MOON"}""",
            "play_counts" to """{"1":5,"3":2}""",
            "recent_played" to "[3,1]",
            "theme_mode" to "NIGHT",
            "crossfade_sec" to 6,
            "book_p_Hobbit" to 123_456L,
            "book_speed" to 1.25f,
            "show_stats" to false,
            "last_session" to "{}",
            "facts_x" to "{}",
        )
        val json = B.export(prefs, old, "2026-11-20T10:00")
        // nowy telefon: inne id, karta SD zamiast pamięci wewnętrznej, jednego utworu brak, drugi w innym folderze
        val now = listOf(
            io.github.marekmaro.equinox.data.TrackRef(101, "/storage/1234-ABCD/Music/Rock/a.mp3", "Alfa", "Zespół", 200_000),
            io.github.marekmaro.equinox.data.TrackRef(102, "/storage/emulated/0/Download/inne.mp3", "Beta", "Lodz trio", 181_000),
        )
        val r = B.restore(json, now)
        assertEquals(2, r.tracksFound)
        assertEquals(1, r.tracksMissing)
        assertEquals("NIGHT", r.values["theme_mode"])
        assertEquals(6, r.values["crossfade_sec"])
        assertEquals(123_456L, r.values["book_p_Hobbit"])
        assertEquals(1.25f, r.values["book_speed"])
        assertEquals(false, r.values["show_stats"])
        assertTrue("last_session" !in r.values && "facts_x" !in r.values)
        val pl = org.json.JSONArray(r.values["playlists"] as String).getJSONObject(0)
        assertEquals("Wieczór", pl.getString("name"))
        assertEquals("[101,102]", pl.getJSONArray("tracks").toString())
        val marks = org.json.JSONObject(r.values["track_marks"] as String)
        assertEquals("STAR,MOON", marks.getString("102"))
        assertEquals("[101]", r.values["recent_played"])
    }

    @Test(expected = Exception::class)
    fun backup_rejectsForeignFile() {
        io.github.marekmaro.equinox.data.Backup.restore("""{"hello":1}""", emptyList())
    }

    // ---------- zakładki w audiobookach ----------

    @Test
    fun bookmarks_roundTripOrderAndDedupe() {
        val B = io.github.marekmaro.equinox.data.Bookmarks
        fun bm(id: Long, book: String, i: Int, pos: Long, note: String = "") =
            io.github.marekmaro.equinox.data.Bookmark(id, book, i, pos, "Rozdział ${i + 1}", note, id)
        var all = emptyList<io.github.marekmaro.equinox.data.Bookmark>()
        all = B.add(all, bm(1, "Lalka", 2, 60_000, "Wokulski w sklepie"))
        all = B.add(all, bm(2, "Lalka", 0, 5_000))
        all = B.add(all, bm(3, "Hobbit", 1, 1_000))
        // drugi raz prawie to samo miejsce – zastępuje, nie dubluje
        all = B.add(all, bm(4, "Lalka", 2, 61_500, "poprawiona notatka"))
        val lalka = B.forBook(all, "Lalka")
        assertEquals(listOf(2L, 4L), lalka.map { it.id })               // kolejność czytania
        assertEquals("poprawiona notatka", lalka[1].note)
        assertTrue(B.forBook(all, null).isEmpty())
        // zapis i odczyt (z polskimi znakami w notatce)
        val back = B.decode(B.encode(all))
        assertEquals(all, back)
        assertTrue(B.decode("nie json").isEmpty())
    }

    @Test
    fun bookmarks_limitPerBook() {
        val B = io.github.marekmaro.equinox.data.Bookmarks
        var all = emptyList<io.github.marekmaro.equinox.data.Bookmark>()
        repeat(B.MAX_PER_BOOK + 5) { n ->
            all = B.add(all, io.github.marekmaro.equinox.data.Bookmark(n.toLong(), "K", 0, n * 10_000L, "", "", n.toLong()))
        }
        assertEquals(B.MAX_PER_BOOK, all.size)
        assertTrue(all.none { it.id < 5 }) // najstarsze wypadły
    }

    // ---------- wyrównanie głośności ----------

    @Test
    fun replayGain_parsesAllFormats() {
        // ID3 (TXXX), Vorbis (wielkie litery), MP4 (----:com.apple.iTunes:…)
        val id3 = ReplayGain.parse(listOf("REPLAYGAIN_TRACK_GAIN" to "-6.54 dB", "REPLAYGAIN_ALBUM_GAIN" to "-5.10 dB"))
        assertEquals(-6.54f, id3.trackDb!!, 0.001f)
        assertEquals(-5.10f, id3.albumDb!!, 0.001f)
        val mp4 = ReplayGain.parse(listOf("com.apple.iTunes:replaygain_track_gain" to "+1,5 dB"))
        assertEquals(1.5f, mp4.trackDb!!, 0.001f)
        // Opus: R128 w 1/256 dB względem −23 LUFS → +5 dB do skali ReplayGain
        val opus = ReplayGain.parse(listOf("R128_TRACK_GAIN" to "-2560"))
        assertEquals(-5f, opus.trackDb!!, 0.001f)
        // śmieci i brak znaczników
        assertEquals(ReplayGainTags.NONE, ReplayGain.parse(listOf("REPLAYGAIN_TRACK_GAIN" to "głośno", "ARTIST" to "X")))
        assertEquals(null, ReplayGain.parseDb("-99 dB"))
        assertEquals(-3.1f, ReplayGain.parseDb("−3.1")!!, 0.001f)
    }

    @Test
    fun replayGain_modesAndLevels() {
        val t = ReplayGainTags(trackDb = -8f, albumDb = -6f)
        assertEquals(null, t.gainFor(ReplayGainMode.OFF, shuffled = true))
        assertEquals(-8f, t.gainFor(ReplayGainMode.AUTO, shuffled = true)!!, 0f)
        assertEquals(-6f, t.gainFor(ReplayGainMode.AUTO, shuffled = false)!!, 0f)
        assertEquals(-6f, ReplayGainTags(null, -6f).gainFor(ReplayGainMode.TRACK, false)!!, 0f) // brak utworu → album
        // −6 dB ≈ połowa amplitudy; dodatnie wzmocnienie nie zmienia głośności odtwarzacza, tylko podbija (maks. +6 dB)
        assertEquals(0.501f, ReplayGain.volumeFor(-6f), 0.001f)
        assertEquals(1f, ReplayGain.volumeFor(3f), 0f)
        assertEquals(1f, ReplayGain.volumeFor(null), 0f)
        assertEquals(300, ReplayGain.boostMillibelsFor(3f))
        assertEquals(600, ReplayGain.boostMillibelsFor(12f))
        assertEquals(0, ReplayGain.boostMillibelsFor(-4f))
    }

    // ---------- gatunki i lata ----------

    @Test
    fun genresAndYears_cleanup() {
        val T = io.github.marekmaro.equinox.data.TrackTags
        assertEquals(listOf("Rock", "Blues"), T.splitGenres("rock; Blues ;ROCK"))
        assertEquals(listOf("Hip-hop", "Drum And Bass"), T.splitGenres("hip-hop|drum and bass"))
        assertEquals(emptyList<String>(), T.splitGenres("(17)"))
        assertEquals(emptyList<String>(), T.splitGenres(null))
        assertEquals(1987, T.cleanYear(1987))
        assertEquals(2024, T.cleanYear(20240101))
        assertEquals(0, T.cleanYear(0))
        assertEquals(0, T.cleanYear(87))
        assertEquals(1980, T.decadeOf(1987))
        assertEquals(0, T.decadeOf(0))
    }

    // ---------- potrząśnij, aby przedłużyć sen ----------

    @Test
    fun shake_needsThreeDistinctJolts() {
        val strong = 30f // ≈ 3 g
        val calm = 9.8f  // telefon leży
        // jedno szarpnięcie = kilka odczytów pod rząd (50 Hz) – to jeszcze nie potrząśnięcie
        val a = ShakeLogic()
        assertTrue((0 until 5).none { a.onSample(strong, 0f, 0f, 1_000L + it * 20) })
        // spokojne leżenie nigdy
        val b = ShakeLogic()
        assertTrue((0 until 200).none { b.onSample(0f, 0f, calm, it * 20L) })
        // trzy wyraźne ruchy w niecałą sekundę – tak
        val c = ShakeLogic()
        assertTrue(!c.onSample(strong, 0f, 0f, 0))
        assertTrue(!c.onSample(-strong, 0f, 0f, 250))
        assertTrue(c.onSample(strong, 0f, 0f, 500))
        // zaraz potem przerwa – drugie potrząśnięcie liczy się dopiero po 2 s
        assertTrue(!c.onSample(strong, 0f, 0f, 800))
        assertTrue(!c.onSample(strong, 0f, 0f, 1_000))
        assertTrue(!c.onSample(strong, 0f, 0f, 1_200))
        // zbyt wolno (ruchy co sekundę) – nie
        val d = ShakeLogic()
        assertTrue(listOf(0L, 1_000L, 2_000L, 3_000L).none { d.onSample(strong, 0f, 0f, it) })
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

    // ---------- mądre losowanie ----------

    private fun library(artists: Int, perArtist: Int) = (0 until artists * perArtist).map {
        ShuffleItem(id = it.toLong(), artist = "A${it % artists}", album = "A${it % artists}-album${it % 2}", weight = 1.0)
    }

    @Test
    fun smartShuffle_neverSameArtistBackToBack() {
        val items = library(artists = 5, perArtist = 6)
        repeat(50) { seed ->
            val out = smartShuffle(items, Random(seed))
            assertEquals(items.size, out.size)
            assertEquals(items.map { it.id }.toSet(), out.map { it.id }.toSet())
            out.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed: ${a.artist} dwa razy", a.artist != b.artist) }
        }
    }

    @Test
    fun smartShuffle_oneArtistStillWorks() {
        val items = library(artists = 1, perArtist = 10)
        assertEquals(10, smartShuffle(items, Random(1)).size)
    }

    @Test
    fun smartShuffle_recentGoLast_starsGoFirst() {
        val n = 40
        var recentPos = 0.0
        var starPos = 0.0
        repeat(300) { seed ->
            val items = (0 until n).map { i ->
                val w = when (i) {
                    0 -> shuffleWeight(null, recentRank = 0)                 // zagrany przed chwilą
                    1 -> shuffleWeight(setOf(Mark.STAR, Mark.HEART), null)    // perełka
                    else -> shuffleWeight(null, null)
                }
                ShuffleItem(i.toLong(), "A$i", "", w)
            }
            val out = smartShuffle(items, Random(seed))
            recentPos += out.indexOfFirst { it.id == 0L }
            starPos += out.indexOfFirst { it.id == 1L }
        }
        assertTrue("niedawny średnio na pozycji ${recentPos / 300}", recentPos / 300 > n * 0.6)
        assertTrue("perełka średnio na pozycji ${starPos / 300}", starPos / 300 < n * 0.3)
    }

    // ---------- wyszukiwanie ----------

    @Test
    fun search_ignoresPolishLettersAndCase() {
        assertTrue(matchesQuery("Cisza przed burzą", "burza"))
        assertTrue(matchesQuery("Żółw i zając", "zolw"))
        assertTrue(matchesQuery("Łąka", "laka"))
        assertTrue(matchesQuery("Nocny przystanek — Mira Sol", "mira nocny"))
        assertTrue(!matchesQuery("Nocny przystanek", "dzienny"))
        assertEquals("♥★", marksGlyphs(setOf(Mark.STAR, Mark.HEART)))
    }
}
