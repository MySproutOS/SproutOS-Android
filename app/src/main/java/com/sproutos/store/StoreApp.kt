package com.sproutos.store

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class Destination(val label: String, val icon: ImageVector) {
    Store("Store", Icons.Filled.Home),
    Personal("Personal", Icons.Filled.Person),
    Settings("Settings", Icons.Filled.Settings),
}

private enum class Page { Home, Search, Results, Detail }

@Composable
fun SproutStoreApp(
    state: CatalogueState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (ReleaseMetadata) -> Unit,
    onClientAutomaticUpdates: (Boolean) -> Unit,
    onAppAutomaticUpdates: (Boolean) -> Unit,
    onEnableUpdateNotifications: () -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(Destination.Store.name) }
    var pageName by rememberSaveable { mutableStateOf(Page.Home.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOriginName by rememberSaveable { mutableStateOf(Destination.Store.name) }
    val destination = Destination.valueOf(destinationName)
    val page = Page.valueOf(pageName)

    fun goHome(target: Destination) {
        destinationName = target.name
        pageName = Page.Home.name
        detailPackage = null
    }

    fun openDetail(app: App, origin: Destination) {
        destinationName = origin.name
        detailOriginName = origin.name
        detailPackage = app.packageName
        pageName = Page.Detail.name
    }

    BackHandler(enabled = page != Page.Home || destination != Destination.Store) {
        if (page != Page.Home) {
            pageName = Page.Home.name
            detailPackage = null
        } else {
            goHome(Destination.Store)
        }
    }

    val allApps =
        (state.catalogue?.public?.apps.orEmpty() + state.catalogue?.personal?.apps.orEmpty())
            .distinctBy { it.packageName }
    val detail = detailPackage?.let { packageName -> allApps.firstOrNull { it.packageName == packageName } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { item ->
                    val attention =
                        item == Destination.Settings &&
                            (state.automaticUpdateMessage != null || state.updateNotificationsNeedPermission)
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { goHome(item) },
                        icon = {
                            Box {
                                Icon(item.icon, contentDescription = null)
                                if (attention) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd)
                                            .size(7.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.error),
                                    )
                                }
                            }
                        },
                        label = { Text(item.label) },
                        modifier =
                            Modifier.testTag("sprout:nav:${item.name.lowercase()}")
                                .semantics {
                                    contentDescription =
                                        item.label + if (attention) ", needs attention" else ""
                                },
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                page == Page.Detail && detail != null ->
                    AppDetailScreen(
                        app = detail,
                        state = state,
                        onBack = { pageName = Page.Home.name },
                        onInstall = onInstall,
                    )

                destination == Destination.Store && page == Page.Search ->
                    SearchScreen(
                        query = query,
                        apps = state.catalogue?.public?.apps.orEmpty(),
                        onQuery = { query = it },
                        onBack = { pageName = Page.Home.name },
                        onResults = { pageName = Page.Results.name },
                        onApp = { openDetail(it, Destination.Store) },
                    )

                destination == Destination.Store && page == Page.Results ->
                    ResultsScreen(
                        query = query,
                        apps = state.catalogue?.public?.apps.orEmpty(),
                        state = state,
                        onQuery = { query = it },
                        onBack = { pageName = Page.Search.name },
                        onApp = { openDetail(it, Destination.Store) },
                        onInstall = onInstall,
                    )

                destination == Destination.Store ->
                    StoreScreen(
                        state = state,
                        onRefresh = onRefresh,
                        onSearch = {
                            query = ""
                            pageName = Page.Search.name
                        },
                        onApp = { openDetail(it, Destination.Store) },
                        onInstall = onInstall,
                        onSettings = { goHome(Destination.Settings) },
                    )

                destination == Destination.Personal ->
                    PersonalScreen(
                        state = state,
                        onSignIn = onSignIn,
                        onRefresh = onRefresh,
                        onApp = { openDetail(it, Destination.Personal) },
                        onInstall = onInstall,
                    )

                else ->
                    SettingsScreen(
                        state = state,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        onRefresh = onRefresh,
                        onInstall = onInstall,
                        onClientAutomaticUpdates = onClientAutomaticUpdates,
                        onAppAutomaticUpdates = onAppAutomaticUpdates,
                        onEnableUpdateNotifications = onEnableUpdateNotifications,
                    )
            }
        }
    }
}

@Composable
private fun StoreScreen(
    state: CatalogueState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onApp: (App) -> Unit,
    onInstall: (ReleaseMetadata) -> Unit,
    onSettings: () -> Unit,
) {
    val apps = state.catalogue?.public?.apps.orEmpty()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = apps.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    val visible = selectedCategory?.let { category -> apps.filter { it.category == category } } ?: apps
    val updates = apps.filter { state.actionFor(it) == InstallAction.Update }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Store", onRefresh, state.loading)
        }
        item {
            SearchLauncher(onSearch)
        }
        state.automaticUpdateMessage?.let { message ->
            item {
                AttentionNotice(
                    title = "An update needs your attention",
                    body = message,
                    action = "Open settings",
                    onAction = onSettings,
                )
            }
        }
        if (updates.isNotEmpty()) {
            item { SectionHeading("Updates available", "${updates.size} apps") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(updates, key = { it.androidAppId }) { app ->
                        CompactAppCard(app, state, onApp, onInstall)
                    }
                }
            }
        }
        if (categories.isNotEmpty()) {
            item { SectionHeading("Browse", null) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        CategoryChip("All", selectedCategory == null) { selectedCategory = null }
                    }
                    items(categories) { category ->
                        CategoryChip(category, selectedCategory == category) {
                            selectedCategory = category
                        }
                    }
                }
            }
        }
        item { SectionHeading(selectedCategory ?: "All public apps", "${visible.size} apps") }
        when {
            state.error != null -> item { ErrorNotice(state.error!!, onRefresh) }
            state.loading && state.catalogue == null -> items(4) { LoadingAppRow() }
            visible.isEmpty() ->
                item {
                    EmptyState(
                        title = if (selectedCategory == null) "No public apps yet" else "Nothing in $selectedCategory",
                        body =
                            if (selectedCategory == null) {
                                "Published Android apps will appear here after their release and signing checks pass."
                            } else {
                                "Choose another category or view every app."
                            },
                    )
                }
            else -> items(visible, key = { it.androidAppId }) { app ->
                AppRow(app, state, onApp, onInstall)
            }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    apps: List<App>,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onResults: () -> Unit,
    onApp: (App) -> Unit,
) {
    val matches = search(apps.map { Entry.Installable(it) }, query).map { (it as Entry.Installable).app }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            SearchField(query, onQuery, onBack, onResults, autoFocusLabel = "Search public apps")
        }
        if (query.isBlank()) {
            item {
                EmptyState(
                    title = "Find an app",
                    body = "Search by app name, category, or what you want to do.",
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        } else if (matches.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing matches “$query”",
                    body = "Try fewer words or a different name.",
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        } else {
            items(matches.take(6), key = { it.androidAppId }) { app ->
                SearchSuggestion(app, onApp)
            }
            item {
                TextButton(onClick = onResults, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("See all ${matches.size} results for “$query”")
                }
            }
        }
    }
}

@Composable
private fun ResultsScreen(
    query: String,
    apps: List<App>,
    state: CatalogueState,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onApp: (App) -> Unit,
    onInstall: (ReleaseMetadata) -> Unit,
) {
    val matches = search(apps.map { Entry.Installable(it) }, query).map { (it as Entry.Installable).app }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SearchField(query, onQuery, onBack, {}, autoFocusLabel = "Search public apps") }
        item {
            Text(
                if (matches.size == 1) "1 app" else "${matches.size} apps",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("sprout:results:count"),
            )
        }
        if (matches.isEmpty()) {
            item { EmptyState("Nothing matches “$query”", "Try fewer words or a different name.") }
        } else {
            items(matches, key = { it.androidAppId }) { app -> AppRow(app, state, onApp, onInstall) }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    autoFocusLabel: String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("Search apps") },
        leadingIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSubmit() }),
        modifier =
            Modifier.fillMaxWidth().testTag("sprout:store:search-field").semantics {
                contentDescription = autoFocusLabel
            },
    )
}

@Composable
private fun PersonalScreen(
    state: CatalogueState,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onApp: (App) -> Unit,
    onInstall: (ReleaseMetadata) -> Unit,
) {
    val apps = state.catalogue?.personal?.apps.orEmpty()
    val sites = state.catalogue?.personal?.sites.orEmpty()
    val updates = apps.filter { state.actionFor(it) == InstallAction.Update }
    val installed =
        apps.filter {
            state.actionFor(it) in
                setOf(
                    InstallAction.Open,
                    InstallAction.Update,
                    InstallAction.RefuseDowngrade,
                    InstallAction.RefuseDifferentSigner,
                )
        }
    val available = apps.filter { state.actionFor(it) == InstallAction.Install }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Personal", onRefresh, state.loading) }
        if (!state.signedIn) {
            item {
                SignInPanel(onSignIn)
            }
        } else {
            state.error?.let { item { ErrorNotice(it, onRefresh) } }
            if (updates.isNotEmpty()) {
                item { SectionHeading("Updates", "${updates.size} ready") }
                items(updates, key = { "update-${it.androidAppId}" }) { app ->
                    AppRow(app, state, onApp, onInstall)
                }
            }
            item { SectionHeading("Installed apps", "${installed.size}") }
            if (installed.isEmpty()) {
                item { EmptyState("No installed SproutOS apps", "Apps you install from SproutOS are managed here.") }
            } else {
                items(installed, key = { "installed-${it.androidAppId}" }) { app ->
                    AppRow(app, state, onApp, onInstall)
                }
            }
            if (available.isNotEmpty()) {
                item { SectionHeading("Ready to install", "${available.size}") }
                items(available, key = { "available-${it.androidAppId}" }) { app ->
                    AppRow(app, state, onApp, onInstall)
                }
            }
            if (sites.isNotEmpty()) {
                item { SectionHeading("Sites", "${sites.size}") }
                items(sites, key = { it.url }) { site -> SiteRow(site, state) }
            }
            if (apps.isEmpty() && sites.isEmpty()) {
                item {
                    EmptyState(
                        "Nothing here yet",
                        "Anything you deploy on SproutOS shows up in Personal.",
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: CatalogueState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (ReleaseMetadata) -> Unit,
    onClientAutomaticUpdates: (Boolean) -> Unit,
    onAppAutomaticUpdates: (Boolean) -> Unit,
    onEnableUpdateNotifications: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Settings", onRefresh, state.loading) }
        item { SectionHeading("Account", null) }
        item {
            Surface(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.signedIn) "Signed in" else "Not signed in", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.signedIn) "Personal apps and sites are available." else "Sign in to access your organization library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = if (state.signedIn) onSignOut else onSignIn,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(if (state.signedIn) "Sign out" else "Sign in")
                    }
                }
            }
        }
        item { SectionHeading("Automatic updates", null) }
        item {
            SettingsToggle(
                "SproutOS",
                "Keep this store client current.",
                state.automaticUpdateSettings.client,
                onClientAutomaticUpdates,
                "sprout:settings:client-switch",
            )
        }
        item {
            SettingsToggle(
                "Installed apps",
                "Update apps installed through SproutOS.",
                state.automaticUpdateSettings.installedApps,
                onAppAutomaticUpdates,
                "sprout:settings:apps-switch",
            )
        }
        item {
            Text(
                "Checks daily on an unmetered network when battery and storage are not low. Android may still ask you to confirm an update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.updateNotificationsNeedPermission) {
            item {
                AttentionNotice(
                    "Allow update notifications",
                    "Android needs notification permission when an update cannot finish automatically.",
                    "Allow notifications",
                    onEnableUpdateNotifications,
                )
            }
        }
        state.automaticUpdateMessage?.let { message ->
            item { AttentionNotice("Update confirmation required", message, null, null) }
        }
        item { SectionHeading("SproutOS version", null) }
        item {
            val release = state.catalogue?.clientUpdate
            val hasUpdate = release != null && state.actionFor(release) == InstallAction.Update
            Surface(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().testTag("sprout:settings:self-update"),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasUpdate) "Version ${release!!.versionName} is ready" else "SproutOS is up to date", style = MaterialTheme.typography.titleMedium)
                    if (hasUpdate) {
                        Text("Verified update · ${formatBytes(release!!.sizeBytes)}", style = SproutMono)
                        Button(
                            onClick = { onInstall(release!!) },
                            shape = MaterialTheme.shapes.small,
                        ) { Text("Update SproutOS") }
                        InstallStatusLine(state.installState[release.packageName] ?: InstallState.Idle)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Automatic checks are working.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDetailScreen(
    app: App,
    state: CatalogueState,
    onBack: () -> Unit,
    onInstall: (ReleaseMetadata) -> Unit,
) {
    val action = state.actionFor(app)
    val operation = state.installState[app.packageName] ?: InstallState.Idle
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(app.label, 72)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall)
                    if (app.summary.isNotBlank()) {
                        Text(app.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    app.category?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        item {
            Button(
                onClick = { onInstall(app) },
                enabled = installButtonEnabled(operation, action),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sprout:detail:install"),
            ) { Text(actionLabel(action)) }
        }
        item { InstallStatusLine(operation) }
        item { SectionHeading("Release", null) }
        item {
            FactRow("Version", "${app.versionName} (${app.versionCode})")
            FactRow("Size", formatBytes(app.sizeBytes))
            FactRow("Package", app.packageName)
        }
        item { SectionHeading("Verification", null) }
        item {
            VerificationRow("Signed by SproutOS with this project’s key")
            VerificationRow("Package, version, size, and SHA-256 checked before install")
            Text(app.sha256.chunked(4).joinToString(" "), style = SproutMono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("sprout:detail:digest"))
        }
        item { SectionHeading("Installing from SproutOS", null) }
        item {
            Text(
                "Android asks you to allow installs from SproutOS the first time. SproutOS verifies the release before handing it to Android, and will not replace an app signed by a different key.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onRefresh: () -> Unit, loading: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
private fun SearchLauncher(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sprout:store:search-launcher"),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Search apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(3.dp).height(26.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.testTag("sprout:store:category:${label.lowercase().replace(' ', '-')}")
    )
}

@Composable
private fun AppRow(app: App, state: CatalogueState, onApp: (App) -> Unit, onInstall: (ReleaseMetadata) -> Unit) {
    val action = state.actionFor(app)
    val operation = state.installState[app.packageName] ?: InstallState.Idle
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("sprout:store:app:${app.androidAppId}"),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onApp(app) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(app.label, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                    Text(app.summary.ifBlank { "Version ${app.versionName}" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${app.versionName}  ·  ${formatBytes(app.sizeBytes)}", style = SproutMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                AppActionButton(action, operation) { onInstall(app) }
            }
            if (operation !is InstallState.Idle) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InstallStatusLine(operation, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun CompactAppCard(app: App, state: CatalogueState, onApp: (App) -> Unit, onInstall: (ReleaseMetadata) -> Unit) {
    val action = state.actionFor(app)
    val operation = state.installState[app.packageName] ?: InstallState.Idle
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(164.dp).clickable { onApp(app) },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppMark(app.label, 42)
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.versionName, style = SproutMono)
            AppActionButton(action, operation) { onInstall(app) }
        }
    }
}

@Composable
private fun AppMark(label: String, size: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label.trim().firstOrNull()?.uppercase() ?: "S", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(RoundedCornerShape(2.dp)).background(SproutGrowth))
        }
    }
}

@Composable
private fun AppActionButton(action: InstallAction, operation: InstallState, onClick: () -> Unit) {
    val label = actionLabel(action)
    if (action == InstallAction.Open) {
        OutlinedButton(
            onClick = onClick,
            enabled = installButtonEnabled(operation, action),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text(label) }
    } else {
        Button(
            onClick = onClick,
            enabled = installButtonEnabled(operation, action),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text(label) }
    }
}

private fun actionLabel(action: InstallAction): String =
    when (action) {
        InstallAction.Install -> "Install"
        InstallAction.Update -> "Update"
        InstallAction.Open -> "Open"
        InstallAction.RefuseDowngrade -> "Newer installed"
        InstallAction.RefuseDifferentSigner -> "Signer differs"
    }

@Composable
private fun InstallStatusLine(operation: InstallState, modifier: Modifier = Modifier) {
    installStatusText(operation)?.let { status ->
        Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("sprout:detail:status")) {
            if (operation is InstallState.Downloading) {
                val progress = if (operation.total > 0) operation.bytes.toFloat() / operation.total else 0f
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp))
                Spacer(Modifier.height(6.dp))
            } else if (operation is InstallState.Verifying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                Spacer(Modifier.height(6.dp))
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (operation is InstallState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String) {
    Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChecked(!checked) }.padding(16.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SearchSuggestion(app: App, onClick: (App) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick(app) }.padding(vertical = 12.dp).testTag("sprout:search:suggestion:${app.androidAppId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(app.label, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Text("${app.versionName}  ·  ${formatBytes(app.sizeBytes)}", style = SproutMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SiteRow(site: Site, state: CatalogueState) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable { state.open(site) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(site.name, style = MaterialTheme.typography.titleMedium)
                Text(site.summary.ifBlank { site.url }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Open", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SignInPanel(onSignIn: () -> Unit) {
    Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your SproutOS library", style = MaterialTheme.typography.titleLarge)
            Text("Sign in to install and manage apps from your organizations, open deployed sites, and see available updates.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onSignIn,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("sprout:personal:signin"),
            ) { Text("Sign in") }
        }
    }
}

@Composable
private fun AttentionNotice(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    Surface(border = BorderStroke(2.dp, MaterialTheme.colorScheme.error), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall)
                if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun ErrorNotice(message: String, retry: () -> Unit) {
    Surface(border = BorderStroke(2.dp, MaterialTheme.colorScheme.error), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = retry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingAppRow() {
    Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.fillMaxWidth(0.8f).height(9.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
        Text(value, style = SproutMono, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VerificationRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit += 1
    } while (value >= 1024 && unit < units.lastIndex)
    return String.format(Locale.US, if (value >= 10) "%.0f %s" else "%.1f %s", value, units[unit])
}
