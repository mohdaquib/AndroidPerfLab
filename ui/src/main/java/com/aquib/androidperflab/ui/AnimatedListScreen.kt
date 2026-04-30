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

private data class AnimatedItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val body: String,
)

private fun generateAnimatedItems(count: Int = 80): List<AnimatedItem> = List(count) { i ->
    AnimatedItem(
        id = i,
        title = "Animation Demo Item #$i",
        subtitle = "Tap to expand · item ${i + 1} of $count",
        body = "This card has animateContentSize applied, an alpha that reads State<Float> " +
            "in composition scope (not inside a graphicsLayer lambda), and a Color " +
            "constructed inline on every recomposition. All intentionally unoptimized.",
    )
}

@Composable
fun AnimatedListScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val items = remember { generateAnimatedItems() }
    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Text("← Back")
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("animated_list")
                .semantics { contentDescription = "animated_list" }
        ) {
            // BAD: no key lambda — Compose cannot track item identity across recompositions,
            // so any structural change causes it to diff by position rather than by id.
            items(items) { item ->
                AnimatedListCard(item = item)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AnimatedListCard(item: AnimatedItem) {
    var expanded by remember { mutableStateOf(false) }

    // BAD: animatedAlpha is a State<Float> whose value changes every ~16 ms while the
    // animation runs. Reading it here (via the `by` delegate) subscribes this composable
    // to that state, so the ENTIRE composable — and every child inside it — recomposes
    // on every single animation frame.
    //
    // Correct fix: don't read the state here at all. Instead, pass it into a graphicsLayer
    // lambda where only the draw phase is invalidated:
    //   Modifier.graphicsLayer { alpha = animatedAlpha }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_${item.id}")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            // Stagger durations slightly so items don't all flash in sync.
            animation = tween(durationMillis = 600 + (item.id % 10) * 80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha_${item.id}",
    )

    // BAD: new Color object allocated on every recomposition — should be a top-level
    // constant or wrapped in remember { Color(...) }.
    val accentColor = Color(
        red   = (item.id * 37 % 200 + 55) / 255f,
        green = (item.id * 71 % 180 + 50) / 255f,
        blue  = (item.id * 13 % 220 + 35) / 255f,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // BAD 1: animateContentSize on every item in the list.
            // When any card expands, the LayoutModifier runs its size interpolation on
            // every animation frame for every card that has this modifier — not just the
            // one the user tapped.
            .animateContentSize()
            // BAD 2: Modifier.alpha() evaluates its argument during composition, so the
            // state read above makes this whole subtree recompose every frame.
            // Modifier.graphicsLayer { alpha = animatedAlpha } would confine the read to
            // the draw phase and skip recomposition entirely.
            .alpha(animatedAlpha)
            .background(accentColor.copy(alpha = 0.07f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.body, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            // Extra lines increase the layout cost when animateContentSize runs.
            repeat(4) { line ->
                Text(
                    text = "Detail line ${line + 1} — item #${item.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
