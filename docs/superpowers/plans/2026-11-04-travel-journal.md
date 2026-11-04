# Travel Journal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use checkbox syntax.

**Goal:** Deliver a signed installable Android travel journal with local records, configurable AI, semantic search and destination recommendations.

**Architecture:** Kotlin/Compose native Android app. SQLite and private photo files hold user content; Android Keystore protects the optional AI credential. Structured destination catalog constrains model recommendations. The UI calls one repository and one AI service, with no application backend.

**Tech Stack:** Android SDK 35, Kotlin, Compose Material3, SQLiteOpenHelper, HttpURLConnection, coroutines, JUnit/Robolectric where Android services are needed.

**Spec:** docs/superpowers/specs/2026-11-04-travel-journal-design.md

## Global Constraints

- Android 8.0 minimum; Chinese user-facing UI; magazine theme first.
- Maximum 12 photos per entry and 4 photos per AI call.
- Optional user-supplied HTTPS OpenAI-compatible endpoint; no bundled API key.
- Models never modify saved records directly; no AI call for ordinary browsing.
- Photos, wishes, journals and backups are local; export excludes credentials.
- Explicit real API, simulated API, build, emulator and device evidence boundaries.

## Review Focus

- Photo picker cancellation, rotation, and returning from the background preserve a draft or warn before loss.
- Wrong endpoint, invalid model response and API errors keep user input editable.
- Imported archives cannot traverse paths or overwrite existing content.
- A wished-for destination never appears as an already completed trip.
- Unsupported regions and unknown model IDs cannot become invented catalog entries.

## Shared interfaces

Package `cn.edu.traveljournal`. Files under `app/src/main/java/cn/edu/traveljournal/`.

`Journal(id:String,title:String,date:String,place:String,body:String,photos:List<String>,tags:List<String>,photoDescription:String,createdAt:Long,updatedAt:Long,wishId:String?)`.

`Destination(id:String,name:String,region:String,tags:List<String>,summary:String,days:String,season:String,sourceUrl:String,checkedAt:String)`.

`Wish(id:String,destination:Destination,query:String,reason:String,notes:String,visited:Boolean,journalId:String?,createdAt:Long)`.

`AiConfig(baseUrl:String,apiKey:String,model:String,spiritName:String,consent:Boolean)`.

`SearchHit(id:String,reason:String)`; `PhotoSuggestion(tags:List<String>,description:String,title:String)`; `Recommendation(id:String,reason:String,caution:String)`; `RecommendationResult(understood:String,items:List<Recommendation>)`.

`JournalRepository(context)` exposes `journals():List<Journal>`, `saveJournal(j:Journal)`, `deleteJournal(id:String)`, `wishes():List<Wish>`, `saveWish(w:Wish)`, `deleteWish(id:String)`, `destinations():List<Destination>`, `photoFile(name:String):File`, `importPhoto(uri:Uri):String`, `loadConfig():AiConfig`, `saveConfig(c:AiConfig)`, `exportBackup(out:OutputStream)`, `importBackup(input:InputStream):Int`.

`AiClient` exposes suspend `test(config):String`, `describe(config,photos:List<File>,context:String):PhotoSuggestion`, `search(config,query:String,journals:List<Journal>):List<SearchHit>`, `recommend(config,query:String,destinations:List<Destination>,excluded:Set<String>):RecommendationResult`, `recall(config,journal):String`. Exceptions contain readable messages with no credentials. Cancellation propagates.

`localSearch(query:String,journals:List<Journal>):List<SearchHit>` is separate from model search. Constructor defaults are supplied for optional model fields so drafts can be concise.

### Task 1: Buildable native shell and reproducible tooling

Files: root Gradle/wrapper configuration, app Gradle/manifest/resources, scripts/build.sh, .gitignore.

- [x] Check existing tools; set up WSL Java/Android build tools, verify official compatibility notes; fall back to existing Windows SDK only if needed, recording reason.
- [x] Configure a minimal Compose activity with Android SDK 35/min 26 and no cloud backup or cleartext traffic.
- [x] Run initial compilation; retain exact build commands and output.

### Task 2: Local content, credentials and backup

Files: Models.kt, JournalRepository.kt, Backup.kt; Android-aware tests.

- [ ] Write and observe failing checks for persistence, deleted records, wish/trip separation, path traversal and secret-free backup.
  - Process exception: tests are implemented and pass, but were not observed failing before production implementation because the initial toolchain was unavailable. Later failures and regression fixes do not establish that earlier sequence.
- [x] Implement the shared interfaces, transactional SQLite persistence, copied photos, Keystore credentials and bounded safe archive import.
- [x] Run repository tests; verify backup round trip with photos and wishes, and invalid import rollback.

### Task 3: Model integration and ranking

Files: AiClient.kt, Search.kt; response/parser and local ranking tests.

- [ ] Write and observe failing checks for endpoint normalization, unknown IDs, malformed response and Chinese local search.
  - Process exception: a later replay against preserved skeletons failed 5/5 and current code passed 5/5. This is sensitivity evidence, not proof of a pre-implementation red/green sequence; see docs/核心检查.md.
- [x] Implement cancellable requests with no cross-domain redirects, constrained JSON parsing, 4-photo downscaling and tag suggestions.
- [x] Implement full-scope batched semantic search (20/batch, max 100), destination recommendation over known IDs, recall and explicit error handling.
- [x] Run tests with representative model-shaped responses; report real provider calls separately.

### Task 4: Sourced destination catalog and experiment materials

Files: assets/destinations.json, docs/目的地资料来源.md, experiments/*.

- [x] Verify 30 domestic destination facts from official tourism sources; store source and review date per entry.
- [x] Provide static general information, no invented live prices or availability.
- [x] Create reproducible retrieval/recommendation evaluation inputs and metric script; distinguish pending model measurements from measured baseline.

### Task 5: Complete magazine UI

Files: MainActivity.kt, JournalScreens.kt, DiscoverScreens.kt, SettingsScreen.kt, Ui.kt.

- [x] Implement four tabs, photos-first home, optional clearly labeled examples, journal creation/detail/edit/delete and photo viewing/reordering.
- [x] Implement draft save/abandon behavior, source-aware photo import, suggestion confirmation and offline states.
- [x] Implement separate local/AI search, inspiration needs input, recommendations, reroll exclusions, wishlist and wish-to-journal drafts.
- [x] Implement named spirit, validated model settings, consent, connection test and import/export through the system picker.
- [x] Verify app startup and major workflows on an available emulator/device or document the exact unavailable runtime.
  - No adb device, emulator executable or system image; JVM Activity smoke passed, device and visual acceptance remain unexecuted.

### Task 6: Review and deliver

Files: README.md, docs/验证记录.md, release/*, plan ledger.

- [x] Run app compilation, relevant tests and Android lint; fix build/runtime failures with bounded regression checks.
- [x] Request fresh whole-project review and resolve important findings.
- [x] Build signed APK, verify signature/package and provide source plus user-facing setup instructions.
- [x] Record screenshots and runtime evidence if an emulator is available; clearly list real API and physical-device gaps.

Final evidence (2026-11-05): 31/31 Gradle tests, lint 0 errors/7 dependency-version warnings, app and device-test APK compilation, APK v2 signature/package verification, two offline Python material checks, WSL launcher check. No screenshots or real provider measurements were fabricated. See docs/验证记录.md and release/交付说明.md.

## Execution rulings

- User's “开始制作” authorizes implementation of the revised design; proceed without another permission round.
- Current dedicated `codex/travel-journal-design` branch has only this project's documents and no pre-existing code. Continue here with app in `app/`, preserving original documents in Git; another worktree would add no isolation value for this empty project.
- Use inline implementation plus focused independent catalog/tooling/core work and a fresh final reviewer; tasks have disjoint file ownership.
