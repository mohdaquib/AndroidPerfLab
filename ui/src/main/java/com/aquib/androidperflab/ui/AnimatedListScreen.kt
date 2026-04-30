package com.aquib.androidperflab.ui

import androidx.compose.animation.core.DeferredTargetAnimation
import androidx.compose.animation.core.ExperimentalAnimatableApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// @Immutable: all fields are val and all types are immutable, so the Compose compiler
// can skip any composable whose AnimatedItem argument has not changed reference.
@Immutable
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
        body = "Alpha pulse runs via graphicsLayer (draw phase — zero recomposition). " +
            "Expand/collapse uses DeferredTargetAnimation inside Modifier.layout — " +
            "height changes are layout-phase only. No recomposition during animation frames.",
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
                .semantics { contentDescription = "animated_list" },
        ) {
            // FIX — stable key: pins each slot to a specific AnimatedItem by id so
            // Compose can reuse existing nodes on structural changes instead of
            // destroying and recreating items by position.
            items(items, key = { it.id }) { item ->
                AnimatedListCard(item = item)
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalAnimatableApi::class)
@Composable
private fun AnimatedListCard(item: AnimatedItem) {
    var expanded by remember { mutableStateOf(false) }

    // rememberCoroutineScope() gives a scope tied to this composable's lifecycle.
    // DeferredTargetAnimation uses it to launch the height-animation coroutine from
    // inside the layout lambda — without ever touching the composition phase.
    val scope = rememberCoroutineScope()

    // FIX — remember(item.id): Color object allocated once per item.
    // Previously a new Color was constructed on every recomposition.
    val accentColor = remember(item.id) {
        Color(
            red   = (item.id * 37 % 200 + 55) / 255f,
            green = (item.id * 71 % 180 + 50) / 255f,
            blue  = (item.id * 13 % 220 + 35) / 255f,
        )
    }

    // FIX — infinite alpha pulse via graphicsLayer:
    // animateFloat() returns State<Float>. Assigning to a plain val (no `by`) means
    // the value is NOT read here in the composition scope. The `by` delegate would call
    // State.getValue() during composition, subscribing this entire composable to the
    // animation and causing a full recompose on every 16 ms frame.
    //
    // Instead, alphaState.value is read only inside the graphicsLayer lambda below,
    // which runs in the draw phase. Only the GPU layer is invalidated on each tick —
    // composition and layout are never touched.
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_${item.id}")
    val alphaState = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 600 + (item.id % 10) * 80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha_${item.id}",
    )

    // FIX — DeferredTargetAnimation for expand/collapse:
    // Drives the body's height from 0→full (expand) or full→0 (collapse) entirely
    // from inside Modifier.layout. State reads in a layout lambda trigger a layout-phase
    // re-run, not a recomposition — so the 80 frames of a spring animation produce
    // 80 layout passes and zero recompositions.
    val expandAnim = remember { DeferredTargetAnimation(Float.VectorConverter) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // FIX — graphicsLayer for alpha: alphaState.value is read inside the
            // lambda, scoped to the draw phase. The composable tree above this point
            // is never invalidated by the animation.
            .graphicsLayer { alpha = alphaState.value }
            .background(accentColor.copy(alpha = 0.07f))
            .clickable { expanded = !expanded },
    ) {
        // ── Header (always visible) ───────────────────────────────────────────
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

        // ── Body (height animated via DeferredTargetAnimation) ────────────────
        //
        // The body is always present in the composition tree — no if/else branch.
        // Removing the branch means tapping the card causes exactly ONE recomposition
        // (to flip the chevron); thereafter the spring animation runs purely in the
        // layout phase via DeferredTargetAnimation.
        //
        // Modifier.layout measures the full body height, then reports an animated
        // fraction of that height to the parent. clipToBounds() hides content that
        // extends beyond the currently animated bounds so nothing visually overflows.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)

                    // updateTarget() is called here (layout phase) — it starts or
                    // redirects the animation coroutine when the target changes, and
                    // returns the current interpolated progress value.
                    // Reading it here causes layout-phase invalidation on each frame,
                    // NOT a recomposition.
                    val progress = expandAnim.updateTarget(
                        target         = if (expanded) 1f else 0f,
                        coroutineScope = scope,
                        animationSpec  = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                    val animatedHeight = (placeable.height * progress)
                        .roundToInt()
                        .coerceAtLeast(0)

                    layout(placeable.width, animatedHeight) {
                        placeable.place(0, 0)
                    }
                },
        ) {
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
