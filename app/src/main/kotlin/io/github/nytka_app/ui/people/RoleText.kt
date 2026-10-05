package io.github.nytka_app.ui.people

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R

/** "{label} may be the {role}" and its two variants; [label] is what the evidence line is called now. */
@Composable
@ReadOnlyComposable
internal fun SuggestionWording.title(label: String): String =
    when (this) {
        is SuggestionWording.Name -> stringResource(R.string.suggestion_banner_format, label, name)
        is SuggestionWording.RoleOnly -> stringResource(R.string.role_suggestion_role_format, label, role)
        is SuggestionWording.NameAndRole ->
            stringResource(R.string.role_suggestion_name_role_format, label, name, role)
    }

@Composable
@ReadOnlyComposable
internal fun SuggestionWording.acceptText(): String =
    when (this) {
        is SuggestionWording.RoleOnly -> stringResource(R.string.role_accept_format, role)
        else -> stringResource(R.string.suggestion_accept)
    }

@Composable
@ReadOnlyComposable
internal fun SuggestionWording.rejectText(): String =
    when (this) {
        is SuggestionWording.Name -> stringResource(R.string.suggestion_reject_format, name)
        is SuggestionWording.NameAndRole -> stringResource(R.string.suggestion_reject_format, name)
        is SuggestionWording.RoleOnly -> stringResource(R.string.role_reject)
    }

/** "Add name" for a person known only by a role, "Rename" otherwise. */
@Composable
@ReadOnlyComposable
internal fun renameLabel(named: Boolean): String =
    stringResource(if (named) R.string.action_rename else R.string.action_add_name)

/** "Name not known yet" under the name of a person known only by a role. */
@Composable
internal fun NameNotKnown(named: Boolean) {
    if (!named) Text(stringResource(R.string.name_not_known))
}

/** The inbox question for a name row. */
@Composable
@ReadOnlyComposable
internal fun SuggestionWording.question(): String =
    when (this) {
        is SuggestionWording.Name -> stringResource(R.string.review_name_question_format, name)
        is SuggestionWording.RoleOnly -> stringResource(R.string.review_role_question_format, role)
        is SuggestionWording.NameAndRole ->
            stringResource(R.string.review_name_role_question_format, name, role)
    }

@Composable
@ReadOnlyComposable
internal fun roleNoticeText(notice: RoleNotice): String =
    when (notice) {
        is RoleNotice.MergedInto -> stringResource(R.string.role_merged_format, notice.name)
    }

/** Shows [notice] once in [host], then calls [shown]. */
@Composable
fun RoleNoticeEffect(
    notice: RoleNotice?,
    host: SnackbarHostState,
    shown: () -> Unit,
) {
    val text = notice?.let { roleNoticeText(it) }
    LaunchedEffect(notice) {
        text ?: return@LaunchedEffect
        host.showSnackbar(text)
        shown()
    }
}
