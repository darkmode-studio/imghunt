# ImgHunt native Android beta

Kotlin and Jetpack Compose image search with native navigation, a full-screen viewer, saved images and background downloads. This branch is a first development milestone, not a production-ready release.

## Broader web images

Configure and host the service in backend/ separately. Enter its HTTPS URL and separate app access token in Android Search settings. The Brave provider key stays on the server. No backend or subscription is activated by this repository.

Brave returns up to 200 images per query, not unlimited paginated results. Android reveals that batch progressively without a new provider call per scroll. Original-image URLs, source-page links and unknown reuse rights remain distinct. SafeSearch is strict. Missing configuration is shown as setup, not as no search results.

Open catalogs mode is still available without a gateway, using the selected catalog sources. Optional stock services require your own source key. Source-specific continuation and visible error states replace silent fallback.

## Beta APK

The package is com.sandeep.imghunt.beta and the launcher label is ImgHunt Beta. It installs beside the old app; old app data is not migrated. The release variant uses a configured keystore or the existing development-key fallback, not a store-ready production signing setup.

## Verification

CI runs mocked gateway tests, Android unit tests, lint, device-test compilation, and APK builds. Tests under app/src/androidTest must still be RUN on an emulator or phone using ./gradlew connectedDebugAndroidTest. Compiling them is not a device pass. Live provider access needs actual hosting and credentials.

Before public release: live phone/provider tests, accessibility and performance checks, production signing, per-user gateway authentication and quotas, privacy documentation, and security review.
