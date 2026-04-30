package com.aquib.androidperflab.ui

import androidx.compose.runtime.Immutable

// @Immutable tells the Compose compiler that every field in FeedItem is immutable after
// construction. This lets Compose skip any composable whose only changed parameter is
// a FeedItem whose reference is equal to the previous value, without re-checking each
// field individually. Use @Immutable (not @Stable) because all fields are val and all
// field types are themselves immutable — a stronger guarantee than @Stable, which only
// promises that mutations will be notified.
@Immutable
data class FeedItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val description: String,
    val author: String,
    val imageUrl: String,
    val timestampMillis: Long,
)
