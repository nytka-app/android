package io.github.nytka_app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/**
 * What a list shows when it has no rows: [Empty] says what will appear and how, [Loading] three static
 * skeleton rows, [Error] what failed and what is safe with one [action], [Offline] where the data waits.
 * [Loading] takes no text; [title] is then its accessible label.
 */
@Composable
fun ListState(
    kind: ListStateKind,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    when (kind) {
        ListStateKind.Empty -> EmptyState(title, body, detail, action, modifier)
        ListStateKind.Loading -> LoadingState(title, modifier)
        ListStateKind.Error ->
            Banner(
                title,
                body,
                detail,
                action,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                modifier,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
            }

        ListStateKind.Offline ->
            Banner(
                title,
                body,
                detail,
                action,
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurface,
                modifier,
            ) {
                Icon(
                    painterResource(R.drawable.ic_cloud_off),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String?,
    detail: String?,
    action: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThreadLoop()
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

/** The Memories thread-loop: a thread with one loop, dashed because nothing is tied yet. */
@Composable
private fun ThreadLoop() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(width = 96.dp, height = 40.dp)) {
        val w = size.width
        val h = size.height
        val path =
            Path().apply {
                moveTo(0f, h * 0.8f)
                cubicTo(w * 0.25f, h * 0.8f, w * 0.3f, h * 0.9f, w * 0.4f, h * 0.55f)
                cubicTo(w * 0.5f, h * 0.05f, w * 0.7f, h * 0.05f, w * 0.65f, h * 0.45f)
                cubicTo(w * 0.6f, h * 0.85f, w * 0.45f, h * 0.7f, w * 0.5f, h * 0.55f)
                cubicTo(w * 0.6f, h * 0.35f, w * 0.8f, h * 0.8f, w, h * 0.8f)
            }
        drawPath(
            path,
            color,
            style =
                Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                ),
        )
    }
}

@Composable
private fun LoadingState(
    label: String,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(SKELETON_ROWS) {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBar(width = 0.4f, height = 12.dp)
                    SkeletonBar(width = 0.75f, height = 16.dp)
                    SkeletonBar(width = 1f, height = 12.dp)
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 3

@Composable
private fun SkeletonBar(
    width: Float,
    height: Dp,
) {
    Spacer(
        Modifier
            .fillMaxWidth(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun Banner(
    title: String,
    body: String?,
    detail: String?,
    action: (@Composable () -> Unit)?,
    container: Color,
    content: Color,
    modifier: Modifier,
    glyph: @Composable () -> Unit,
) {
    Surface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            glyph()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                detail?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                action?.invoke()
            }
        }
    }
}

/** The error and offline states of a list, worded the same on every screen; [onRetry] is the error's one action. */
@Composable
fun ListFailure(
    error: String,
    offline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (offline) {
        ListState(
            ListStateKind.Offline,
            stringResource(R.string.list_offline_title),
            modifier,
            body = stringResource(R.string.list_offline_body),
        )
    } else {
        ListState(
            ListStateKind.Error,
            error,
            modifier,
            body = stringResource(R.string.list_error_safe),
            action = { TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } },
        )
    }
}
