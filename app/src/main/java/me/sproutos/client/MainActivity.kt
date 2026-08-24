package me.sproutos.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Two tabs, as the brief specifies: public and personal.
 *
 * The tabs are not two catalogues — they are two views of one response, split by who is asking.
 * That is why an unauthenticated visitor sees an empty personal tab with a sentence in it rather
 * than a sign-in wall: somebody deciding whether to use SproutOS should be able to look first.
 */
enum class Tab(val label: String) {
    Public("Public"),
    Personal("Personal"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CatalogueScreen() } }
    }
}

@Composable
fun CatalogueScreen(state: CatalogueState = remember { CatalogueState() }) {
    var tab by remember { mutableStateOf(Tab.Public) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { Text(entry.label) },
                        icon = {},
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
            )

            val entries = search(state.entriesFor(tab), query)

            when {
                state.error != null -> Text(state.error!!, modifier = Modifier.padding(top = 24.dp))

                entries.isEmpty() && tab == Tab.Personal ->
                    // Said, rather than an empty list. A blank tab reads as a failure to load; a
                    // sentence reads as an account with nothing in it yet.
                    Text(
                        "Nothing here yet. Anything you deploy on SproutOS shows up in this tab.",
                        modifier = Modifier.padding(top = 24.dp),
                    )

                entries.isEmpty() ->
                    Text("Nothing matches.", modifier = Modifier.padding(top = 24.dp))

                else -> LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                    items(entries) { entry -> EntryRow(entry, state) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, state: CatalogueState) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(entry.title, style = MaterialTheme.typography.titleMedium)
        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall)

        when (entry) {
            is Entry.Installable ->
                Button(onClick = { state.install(entry.app) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Install")
                }

            is Entry.Website ->
                Button(onClick = { state.open(entry.site) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Open")
                }
        }
    }
}
