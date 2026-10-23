package io.github.nytka_app.core.context

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCategoryTest {
    @Test
    fun `known packages map to their category and the rest to other`() {
        assertEquals(MediaCategory.VIDEO, MediaCategory.of("com.google.android.youtube"))
        assertEquals(MediaCategory.MUSIC, MediaCategory.of("com.spotify.music"))
        assertEquals(MediaCategory.PODCAST, MediaCategory.of("de.danoeh.antennapod"))
        assertEquals(MediaCategory.MESSENGER, MediaCategory.of("org.telegram.messenger"))
        assertEquals(MediaCategory.BROWSER, MediaCategory.of("com.android.chrome"))
        assertEquals(MediaCategory.OTHER, MediaCategory.of("com.example.player"))
    }
}
