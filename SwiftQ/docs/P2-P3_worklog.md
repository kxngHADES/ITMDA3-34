# P2 / P3 worklog (append-only)

## Phase 0 — investigation (2026-10-13)
- P1 app is **Jetpack Compose** (Material3 + SwiftQTheme tokens in `ui/theme`), not XML. Package `com.example.swiftq`, minSdk 30, compileSdk 37, AGP 9.2.1, Kotlin 2.4.20. Languages: en + af only. No DI, no Firebase, no Room/Retrofit yet. Navigation = enum state in MainActivity.
- Tooling: no `java`/`adb` on PATH. Using Android Studio JBR (JDK 21) and ~/Library/Android/sdk. AVD `Pixel_10_Pro_XL` (arm64, API from system-images) booted in background.
- API code read (routes/v1, services, schemas). Dev server /openapi.json fetched to confirm what is actually deployed.
- **Big findings** (details in PDF): dev API has no GET /v1/clinics, no /v1/sync/push|pull, no /v1/tickets/mine, and no way for a patient to obtain a token (only staff email/password login issues an HS256 JWT; no anonymous/Firebase exchange). `GET /v1/clinics/{id}/service-points` and `POST /v1/queues/{id}/tickets` do exist.
- Figma frames 02–07 = Home, Home(active ticket), Select clinic, Priority, Confirm join, You're in. No service-point frame and no name field; added service-point step (API needs a queue per service point).
- Build green (KSP+Room ok). Mock contract server at docs/tools/mock_api.py (dev API cannot serve patient flow).
- Emulator runs done vs mock: online join, airplane join+reconnect, kill mid-flight replay (same key/body, 1 ticket), paused queue, double tap (1 POST), back from status->Home, AF screens. Real dev API: clinics list shows error state (GET /v1/clinics 404).
- Tests: 25 JVM + 5 instrumented pass. PDF docs/P2-P3_Handback.pdf built with headless Chrome and checked (screenshots render). Done.
