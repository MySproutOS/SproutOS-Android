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
    val clientUpdate: ClientRelease? = null,
)

@Serializable
data class PublicSection(val apps: List<App> = emptyList())

@Serializable
data class PersonalSection(
    val apps: List<App> = emptyList(),
    val sites: List<Site> = emptyList(),
)

interface ReleaseMetadata {
    val packageName: String
    val label: String
    val versionName: String
    val versionCode: Long
    val sha256: String
    val sizeBytes: Long
    val certificateSha256: String
    val downloadUrl: String
}

@Serializable
data class ClientRelease(
    override val packageName: String,
    override val versionName: String,
    override val versionCode: Long,
    override val sha256: String,
    override val sizeBytes: Long,
    override val certificateSha256: String,
    override val downloadUrl: String,
    val required: Boolean = false,
) : ReleaseMetadata {
    override val label: String get() = "SproutOS"
}

@Serializable
data class App(
    val androidAppId: String,
    val projectId: String,
    override val packageName: String,
    override val label: String,
    val summary: String = "",
    override val versionName: String,
    override val versionCode: Long,
    override val sha256: String,
    override val sizeBytes: Long,
    override val certificateSha256: String,
    override val downloadUrl: String,
    val iconUrl: String? = null,
) : ReleaseMetadata

@Serializable
data class Site(val name: String, val url: String, val summary: String = "")

/** The one version this build understands. */
const val SUPPORTED_VERSION: Int = 2

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
    data class UnsupportedVersion(val version: Int) : CatalogueResult

    data object Unauthorized : CatalogueResult

    data class Malformed(val reason: String) : CatalogueResult
}

fun parseCatalogue(body: String): CatalogueResult =
    try {
        val catalogue = json.decodeFromString<Catalogue>(body)
        if (catalogue.version != SUPPORTED_VERSION) {
            CatalogueResult.UnsupportedVersion(catalogue.version)
        } else {
            validateCatalogue(catalogue)?.let(CatalogueResult::Malformed)
                ?: CatalogueResult.Ok(catalogue)
        }
    } catch (cause: Exception) {
        CatalogueResult.Malformed(cause.message ?: "could not read the catalogue")
    }

private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
private val sha256 = Regex("^[0-9a-f]{64}$")

fun packageNameFor(projectId: String): String =
    "me.sproutos.app.p${projectId.replace("-", "")}".lowercase()

/** Refuse internally inconsistent release metadata before any bytes are downloaded. */
private fun validateCatalogue(catalogue: Catalogue): String? {
    val apps = catalogue.public.apps + catalogue.personal.apps
    for (app in apps) {
        if (!uuid.matches(app.androidAppId) || !uuid.matches(app.projectId)) {
            return "an Android app identifier is not a UUID"
        }
        if (app.packageName != packageNameFor(app.projectId)) {
            return "an Android package does not match its immutable project identifier"
        }
        if (app.versionCode <= 0 || app.versionName.isBlank()) return "an Android version is invalid"
        if (!sha256.matches(app.sha256) || !sha256.matches(app.certificateSha256)) {
            return "an Android release digest is not canonical SHA-256"
        }
        if (app.sizeBytes <= 0) return "an Android release size is invalid"
        val download = runCatching { java.net.URI(app.downloadUrl) }.getOrNull()
        if (download?.scheme != "https" || download.host.isNullOrBlank()) {
            return "an Android release URL is not HTTPS"
        }
    }
    if (
        catalogue.public.apps.groupingBy(App::androidAppId).eachCount().any { it.value > 1 } ||
            catalogue.personal.apps.groupingBy(App::androidAppId).eachCount().any { it.value > 1 }
    ) {
        return "an Android app appears more than once"
    }
    for (site in catalogue.personal.sites) {
        val target = runCatching { java.net.URI(site.url) }.getOrNull()
        if (target?.scheme != "https" || target.host.isNullOrBlank()) {
            return "a personal site URL is not HTTPS"
        }
    }
    catalogue.clientUpdate?.let { release ->
        if (release.packageName != "me.sproutos.client") {
            return "the SproutOS client package is invalid"
        }
        if (release.versionCode <= 0 || release.versionName.isBlank()) {
            return "the SproutOS client version is invalid"
        }
        if (!sha256.matches(release.sha256) || !sha256.matches(release.certificateSha256)) {
            return "the SproutOS client release digest is not canonical SHA-256"
        }
        if (release.sizeBytes <= 0) return "the SproutOS client release size is invalid"
        val download = runCatching { java.net.URI(release.downloadUrl) }.getOrNull()
        if (download?.scheme != "https" || download.host.isNullOrBlank()) {
            return "the SproutOS client release URL is not HTTPS"
        }
    }
    return null
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
