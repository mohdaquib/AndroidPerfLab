package com.aquib.androidperflab.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("UnrememberedMutableState")
@Composable
fun DetailScreen(
    item: FeedItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The only remember{} in this composable — drives continuous recompositions so every
    // bad practice below has a visible cost during profiling.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500L); tick++ } }

    // BAD: mutableStateOf without remember — both values reset to 0 on every recomposition,
    // so user interactions never persist across a recompose cycle.
    var likeCount by mutableStateOf(0)
    var bookmarkCount by mutableStateOf(0)

    // BAD: no derivedStateOf — isPopular recalculates on every recomposition of DetailScreen
    // (e.g. each tick), not only when likeCount actually changes.
    val isPopular = likeCount > 50

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailBackButton(onBack = onBack)                               // 1  BAD: inline lambda
        DetailHeroImage(url = item.imageUrl, tick = tick)               // 2  recomposes every 500 ms
        DetailTimestamp(millis = item.timestampMillis)                   // 3  BAD: inline SimpleDateFormat
        DetailTitle(title = item.title, isPopular = isPopular)          // 4  re-evaluates each tick
        DetailTagsRow(title = item.title)                                // 5  BAD: split() inline
        DetailReadingTime(description = item.description)               // 6  BAD: word count inline
        DetailLiveCounter(tick = tick)                                   // 7  ticks every 500 ms
        DetailInteractionBar(                                            // 8  BAD: inline lambdas
            likeCount = likeCount,
            bookmarkCount = bookmarkCount,
            onLike = { likeCount++ },
            onBookmark = { bookmarkCount++ },
        )
        DetailDescription(description = item.description)               // 9
        DetailStatsGrid(id = item.id)                                    // 10 BAD: arithmetic inline
        DetailRelatedSection(sourceId = item.id)                        // 11 BAD: no key, inline List
        DetailAuthorCard(author = item.author, id = item.id)            // 12 BAD: inline Color + ops
    }
}

// ── Child composables ────────────────────────────────────────────────────────

@Composable
private fun DetailBackButton(onBack: () -> Unit) {
    // BAD: onBack is an inline lambda at the call site — a new instance each recomposition,
    // so Compose cannot skip recomposing this child.
    TextButton(onClick = onBack) {
        Text("← Back to feed")
    }
}

@Composable
private fun DetailHeroImage(url: String, tick: Int) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
    Text(
        text = "Live updates: $tick",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun DetailTimestamp(millis: Long) {
    // BAD: new SimpleDateFormat and Date allocated on every recomposition.
    val formatted = SimpleDateFormat("EEEE, MMMM dd yyyy 'at' HH:mm:ss", Locale.getDefault())
        .format(Date(millis))
    Text(
        text = formatted,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun DetailTitle(title: String, isPopular: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (isPopular) {
            Text(
                text = "🔥 Popular",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DetailTagsRow(title: String) {
    // BAD: split + filter + map run on every recomposition — should be remember { }.
    val tags = title.split(" ").filter { it.length > 3 }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // BAD: no key {} — Compose cannot track tag identity across recompositions.
        tags.forEach { tag ->
            Text(
                text = "#${tag.lowercase()}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DetailReadingTime(description: String) {
    // BAD: split + size called on every recomposition — should be remember { }.
    val wordCount = description.split(" ").size
    val minutes = (wordCount / 200).coerceAtLeast(1)
    Text(
        text = "$wordCount words · $minutes min read",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun DetailLiveCounter(tick: Int) {
    Text(
        text = "Recomposed $tick times",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DetailInteractionBar(
    likeCount: Int,
    bookmarkCount: Int,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
) {
    // BAD: no derivedStateOf — these strings are rebuilt every recomposition even when
    // likeCount / bookmarkCount have not changed (e.g. on each tick).
    val likeLabel = "♥ Like ($likeCount)"
    val bookmarkLabel = "🔖 Save ($bookmarkCount)"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // BAD: onLike / onBookmark are inline lambdas — new instances each recomposition,
        // preventing Compose from skipping Button recomposition.
        Button(onClick = onLike, modifier = Modifier.testTag("detail_like_button")) { Text(likeLabel) }
        OutlinedButton(onClick = onBookmark, modifier = Modifier.testTag("detail_bookmark_button")) { Text(bookmarkLabel) }
    }
}

@Composable
private fun DetailDescription(description: String) {
    Text(text = description, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DetailStatsGrid(id: Int) {
    // BAD: all multiplications run on every recomposition — should be remember { }.
    val views    = id * 317 + 1_200
    val shares   = id * 41  + 80
    val comments = id * 13  + 25
    val reposts  = id * 7   + 10
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Post stats", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("👁  $views views")
            Text("🔁 $reposts reposts")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("💬 $comments comments")
            Text("📤 $shares shares")
        }
    }
}

@Composable
private fun DetailRelatedSection(sourceId: Int) {
    // BAD: List allocation on every recomposition — should be remember { }.
    val relatedIds = List(8) { i -> (sourceId + i + 1) % 220 }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Related posts", style = MaterialTheme.typography.titleSmall)
        // BAD: no key {} on forEach — Compose cannot identify items across recompositions,
        // so it may recompose all children even when only one changes.
        relatedIds.forEach { relatedId ->
            DetailRelatedItem(
                imageUrl = "https://picsum.photos/seed/$relatedId/60/60",
                title = "Related Post #$relatedId",
                category = "Category ${relatedId % 10}",
            )
        }
    }
}

@Composable
private fun DetailRelatedItem(imageUrl: String, title: String, category: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DetailAuthorCard(author: String, id: Int) {
    // BAD: Color object allocated inline — should be a top-level constant or remember { }.
    val avatarColor = Color(0xFF1565C0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // BAD: split + map + joinToString to derive initials on every recomposition.
        val initials = author.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("")

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(avatarColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initials, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Column {
            Text(author, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            // BAD: inline modulo + addition on every recomposition.
            Text("${id % 50 + 5} posts published", style = MaterialTheme.typography.labelSmall)
        }
    }
}
