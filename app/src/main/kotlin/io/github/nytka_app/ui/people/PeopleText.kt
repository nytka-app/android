package io.github.nytka_app.ui.people

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.UnnamedVoice
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@ReadOnlyComposable
internal fun linesText(count: Int) = pluralStringResource(R.plurals.people_line_count, count, count)

@Composable
@ReadOnlyComposable
internal fun personLine(
    person: Person,
    hasSummaries: Boolean,
): String {
    if (!hasSummaries) return linesText(person.segments)
    val parts =
        listOfNotNull(
            lastSeenText(person.lastSeenAt)?.let { stringResource(R.string.people_last_heard_format, it) },
            person.facts?.let { pluralStringResource(R.plurals.people_fact_count, it, it) },
        )
    return parts.ifEmpty { listOf(stringResource(R.string.people_never_heard)) }.joinToString(" · ")
}

@Composable
@ReadOnlyComposable
internal fun voiceLine(voice: UnnamedVoice): String {
    val heard = lastSeenText(voice.lastSeenAt.ifEmpty { null }) ?: stringResource(R.string.people_last_heard_unknown)
    return stringResource(R.string.unnamed_voice_line_format, linesText(voice.segments), heard)
}

@Composable
@ReadOnlyComposable
internal fun lastSeenText(text: String?): String? {
    val zone = ZoneId.systemDefault()
    val seen = LastSeen.of(text, LocalDate.now(zone), zone) ?: return null
    return when (seen) {
        is LastSeen.Today ->
            stringResource(R.string.last_seen_today_format, DateTimeFormatter.ofPattern("HH:mm").format(seen.time))

        LastSeen.Yesterday -> stringResource(R.string.last_seen_yesterday)
        is LastSeen.On -> DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]).format(seen.date)
    }
}

internal fun noticeText(
    context: Context,
    notice: PeopleNotice,
): String =
    when (notice) {
        is PeopleNotice.Renamed -> context.getString(R.string.people_renamed_format, notice.name)
        is PeopleNotice.VoiceNamed -> context.getString(R.string.people_voice_named_format, notice.name)
        is PeopleNotice.Merged -> context.getString(R.string.people_merged_format, notice.from, notice.into)
        is PeopleNotice.Deleted -> context.getString(R.string.people_deleted_format, notice.name)
        is PeopleNotice.DeletedWithVoiceModel ->
            context.getString(R.string.people_deleted_voice_model_format, notice.name)

        is PeopleNotice.DeletedVoiceModelStays ->
            context.getString(R.string.people_deleted_voice_model_stays_format, notice.name)

        is PeopleNotice.NameTaken -> context.getString(R.string.people_name_taken_format, notice.name)
        is PeopleNotice.Failed -> failureText(context, notice)
    }

private fun failureText(
    context: Context,
    failure: PeopleNotice.Failed,
): String =
    when {
        failure.kind == FailureKind.NotFound && failure.item -> context.getString(R.string.item_no_longer_exists)
        failure.kind == FailureKind.NotFound || failure.kind == FailureKind.Unsupported ->
            context.getString(R.string.server_needs_update)

        failure.kind == FailureKind.Forbidden -> context.getString(R.string.app_needs_admin_token)
        else -> failure.message
    }

@Composable
@ReadOnlyComposable
internal fun voiceTitle(voice: UnnamedVoice): String =
    voice.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.people_unknown_voice)
