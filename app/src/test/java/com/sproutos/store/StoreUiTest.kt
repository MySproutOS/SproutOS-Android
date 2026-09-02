package com.sproutos.store

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreUiTest {
    @Test
    fun `formats release sizes for people without losing useful precision`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("999 B", formatBytes(999))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("8.0 MB", formatBytes(8L * 1024 * 1024))
        assertEquals("8.0 EB", formatBytes(Long.MAX_VALUE))
    }

    @Test
    fun `catalogue accepts optional category and remains compatible without it`() {
        val common =
            """
            {
              "version": 2,
              "generatedAt": "2026-09-02T00:00:00Z",
              "expiresAt": "2026-09-02T01:00:00Z",
              "public": {"apps": [{
                "androidAppId": "01900000-0000-7000-8000-000000000001",
                "projectId": "01900000-0000-7000-8000-000000000002",
                "packageName": "me.sproutos.app.p01900000000070008000000000000002",
                "label": "Field Notes",
                "summary": "Capture observations",
                "versionName": "1.0.0",
                "versionCode": 1,
                "sha256": "${"a".repeat(64)}",
                "sizeBytes": 1024,
                "certificateSha256": "${"b".repeat(64)}",
                "downloadUrl": "https://fixtures.invalid/field-notes.apk"%s
              }]},
              "personal": {"apps": [], "sites": []}
            }
            """.trimIndent()

        val uncategorized = (parseCatalogue(common.format("")) as CatalogueResult.Ok).catalogue
        val categorized =
            (parseCatalogue(common.format(",\n      \"category\": \"Personal tools\"")) as CatalogueResult.Ok)
                .catalogue

        assertEquals(null, uncategorized.public.apps.single().category)
        assertEquals("Personal tools", categorized.public.apps.single().category)
    }
}
