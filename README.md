# Eporner Gay CloudStream Extension

An unofficial, adults-only CloudStream extension. It uses Eporner's public v2 API with the gay-only filter for discovery.

The default section is **Amateur**, sorted by the most recently added videos. Search results also default to newest-first.

## Important

- Strictly 18+ / NSFW.
- This project does not host media.
- Confirm Eporner's current API, embedding and redistribution terms before publishing.
- Respect removals and do not bypass authentication, paywalls or access controls.
- Availability may depend on your country, network and Eporner's API.

## Build

```bash
./gradlew EpornerGay:make
```

The resulting `.cs3` file is written under `EpornerGay/build/`.

## Publish

1. Push this project to a public GitHub repository named `eporner-gay-cloudstream-`.
3. Enable GitHub Actions with read/write workflow permissions.
4. Push to `main`; the included workflow builds and publishes the files to the `builds` branch.
5. Point users to the raw `repo.json` URL.

This extension is intentionally marked beta until catalogue and playback are tested on a real Android device.
