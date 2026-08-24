package me.sproutos.client

import android.content.Intent
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
    private val state = CatalogueState()
    private lateinit var store: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = SessionStore(this)
        state.context = this
        state.restore(store)

        setContent { MaterialTheme { CatalogueScreen(state, ::signIn) } }

        // The launch intent can itself be the callback, when the browser reopened a task that had
        // been evicted. Handled here as well as in `onNewIntent` or a cold return silently does
        // nothing.
        handleRedirect(intent)
    }

    /**
     * The browser coming back.
     *
     * `singleTask` in the manifest means this arrives here rather than as a second copy of the
     * activity stacked behind the first — which would have the callback delivered to an instance
     * holding no `pending`, and every sign-in would report a state mismatch.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "sproutos") return

        state.completeSignIn(data.toString(), store) { code ->
            exchangeCode(apiBase(), CLIENT_ID, code)
        }
    }

    private fun signIn() = state.signIn(apiBase(), CLIENT_ID)

    private fun apiBase(): String =
        getString(R.string.api_base)

    private companion object {
        /*
          The platform's `oauth_client.id`, which is a UUID column — there is no separate readable
          client identifier. Seeded by `0007_android_client`, and it must match exactly: a different
          id is a client nothing has granted, and every signed-in customer would be asked to
          authorise again.
        */
        const val CLIENT_ID = "01a03b00-0000-7000-8000-0000000a4d01"
    }
}

@Composable
fun CatalogueScreen(
    state: CatalogueState = remember { CatalogueState() },
    onSignIn: () -> Unit = {},
) {
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

                tab == Tab.Personal && !state.signedIn ->
                    // Not a wall in front of the whole app: the Public tab works signed out, and
                    // somebody deciding whether to use SproutOS should be able to look first.
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        Text("Sign in to see the apps and sites you have deployed.")
                        Button(onClick = onSignIn, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Sign in")
                        }
                    }

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
