package com.aquib.androidperflab.ui

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

// FIX — constant Color: moved out of DetailAuthorCard so the Color object is allocated
// once at class-load time rather than on every recomposition of the card.
private val AvatarColor = Color(0xFF1565C0)

@Composable
fun DetailScreen(
    item: FeedItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500L); tick++ } }

    // FIX — remember state: both counters now survive tick-driven recompositions.
    // Without remember{}, mutableStateOf creates a brand-new State object every
    // recompose, so any increment the user performed was silently discarded the moment
    // the next 500 ms tick fired.
    var likeCount by remember { mutableStateOf(0) }
    var bookmarkCount by remember { mutableStateOf(0) }

    // FIX — derivedStateOf: isPopular is re-evaluated only when likeCount changes,
    // and it only notifies observers (DetailTitle) when the resulting Boolean actually
    // flips. Previously this bare comparison ran unconditionally on every tick, causing
    // DetailTitle to recompose at 2 Hz even though isPopular was always false.
    val isPopular by remember { derivedStateOf { likeCount > 50 } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailBackButton(onBack = onBack)
        // FIX — split composables: the former DetailHeroImage(url, tick) bundled a
        // stable image with a dynamic counter. Splitting them means the AsyncImage node
        // is now skipped on every tick; only DetailLiveUpdateBadge recomposes at 2 Hz.
        DetailHeroImage(url = item.imageUrl)
        DetailLiveUpdateBadge(tick = tick)
        DetailTimestamp(millis = item.timestampMillis)
        DetailTitle(title = item.title, isPopular = isPopular)
        DetailTagsRow(title = item.title)
        DetailReadingTime(description = item.description)
        DetailLiveCounter(tick = tick)
        DetailInteractionBar(
            likeCount = likeCount,
            bookmarkCount = bookmarkCount,
            onLike = { likeCount++ },
            onBookmark = { bookmarkCount++ },
        )
        DetailDescription(description = item.description)
        DetailStatsGrid(id = item.id)
        DetailRelatedSection(sourceId = item.id)
        DetailAuthorCard(author = item.author, id = item.id)
    }
}

// ── Child composables ─────────────────────────────────────────────────────────

@Composable
private fun DetailBackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) {
        Text("← Back to feed")
    }
}

// FIX — stable image node: url is the only parameter and String is stable, so the
// Compose compiler marks this function skippable. The AsyncImage is never re-entered
// during tick-driven recompositions as long as the URL has not changed.
@Composable
private fun DetailHeroImage(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}

// FIX — extracted dynamic node: separated from DetailHeroImage so only this
// lightweight Text recomposes every 500 ms. The image above it is fully isolated.
@Composable
private fun DetailLiveUpdateBadge(tick: Int) {
    Text(
        text = "Live updates: $tick",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun DetailTimestamp(millis: Long) {
    // FIX — remember(millis): SimpleDateFormat and Date are allocated only when the
    // timestamp value actually changes. Previously both were allocated on every
    // recomposition even though the formatted string was always identical.
    val formatted = remember(millis) {
        SimpleDateFormat("EEEE, MMMM dd yyyy 'at' HH:mm:ss", Locale.getDefault())
            .format(Date(millis))
    }
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
    // FIX — remember(title): split + filter run only when title changes.
    // FIX — key(tag): each chip now has a stable identity so Compose can diff
    // additions and removals rather than destroying and recreating every chip.
    val tags = remember(title) { title.split(" ").filter { it.length > 3 } }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            key(tag) {
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
}

@Composable
private fun DetailReadingTime(description: String) {
    // FIX — remember(description): split().size runs only when description changes.
    // The result is a Pair<Int,Int> so both derived values share a single remember block.
    val (wordCount, minutes) = remember(description) {
        val words = description.split(" ").size
        words to (words / 200).coerceAtLeast(1)
    }
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
    // FIX — remember(count): label strings are memoized per count value. Previously
    // concatenation ran on every recomposition even when counts had not changed
    // (e.g. during a tick-triggered recompose of the parent).
    val likeLabel     = remember(likeCount)     { "♥ Like ($likeCount)" }
    val bookmarkLabel = remember(bookmarkCount) { "🔖 Save ($bookmarkCount)" }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    // FIX — remember(id): all four stats are pure functions of id, which never
    // changes for a given item. Previously the multiplications ran on every recompose.
    val views    = remember(id) { id * 317 + 1_200 }
    val shares   = remember(id) { id * 41  + 80 }
    val comments = remember(id) { id * 13  + 25 }
    val reposts  = remember(id) { id * 7   + 10 }
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
    // FIX — remember(sourceId): list allocation runs only when sourceId changes.
    // FIX — key(relatedId): each row has a stable identity so Compose diffs the
    // eight-item block instead of recomposing it wholesale.
    val relatedIds = remember(sourceId) { List(8) { i -> (sourceId + i + 1) % 220 } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Related posts", style = MaterialTheme.typography.titleSmall)
        relatedIds.forEach { relatedId ->
            key(relatedId) {
                DetailRelatedItem(
                    imageUrl = "https://picsum.photos/seed/$relatedId/60/60",
                    title = "Related Post #$relatedId",
                    category = "Category ${relatedId % 10}",
                )
            }
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
    // FIX — remember(author): initials derivation (split + map + joinToString) runs
    // only when the author string changes, not on every recomposition.
    val initials = remember(author) {
        author.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("")
    }
    // FIX — remember(id): post-count string is a pure function of id.
    val postsPublished = remember(id) { "${id % 50 + 5} posts published" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(AvatarColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initials, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Column {
            Text(author, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(postsPublished, style = MaterialTheme.typography.labelSmall)
        }
    }
}
