package io.github.nytka_app.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.core.api.DayEvent
import io.github.nytka_app.ui.conversations.Formatting
import java.time.ZoneId

/** A day event as one muted line: when, then what. One with a person opens that person; the others are only text. */
@Composable
internal fun EventLine(
    event: DayEvent,
    zone: ZoneId,
    onOpenPerson: (String) -> Unit,
) {
    val personId = event.personId
    val tap = if (personId != null) Modifier.clickable { onOpenPerson(personId) } else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (personId != null) 48.dp else 32.dp)
            .then(tap)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Formatting.parse(event.at)?.let { Formatting.clock(it, zone) }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            eventText(event),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The plain wording of a kind, and the one that takes the person or category after it ("Media started · video"). */
private data class Wording(
    @StringRes val plain: Int,
    @StringRes val with: Int?,
)

private val WORDINGS =
    mapOf(
        DayEvent.PERSON_JOINED to
            Wording(
                R.string.timeline_event_joined_someone,
                R.string.timeline_event_joined_format,
            ),
        DayEvent.PERSON_LEFT to Wording(R.string.timeline_event_left_someone, R.string.timeline_event_left_format),
        DayEvent.MEDIA_STARTED to
            Wording(R.string.timeline_event_media_started, R.string.timeline_event_media_started_format),
        DayEvent.MEDIA_STOPPED to
            Wording(R.string.timeline_event_media_stopped, R.string.timeline_event_media_stopped_format),
        DayEvent.CALL_STARTED to
            Wording(R.string.timeline_event_call_started, R.string.timeline_event_call_started_format),
        DayEvent.CALL_ENDED to Wording(R.string.timeline_event_call_ended, R.string.timeline_event_call_ended_format),
        DayEvent.ALONE to Wording(R.string.timeline_event_alone, null),
    )

@Composable
private fun eventText(event: DayEvent): String {
    if (event.kind == DayEvent.SILENCE) {
        return event.minutes?.let { stringResource(R.string.timeline_event_silence_format, it) }
            ?: stringResource(R.string.timeline_event_silence)
    }
    val wording = WORDINGS[event.kind] ?: return ""
    val extra =
        when (event.kind) {
            DayEvent.PERSON_JOINED, DayEvent.PERSON_LEFT -> event.voiceLabel?.takeIf { it.isNotBlank() }
            else -> event.category
        }
    return if (extra != null &&
        wording.with != null
    ) {
        stringResource(wording.with, extra)
    } else {
        stringResource(wording.plain)
    }
}
