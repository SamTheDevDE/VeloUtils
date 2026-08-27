package de.samthedev.veloutils.common

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DurationParserTest {
    @Test fun `parses compound durations`() {
        assertEquals(Duration.ofSeconds(788_645), DurationParser.parse("1w 2d 3h 4m 5s"))
    }

    @Test fun `rejects malformed and zero durations`() {
        assertFailsWith<IllegalArgumentException> { DurationParser.parse("1hour") }
        assertFailsWith<IllegalArgumentException> { DurationParser.parse("0s") }
        assertFailsWith<IllegalArgumentException> { DurationParser.parse("1h-2m") }
    }

    @Test fun `formats durations deterministically`() {
        assertEquals("1d 2h 3m 4s", DurationParser.format(Duration.ofSeconds(93_784)))
    }
}
