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
        items(items = items, key = { it.id }) { item ->
            FeedItemRow(item = item, onClick = { onItemClick(item) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun FeedItemRow(item: FeedItem, onClick: () -> Unit = {}) {
    // Intentionally unoptimized: a new SimpleDateFormat is allocated and a new Date is
    // constructed on every recomposition instead of being computed once with remember {}.
    val timestamp = SimpleDateFormat("EEE, dd MMM yyyy  HH:mm:ss", Locale.getDefault())
        .format(Date(item.timestampMillis))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
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
