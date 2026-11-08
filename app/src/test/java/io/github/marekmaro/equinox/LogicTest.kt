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
}
