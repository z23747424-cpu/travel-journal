# Native City Atlas Implementation Plan

> For agentic workers: use executing-plans inline in this session. User explicitly requested direct construction of the supplied design. No additional scope or aesthetic interview is needed.

**Goal:** Deliver an offline native, city-level travel collage map with editable fills, planned flags, note previews and compatible backups.

**Architecture:** CityAtlas parses bundled simplified GeoJSON and derives city/marker presentation from existing journals. NativeTravelMapView draws paths, photo clips and markers using Android Canvas. Compose owns accessible controls, selection/preview sheets and journal integration.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.12.01, Android Canvas/View, existing SQLite/JSON and Robolectric 4.14.1. No new runtime library or SDK.

## Global constraints

- Android min 26, target/compile 35; fixed Gradle 8.9, AGP 8.7.3; Java bytecode 17.
- Worktree E:/TravelJournal-city-atlas, branch codex/native-city-atlas. Preserve previous releases.
- No map WebView, tile requests, provider Key or model request in drawing.
- Raw coordinates remain valid outside China; missing regional coverage is shown honestly.
- No AI-module changes. Existing notes, photos, visited state and backup references survive.

## Task 1: Material and compatible styles

Files: scripts/prepare-city-atlas.py; app/src/main/assets/atlas/; Models.kt; Backup.kt; JournalRepository.kt; NativeAtlasTest.kt.

Interfaces: Journal.mapCityCode: String?, mapFillColor: String = #A9BFAE, mapFillMode: String = color. JSON keys share these names. City/photo styles do not own additional photo files.

- [ ] Write regression asserting journalFromJson/journalJson preserve color/photos/cityCode and old records default correctly; run focused test and observe failure.
- [ ] Fetch fixed provincial/city boundary materials, simplify closed rings, retain islands/holes, emit source hashes/license/coverage manifest.
- [ ] Add optional fields, strict value checks, JSON serialization. Existing photo remapping remains sufficient.
- [ ] Verify style round-trip, old JSON, and backup with local photos.

Command: bash scripts/build.sh :app:testDebugUnitTest --tests '*NativeAtlasTest*'.

## Task 2: Atlas geometry and presentation

Files: CityAtlas.kt; NativeAtlasTest.kt.

Interfaces: CityAtlas.load(Context), cityAt(MapPoint): AtlasCity?, cities, provincePaths; AtlasCity(code, name, parentCode, center, polygons). cityPresentations(journals) groups by actual coordinate, latest visited record controls color/mode, unvisited notes produce flags. Viewport converts projected coordinates to/from screen and clamps zoom.

- [ ] Test Hangzhou/Suzhou/Taipei containment, outside points, holes and coordinates on boundaries.
- [ ] Implement simplified local parser with bounded arrays and finite coordinates, reusable projected paths, point-in-ring.
- [ ] Test one Hangzhou visit leaves Ningbo unfilled, unvisited records never fill, updated style wins, marker grouping unfolds with zoom.
- [ ] Implement deterministic presentation without modifying journal data.

## Task 3: Native renderer and interaction

Files: NativeTravelMapView.kt; MapSurface.kt; NativeAtlasTest.kt.

Interfaces: View accepts atlas/journals/decoded images and onPoint/onNotes/onMove/onCamera callbacks; Compose surface retains existing public callback shape. Canvas renderer can draw to Bitmap for validation.

- [ ] Add bitmap render check with a measured 393x600 native view; require visible outlines, distinct city fill pixels and a planned flag.
- [ ] Draw paper background, thin dashed city paths, province outlines, city-clipped color/photo grids, labels and grouped markers. Keep styling constant in screen units.
- [ ] Implement GestureDetector and ScaleGestureDetector; long press selects, taps preview, double tap zooms, long press sticker drags, cancel does not save.
- [ ] Decode photos on IO; bound cache and fall back to color. Expose semantic map description and accessible controls/list.
- [ ] Verify viewport inverse and genuine non-empty Bitmap drawing. Capture clearly labeled native-view proof, not a claimed phone screenshot.

## Task 4: Map page, city selection and notebook styles

Files: MapScreens.kt; MapStyleControls.kt; JournalScreens.kt; MainActivity.kt; DESIGN.md; PRODUCT.md.

Interfaces: MapHome receives existing repository and callbacks plus onSaveMapNote(Journal); MainActivity saves on IO and refreshes. MapLocationPicker stays callable by editor. MapStyleControls edits optional journal fields only.

- [ ] Create expansive map-first layout, compact header, city/flag statistics, minimum 48dp controls and one add action.
- [ ] Add searchable city list; long-press selection sheet; visited/color/photos controls; direct planned-flag save.
- [ ] Preview photograph and story before opening full journal; support same-city list and another entry here.
- [ ] Add fill controls to journal editor, preserve sticker selection and clearing references on photo removal.
- [ ] Update copy and docs to city fill/outlined unvisited/flags. Document source attribution and no coverage guess.

## Task 5: Verification and delivery

Files: app/build.gradle.kts; docs/验证记录-1.3.md; docs/COLLABORATION.md; release notes; existing release directory for new artifacts only.

- [ ] Run assembleDebug, testDebugUnitTest, lintDebug and assembleDebugAndroidTest from final source.
- [ ] Inspect native Bitmap evidence at phone/tablet sizes, normal and large font scale; fix identified rendering defects once and rerun affected checks.
- [ ] Verify package/signature/version and archive contents; export new APK under version 1.3.0/versionCode 4, preserve 1.2.
- [ ] Review changed files and AI boundary, commit feature branch; report native-vs-device verification limits and actual remaining concerns.

Full command: bash scripts/build.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest.
