package me.sproutos.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The catalogue, as the platform serves it.
 *
 * Mirrors `lib/typescript/services/src/android-index.ts`. The two are one contract, so the field
 * names here are the JSON's exactly — a rename on either side is an app that shows an empty tab and
 * says nothing about why.
 */
@Serializable
data class Catalogue(
    val version: Int,
    val generatedAt: String,
    val expiresAt: String,
    val public: PublicSection = PublicSection(),
    val personal: PersonalSection = PersonalSection(),
)

@Serializable
data class PublicSection(val apps: List<App> = emptyList())

@Serializable
data class PersonalSection(
    val apps: List<App> = emptyList(),
    val sites: List<Site> = emptyList(),
)

@Serializable
data class App(
    val packageName: String,
    val label: String,
    val summary: String = "",
    val versionName: String,
    val versionCode: Long,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val iconUrl: String? = null,
)

@Serializable
data class Site(val name: String, val url: String, val summary: String = "")

/** The one version this build understands. */
const val SUPPORTED_VERSION: Int = 1

/**
 * Parsing is lenient about unknown keys and strict about the version.
 *
 * Unknown keys are ignored so the platform can add a field without breaking every installed copy of
 * this app — which it will, and which should not require an update.
 *
 * The version is not. A newer catalogue read by this build would be *partially* understood: a
 * section it does not know about looks like an empty one, and a customer concludes their apps are
 * gone. Refusing is the honest failure.
 */
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

sealed interface CatalogueResult {
    data class Ok(val catalogue: Catalogue) : CatalogueResult

    /** The platform speaks a version this build does not. The app must ask to be updated. */
    data class TooNew(val version: Int) : CatalogueResult

    data class Malformed(val reason: String) : CatalogueResult
}

fun parseCatalogue(body: String): CatalogueResult =
    try {
        val catalogue = json.decodeFromString<Catalogue>(body)
        if (catalogue.version != SUPPORTED_VERSION) {
            CatalogueResult.TooNew(catalogue.version)
        } else {
            CatalogueResult.Ok(catalogue)
        }
    } catch (cause: Exception) {
        CatalogueResult.Malformed(cause.message ?: "could not read the catalogue")
    }

/** One row in a tab: an app to install, or a site to open. */
sealed interface Entry {
    val title: String
    val subtitle: String

    data class Installable(val app: App) : Entry {
        override val title: String get() = app.label
        override val subtitle: String get() = app.summary.ifBlank { app.versionName }
    }

    data class Website(val site: Site) : Entry {
        override val title: String get() = site.name
        override val subtitle: String get() = site.summary.ifBlank { site.url }
    }
}

/**
 * Search across both kinds.
 *
 * Apps and sites together, because somebody who built a site and an app on SproutOS thinks of them
 * as one project rather than two catalogues — and a search that covered only half would leave them
 * concluding the other half is missing.
 *
 * Every term must match, so a second word narrows.
 */
fun search(entries: List<Entry>, query: String): List<Entry> {
    val terms = query.lowercase().split(" ").filter { it.isNotBlank() }
    if (terms.isEmpty()) return entries

    return entries.filter { entry ->
        val haystack = "${entry.title} ${entry.subtitle}".lowercase()
        terms.all { haystack.contains(it) }
    }
}

fun publicEntries(catalogue: Catalogue): List<Entry> =
    catalogue.public.apps.map { Entry.Installable(it) }

/** Apps first, then sites: the tab is called "personal" and an app is the thing you install. */
fun personalEntries(catalogue: Catalogue): List<Entry> =
    catalogue.personal.apps.map { Entry.Installable(it) } +
        catalogue.personal.sites.map { Entry.Website(it) }
