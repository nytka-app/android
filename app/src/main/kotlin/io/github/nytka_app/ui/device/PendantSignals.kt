package io.github.nytka_app.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/** The shape drawn beside a row: the page shows steady, blinking and off, never the device's own LED hues. */
enum class SignalKind { Steady, Blink, None, Press }

data class SignalRow(
    val kind: SignalKind,
    val what: String,
    val means: String,
)

/** What one pendant brand shows the people around its owner. One page per supported brand, from its firmware. */
data class PendantSignalsContent(
    val device: String,
    val truth: String,
    val lightLabel: String,
    val light: List<SignalRow>,
    val buttonLabel: String,
    val button: List<SignalRow>,
    val source: String,
)

/**
 * The Omi pendant on firmware 3.0.x: `set_led_state()` in `omi/firmware/omi/src/main.c` and `lib/core/button.c`
 * (BasedHardware/omi at a82aab91e1), and `CaptureController` for the taps.
 */
@Composable
fun omiSignals(): PendantSignalsContent =
    PendantSignalsContent(
        device = stringResource(R.string.signals_device_omi),
        truth = stringResource(R.string.signals_truth),
        lightLabel = stringResource(R.string.signals_light_label),
        light =
            listOf(
                SignalRow(
                    SignalKind.Steady,
                    stringResource(R.string.signals_light_blue),
                    stringResource(R.string.signals_light_blue_means),
                ),
                SignalRow(
                    SignalKind.Steady,
                    stringResource(R.string.signals_light_red),
                    stringResource(R.string.signals_light_red_means),
                ),
                SignalRow(
                    SignalKind.Blink,
                    stringResource(R.string.signals_light_red_blink),
                    stringResource(R.string.signals_light_red_blink_means),
                ),
                SignalRow(
                    SignalKind.Blink,
                    stringResource(R.string.signals_light_charging),
                    stringResource(R.string.signals_light_charging_means),
                ),
                SignalRow(
                    SignalKind.Steady,
                    stringResource(R.string.signals_light_green),
                    stringResource(R.string.signals_light_green_means),
                ),
                SignalRow(
                    SignalKind.None,
                    stringResource(R.string.signals_light_off),
                    stringResource(R.string.signals_light_off_means),
                ),
                SignalRow(
                    SignalKind.Blink,
                    stringResource(R.string.signals_light_boot),
                    stringResource(R.string.signals_light_boot_means),
                ),
            ),
        buttonLabel = stringResource(R.string.signals_button_label),
        button =
            listOf(
                SignalRow(
                    SignalKind.Press,
                    stringResource(R.string.signals_button_double),
                    stringResource(R.string.signals_button_double_means),
                ),
                SignalRow(
                    SignalKind.Press,
                    stringResource(R.string.signals_button_single),
                    stringResource(R.string.signals_button_single_means),
                ),
                SignalRow(
                    SignalKind.Press,
                    stringResource(R.string.signals_button_hold),
                    stringResource(R.string.signals_button_hold_means),
                ),
            ),
        source = stringResource(R.string.signals_source_omi),
    )

/** The Device tab's "How people nearby know" page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendantSignalsScreen(
    onBack: () -> Unit,
    content: PendantSignalsContent = omiSignals(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.signals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(content.device, style = MaterialTheme.typography.labelLarge)
                    // A fact, not a failure and not a decision: neutral, neither error nor saffron.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(content.truth, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item { SignalHeading(content.lightLabel) }
            items(content.light) { SignalLine(it) }
            item { SignalHeading(content.buttonLabel) }
            items(content.button) { SignalLine(it) }
            item {
                Text(
                    content.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SignalHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun SignalLine(row: SignalRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SignalShape(row.kind)
        Column(Modifier.weight(1f)) {
            Text(row.what, style = MaterialTheme.typography.bodyLarge)
            Text(
                row.means,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Steady is a solid disc, blinking a disc with a ring around it, off an empty ring, a press a rounded key. */
@Composable
private fun SignalShape(kind: SignalKind) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(32.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = Stroke(width = 2.dp.toPx())
        when (kind) {
            SignalKind.Steady -> drawCircle(ink, radius = size.minDimension * 0.3f, center = c)
            SignalKind.Blink -> {
                drawCircle(ink, radius = size.minDimension * 0.2f, center = c)
                drawCircle(ink, radius = size.minDimension * 0.42f, center = c, style = stroke)
            }
            SignalKind.None -> drawCircle(ink, radius = size.minDimension * 0.3f, center = c, style = stroke)
            SignalKind.Press -> {
                val side = size.minDimension * 0.6f
                drawRoundRect(
                    ink,
                    topLeft = Offset(c.x - side / 2, c.y - side / 2),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.3f),
                    style = stroke,
                )
            }
        }
    }
}
