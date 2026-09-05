#!/usr/bin/env node

import http from "node:http"

const port = Number(process.env.PORT ?? 3001)

/** @param {string} character */
const digest = (character) => character.repeat(64)

/**
 * @typedef {object} FixtureAppInput
 * @property {string} id Fixture app identifier.
 * @property {string} project Fixture project identifier.
 * @property {string} label Display label.
 * @property {string} summary Display summary.
 * @property {string} version Semantic version name.
 * @property {number} code Android version code.
 * @property {number} size APK size in bytes.
 * @property {string} category Catalogue category.
 */

/** @param {FixtureAppInput} input */
const app = (input) => {
  const { id, project, label, summary, version, code, size, category } = input
  return {
    androidAppId: id,
    projectId: project,
    packageName: `me.sproutos.app.p${project.replaceAll("-", "")}`,
    label,
    summary,
    versionName: version,
    versionCode: code,
    sha256: digest(id[0]),
    sizeBytes: size,
    certificateSha256: digest(project[0]),
    downloadUrl: `https://fixtures.invalid/${id}.apk`,
    category,
  }
}

const publicApps = [
  app({
    id: "11900000-0000-7000-8000-000000000001",
    project: "21900000-0000-7000-8000-000000000001",
    label: "Field Notes",
    summary: "Private-first notes for research and field work.",
    version: "1.4.2",
    code: 14,
    size: 8_420_000,
    category: "Personal tools",
  }),
  app({
    id: "31900000-0000-7000-8000-000000000001",
    project: "41900000-0000-7000-8000-000000000001",
    label: "Beacon",
    summary: "Monitor services and receive clear incident updates.",
    version: "2.1.0",
    code: 21,
    size: 12_700_000,
    category: "Developer tools",
  }),
  app({
    id: "51900000-0000-7000-8000-000000000001",
    project: "61900000-0000-7000-8000-000000000001",
    label: "Pocket Ledger",
    summary: "Track shared expenses without giving up your data.",
    version: "1.0.3",
    code: 3,
    size: 6_250_000,
    category: "Finance",
  }),
  app({
    id: "71900000-0000-7000-8000-000000000001",
    project: "81900000-0000-7000-8000-000000000001",
    label: "Trailhead",
    summary: "Plan routes, collect places, and share a trip privately.",
    version: "0.8.0",
    code: 8,
    size: 15_100_000,
    category: "Travel",
  }),
]

const server = http.createServer((request, response) => {
  if (request.url !== "/v1/android/catalogue") {
    response.writeHead(404).end()
    return
  }
  const now = new Date()
  response.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" })
  response.end(
    JSON.stringify({
      version: 2,
      generatedAt: now.toISOString(),
      expiresAt: new Date(now.getTime() + 3_600_000).toISOString(),
      public: { apps: publicApps },
      personal: { apps: [], sites: [] },
    }),
  )
})

server.listen(port, "127.0.0.1", () => {
  process.stdout.write(`SproutOS Android fixture listening on http://127.0.0.1:${port}\n`)
})
