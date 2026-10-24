package io.github.nytka_app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MomentUriTest {
    private val id = "0199f1c2-3a4b-7c8d-9e0f-112233445566"

    @Test
    fun `a calendar link opens the conversation at the instant and on the line`() {
        assertEquals(
            Moment(id, Instant.parse("2026-10-23T08:42:00Z").toEpochMilli(), 48121),
            MomentUri.parse("nytka://conversation/$id?at=2026-10-23T08%3A42%3A00Z&segment=48121"),
        )
    }

    @Test
    fun `an unencoded instant, epoch milliseconds, or no moment at all`() {
        assertEquals(1_792_744_920_000, MomentUri.parse("nytka://conversation/$id?at=2026-10-23T08:42:00Z")?.atMs)
        assertEquals(1_792_744_920_000, MomentUri.parse("nytka://conversation/$id?at=1792744920000")?.atMs)
        assertEquals(Moment(id), MomentUri.parse("nytka://conversation/$id"))
    }

    @Test
    fun `an unreadable moment still opens the conversation`() {
        assertEquals(Moment(id), MomentUri.parse("nytka://conversation/$id?at=soon&segment=x"))
    }

    @Test
    fun `anything but a plain conversation id is refused`() {
        assertNull(MomentUri.parse("https://conversation/$id"))
        assertNull(MomentUri.parse("nytka://person/$id"))
        assertNull(MomentUri.parse("nytka://conversation/"))
        assertNull(MomentUri.parse("nytka://conversation/a/b"))
        assertNull(MomentUri.parse("nytka://conversation/x%3Fat%3D1"))
        assertNull(MomentUri.parse(null))
        assertNull(MomentUri.parse("not a uri"))
    }
}
