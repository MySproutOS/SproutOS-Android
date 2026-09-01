package com.sproutos.store

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
          "version": 2,
          "generatedAt": "2026-08-24T12:00:00.000Z",
          "expiresAt": "2026-08-24T13:00:00.000Z",
          "public": { "apps": [
            { "androidAppId":"019d40f0-31d4-7394-90e2-3e20eb3350d1",
              "projectId":"019d40f0-31d4-7394-90e2-3e20eb3350d2",
              "packageName": "me.sproutos.app.p019d40f031d4739490e23e20eb3350d2", "label": "Notes", "summary": "Take notes",
              "versionName": "1.2.0", "versionCode": 7, "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sizeBytes": 1024,
              "certificateSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
              "downloadUrl": "https://cdn/notes.apk" }
          ] },
          "personal": {
            "apps": [
              { "androidAppId":"019d40f0-31d4-7394-90e2-3e20eb3350d3",
                "projectId":"019d40f0-31d4-7394-90e2-3e20eb3350d4",
                "packageName": "me.sproutos.app.p019d40f031d4739490e23e20eb3350d4", "label": "My App", "summary": "",
                "versionName": "0.1.0", "versionCode": 1, "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "sizeBytes": 2048,
                "certificateSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "downloadUrl": "https://cdn/mine.apk" }
            ],
            "sites": [ { "name": "My Site", "url": "https://mine.sproutos.me", "summary": "" } ]
          }
        }
    """.trimIndent()

    @Test
    fun `personal catalogue labels describe entitlement without claiming runtime privacy`() {
        assertEquals("Public", Tab.Public.entryLabel)
        assertEquals("Yours", Tab.Personal.entryLabel)
        assertEquals(
            "Apps and sites available through your SproutOS organizations.",
            Tab.Personal.description,
        )
        assertTrue(!Tab.Personal.description.contains("private", ignoreCase = true))
    }

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
        val extended = body.replace("\"version\": 2", "\"version\": 2, \"somethingNew\": true")

        assertTrue(parseCatalogue(extended) is CatalogueResult.Ok)
    }

    @Test
    fun `refuses a catalogue from the future rather than half-reading it`() {
        /*
          The dangerous kind of incompatibility. A section this build does not know about looks like
          an empty one, and the customer concludes their apps are gone — so it says so instead.
        */
        val newer = body.replace("\"version\": 2", "\"version\": 3")
        val result = parseCatalogue(newer)

        assertTrue(result is CatalogueResult.UnsupportedVersion)
        assertEquals(3, (result as CatalogueResult.UnsupportedVersion).version)
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
            {"version":2,"generatedAt":"t","expiresAt":"t",
             "public":{"apps":[]},"personal":{"apps":[],"sites":[]}}
        """.trimIndent()

        val result = parseCatalogue(anonymous)
        assertTrue(result is CatalogueResult.Ok)
        assertTrue(personalEntries((result as CatalogueResult.Ok).catalogue).isEmpty())
    }

    @Test
    fun `refuses package names not derived from the immutable project id`() {
        val forged = body.replace(
            "me.sproutos.app.p019d40f031d4739490e23e20eb3350d2",
            "me.attacker.lookalike",
        )
        assertTrue(parseCatalogue(forged) is CatalogueResult.Malformed)
    }

    @Test
    fun `reads only a signed DB backed SproutOS client update`() {
        val withUpdate =
            body.replace(
                "\"public\":",
                """
                "clientUpdate": {
                  "packageName":"com.sproutos.store", "versionName":"0.2.0", "versionCode":2,
                  "sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                  "sizeBytes":4096,
                  "certificateSha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                  "downloadUrl":"https://cdn/sproutos.apk", "required":true
                },
                "public":
                """.trimIndent(),
            )
        val parsed = parseCatalogue(withUpdate) as CatalogueResult.Ok
        assertEquals("com.sproutos.store", parsed.catalogue.clientUpdate?.packageName)
        assertEquals(2L, parsed.catalogue.clientUpdate?.versionCode)
        assertEquals(true, parsed.catalogue.clientUpdate?.required)

        val forged = withUpdate.replace("com.sproutos.store", "me.attacker.client")
        assertTrue(parseCatalogue(forged) is CatalogueResult.Malformed)
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
