package com.sproutos.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        setContent {
            MaterialTheme {
                CatalogueScreen(
                    state = state,
                    onSignIn = ::signIn,
                    onSignOut = ::signOut,
                    onRefresh = ::refresh,
                    onInstall = ::install,
                )
            }
        }

        // The launch intent can itself be the callback, when the browser reopened a task that had
        // been evicted. Handled here as well as in `onNewIntent` or a cold return silently does
        // nothing.
        handleRedirect(intent)
        if (intent?.data?.scheme != "sproutos") refresh()
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

        when (val result = state.readSignInCallback(data.toString(), store)) {
            is CallbackResult.Code ->
                lifecycleScope.launch {
                    val session =
                        withContext(Dispatchers.IO) { exchangeCode(apiBase(), CLIENT_ID, result) }
                    if (session == null) {
                        state.signInFailed()
                    } else {
                        state.acceptSession(session, store)
                        refresh()
                    }
                }

            is CallbackResult.Denied, CallbackResult.StateMismatch -> Unit
        }
    }

    private fun signIn() = state.signIn(webBase(), CLIENT_ID, store)

    private fun signOut() {
        state.signOut(store)
        refresh()
    }

    private fun refresh() {
        state.beginRefresh()
        lifecycleScope.launch {
            var oauth = state.session.oauth
            if (oauth != null && state.session.accessToken() == null) {
                oauth = withContext(Dispatchers.IO) { refreshSession(apiBase(), CLIENT_ID, oauth) }
                if (oauth == null) state.signOut(store) else state.replaceSession(oauth, store)
            }
            var result = withContext(Dispatchers.IO) { fetchCatalogue(apiBase(), state.session) }
            if (result is CatalogueResult.Unauthorized) {
                state.signOut(store)
                result = withContext(Dispatchers.IO) { fetchCatalogue(apiBase(), Session(null)) }
            }
            state.accept(result)
        }
    }

    private fun install(app: ReleaseMetadata) {
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) { state.prepareInstall(app) }
            state.launchPrepared(app, prepared)
        }
    }

    private fun apiBase(): String =
        getString(R.string.api_base)

    private fun webBase(): String =
        getString(R.string.web_base)

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
    onSignOut: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onInstall: (ReleaseMetadata) -> Unit = {},
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
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
                if (state.signedIn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSignOut) { Text("Sign out") }
                }
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            state.catalogue?.clientUpdate?.let { release ->
                if (state.actionFor(release) == InstallAction.Update) {
                    val operation =
                        state.installState[release.packageName] ?: InstallState.Idle
                    Button(
                        onClick = { onInstall(release) },
                        enabled = installButtonEnabled(operation, InstallAction.Update),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Update SproutOS to ${release.versionName}")
                    }
                    InstallStatus(operation)
                    if (release.required) {
                        Text("This update is required to keep using the catalogue.")
                    }
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
            )

            val entries = search(state.entriesFor(tab), query)

            when {
                state.error != null ->
                    Text(
                        state.error!!,
                        modifier =
                            Modifier.padding(top = 24.dp).semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )

                state.loading && state.catalogue == null -> Unit

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

                else ->
                    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                        if (tab == Tab.Personal) {
                            val apps = entries.filterIsInstance<Entry.Installable>()
                            val sites = entries.filterIsInstance<Entry.Website>()
                            if (apps.isNotEmpty()) {
                                item {
                                    Text(
                                        "Apps",
                                        modifier = Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                items(apps) { entry -> EntryRow(entry, state, onInstall) }
                            }
                            if (sites.isNotEmpty()) {
                                item {
                                    Text(
                                        "Websites",
                                        modifier = Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                items(sites) { entry -> EntryRow(entry, state, onInstall) }
                            }
                        } else {
                            items(entries) { entry -> EntryRow(entry, state, onInstall) }
                        }
                    }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, state: CatalogueState, onInstall: (ReleaseMetadata) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(entry.title, style = MaterialTheme.typography.titleMedium)
        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall)

        when (entry) {
            is Entry.Installable -> {
                val operation = state.installState[entry.app.packageName] ?: InstallState.Idle
                val action = state.actionFor(entry.app)
                val actionLabel =
                    when (action) {
                        InstallAction.Install -> "Install"
                        InstallAction.Update -> "Update"
                        InstallAction.Open -> "Open"
                        InstallAction.RefuseDowngrade -> "Newer version installed"
                        InstallAction.RefuseDifferentSigner -> "Different app installed"
                    }
                InstallStatus(operation)
                Button(
                    onClick = { onInstall(entry.app) },
                    enabled = installButtonEnabled(operation, action),
                    modifier =
                        Modifier.padding(top = 8.dp).semantics {
                            contentDescription = "$actionLabel ${entry.app.label}"
                        },
                ) {
                    Text(actionLabel)
                }
            }

            is Entry.Website ->
                Button(
                    onClick = { state.open(entry.site) },
                    modifier =
                        Modifier.padding(top = 8.dp).semantics {
                            contentDescription = "Open ${entry.site.name}"
                        },
                ) {
                    Text("Open")
                }
        }
    }
}

@Composable
private fun InstallStatus(operation: InstallState) {
    installStatusText(operation)?.let { status ->
        Text(
            status,
            modifier =
                Modifier.padding(top = 8.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}
