package com.aquib.androidperflab.ui

data class FeedItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val description: String,
    val author: String,
    val imageUrl: String,
    val timestampMillis: Long,
)
