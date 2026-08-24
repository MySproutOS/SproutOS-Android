package me.sproutos.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue contract, from this side.
 *
 * The platform's own tests assert what it emits; these assert what this build accepts. Both sides
 * of one contract need a test, because a rename on either is a tab that shows nothing and explains
 * nothing.
 */
class CatalogueTest {
    private val body = """
        {
          "version": 1,
          "generatedAt": "2026-08-24T12:00:00.000Z",
          "expiresAt": "2026-08-24T13:00:00.000Z",
          "public": { "apps": [
            { "packageName": "me.sproutos.notes", "label": "Notes", "summary": "Take notes",
              "versionName": "1.2.0", "versionCode": 7, "sha256": "abc", "sizeBytes": 1024,
              "downloadUrl": "https://cdn/notes.apk" }
          ] },
          "personal": {
            "apps": [
              { "packageName": "me.sproutos.mine", "label": "My App", "summary": "",
                "versionName": "0.1.0", "versionCode": 1, "sha256": "def", "sizeBytes": 2048,
                "downloadUrl": "https://cdn/mine.apk" }
            ],
            "sites": [ { "name": "My Site", "url": "https://mine.sproutos.me", "summary": "" } ]
          }
        }
    """.trimIndent()

    @Test
    fun `reads what the platform emits`() {
        val result = parseCatalogue(body)
        assertTrue(result is CatalogueResult.Ok)

        val catalogue = (result as CatalogueResult.Ok).catalogue
        assertEquals(1, catalogue.public.apps.size)
        assertEquals("Notes", catalogue.public.apps[0].label)
        assertEquals(7L, catalogue.public.apps[0].versionCode)
        assertEquals(1, catalogue.personal.sites.size)
    }

    @Test
    fun `ignores a field it has never heard of`() {
        // The platform will add fields, and that must not break every installed copy of this app.
        val extended = body.replace("\"version\": 1", "\"version\": 1, \"somethingNew\": true")

        assertTrue(parseCatalogue(extended) is CatalogueResult.Ok)
    }

    @Test
    fun `refuses a catalogue from the future rather than half-reading it`() {
        /*
          The dangerous kind of incompatibility. A section this build does not know about looks like
          an empty one, and the customer concludes their apps are gone — so it says so instead.
        */
        val newer = body.replace("\"version\": 1", "\"version\": 2")
        val result = parseCatalogue(newer)

        assertTrue(result is CatalogueResult.TooNew)
        assertEquals(2, (result as CatalogueResult.TooNew).version)
    }

    @Test
    fun `reports malformed input rather than throwing into the UI`() {
        assertTrue(parseCatalogue("{not json") is CatalogueResult.Malformed)
        assertTrue(parseCatalogue("") is CatalogueResult.Malformed)
    }

    @Test
    fun `an empty personal section is empty, not absent`() {
        // What an unauthenticated reader gets. The tab should say there is nothing there rather
        // than fail to load.
        val anonymous = """
            {"version":1,"generatedAt":"t","expiresAt":"t",
             "public":{"apps":[]},"personal":{"apps":[],"sites":[]}}
        """.trimIndent()

        val result = parseCatalogue(anonymous)
        assertTrue(result is CatalogueResult.Ok)
        assertTrue(personalEntries((result as CatalogueResult.Ok).catalogue).isEmpty())
    }

    @Test
    fun `searches apps and sites together`() {
        val catalogue = (parseCatalogue(body) as CatalogueResult.Ok).catalogue
        val entries = personalEntries(catalogue)

        /*
          Somebody who built a site and an app thinks of them as one project. A search covering only
          apps would leave them concluding the site is missing.
        */
        assertEquals(1, search(entries, "site").size)
        assertEquals(1, search(entries, "my app").size)
        assertEquals(2, search(entries, "my").size)
    }

    @Test
    fun `adding a word narrows the results`() {
        val catalogue = (parseCatalogue(body) as CatalogueResult.Ok).catalogue
        val entries = personalEntries(catalogue)

        assertTrue(search(entries, "my app").size <= search(entries, "my").size)
        assertEquals(entries.size, search(entries, "").size)
    }

    @Test
    fun `falls back to something useful when an app has no summary`() {
        val catalogue = (parseCatalogue(body) as CatalogueResult.Ok).catalogue
        val mine = personalEntries(catalogue).first()

        // A blank second line looks like a broken row; the version is at least true.
        assertEquals("0.1.0", mine.subtitle)
    }
}
