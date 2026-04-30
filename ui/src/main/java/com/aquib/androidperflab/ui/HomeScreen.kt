package com.aquib.androidperflab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        var selectedItem by remember { mutableStateOf<FeedItem?>(null) }
        var showAnimatedList by remember { mutableStateOf(false) }

        when {
            selectedItem != null -> {
                BackHandler { selectedItem = null }
                DetailScreen(item = selectedItem!!, onBack = { selectedItem = null })
            }
            showAnimatedList -> {
                BackHandler { showAnimatedList = false }
                AnimatedListScreen(onBack = { showAnimatedList = false })
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    FeedScreen(onItemClick = { selectedItem = it })
                    FloatingActionButton(
                        onClick = { showAnimatedList = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("animated_list_fab")
                            .semantics { contentDescription = "animated_list_fab" },
                    ) {
                        Text("▶")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
