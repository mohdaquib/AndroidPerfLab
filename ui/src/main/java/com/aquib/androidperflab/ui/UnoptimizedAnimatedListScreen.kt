package com.aquib.androidperflab.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// BUG — no @Immutable: Compose compiler cannot prove fields are stable,
// so it cannot skip recomposition for slots whose AnimatedItem reference is unchanged.
private data class UnoptimizedItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val body: String,
)

private fun generateUnoptimizedItems(count: Int = 80): List<UnoptimizedItem> = List(count) { i ->
    UnoptimizedItem(
        id = i,
        title = "Animation Demo Item #$i",
        subtitle = "Tap to expand · item ${i + 1} of $count",
        body = "Alpha is read via `by` in composition scope — every 16 ms animation tick " +
            "triggers a full recompose of all visible cards. animateContentSize() runs an " +
            "extra layout pass per frame. No stable key means position-based node reuse.",
    )
}

@Composable
fun UnoptimizedAnimatedListScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val items = remember { generateUnoptimizedItems() }
    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Text("← Back")
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("unoptimized_animated_list")
                .semantics { contentDescription = "unoptimized_animated_list" },
        ) {
            // BUG — no key lambda: Compose reuses slots by position.
            // When items scroll in/out, existing nodes are torn down and rebuilt by
            // index rather than by identity, discarding saved state and causing
            // unnecessary composition work.
            items(items) { item ->
                UnoptimizedAnimatedCard(item = item)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun UnoptimizedAnimatedCard(item: UnoptimizedItem) {
    var expanded by remember { mutableStateOf(false) }

    // BUG — alpha read in composition scope via `by` delegate:
    // State.getValue() is called here, so this entire composable subscribes to the
    // animation state. On every 16 ms tick Compose invalidates and recomposes all
    // visible UnoptimizedAnimatedCard instances — not just their draw layers.
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_${item.id}")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600 + (item.id % 10) * 80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha_${item.id}",
    )

    // BUG — inline Color per recomposition:
    // A new Color object is heap-allocated on every recompose. At 60 fps with all
    // visible cards recomposing together this creates sustained allocation pressure.
    val accentColor = Color(
        red   = (item.id * 37 % 200 + 55) / 255f,
        green = (item.id * 71 % 180 + 50) / 255f,
        blue  = (item.id * 13 % 220 + 35) / 255f,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // BUG — Modifier.alpha(alpha): alpha was already read by the `by` delegate
            // above, subscribing this composable to animation ticks. The Float value is
            // then passed here; the recompose chain is set in motion regardless.
            .alpha(alpha)
            .background(accentColor.copy(alpha = 0.07f))
            // BUG — animateContentSize: runs an additional layout pass each time the
            // animated size changes. Combined with per-frame recomposition from the
            // alpha bug above, this is measured on every animation tick for any card
            // that is in the middle of an expand/collapse animation.
            .animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                )
                Text(
                    text  = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text  = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = item.body, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                repeat(4) { line ->
                    Text(
                        text  = "Detail line ${line + 1} — item #${item.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
