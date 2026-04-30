package com.aquib.androidperflab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CATEGORIES = listOf(
    "Technology", "Science", "Health", "Travel", "Food",
    "Sports", "Art", "Music", "Business", "Education",
)

private fun generateFeedItems(count: Int = 220): List<FeedItem> = List(count) { i ->
    FeedItem(
        id = i,
        title = "Post #$i — ${CATEGORIES[i % CATEGORIES.size]}",
        subtitle = "${CATEGORIES[i % CATEGORIES.size]} · ${i % 8 + 1} min read",
        description = "This is the body of feed item $i. It contains enough text to " +
            "simulate a real article excerpt that would appear in a social feed.",
        author = "Author ${i % 25}",
        imageUrl = "https://picsum.photos/seed/$i/200/200",
        timestampMillis = System.currentTimeMillis() - i * 3_600_000L,
    )
}

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    onItemClick: (FeedItem) -> Unit = {},
) {
    val items = remember { generateFeedItems() }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        // FIX 1 — stable key: key = { it.id } pins each slot to a specific FeedItem by
        // identity. Without a key, inserting or removing one item causes every subsequent
        // row to be destroyed and recreated. With a stable key Compose reuses existing
        // nodes and only recomposes the slots whose content actually changed.
        items(items = items, key = { it.id }) { item ->
            FeedItemRow(item = item, onClick = { onItemClick(item) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun FeedItemRow(item: FeedItem, onClick: () -> Unit = {}) {
    // FIX 2 — remembered timestamp: SimpleDateFormat and Date are only allocated when
    // item.timestampMillis changes. Previously they were allocated on every recomposition,
    // even though the formatted string never changed between recompose passes.
    val timestamp = remember(item.timestampMillis) {
        SimpleDateFormat("EEE, dd MMM yyyy  HH:mm:ss", Locale.getDefault())
            .format(Date(item.timestampMillis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // FIX 3 — stable image wrapper: see FeedItemImage below.
        FeedItemImage(url = item.imageUrl)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleSmall)
            Text(text = item.subtitle, style = MaterialTheme.typography.labelMedium)
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            Text(text = "by ${item.author}", style = MaterialTheme.typography.labelSmall)
            Text(text = timestamp, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// FIX 3 — stable AsyncImage wrapper: String is a stable type, so the Compose compiler
// marks this function as skippable. When FeedItemRow recomposes for any reason other
// than a URL change (e.g. a new onClick lambda instance), Compose compares the url
// argument and skips the entire function body — AsyncImage is never re-entered and
// no unnecessary image-load checks are issued to the Coil cache.
@Composable
private fun FeedItemImage(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}
