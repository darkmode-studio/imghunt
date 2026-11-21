# ImgHunt Browser Beta

A native Android app with a visible in-app browser and a native image tray. This version opens directly into Browse. No gateway URL, app access token, paid API key or server setup is required by the normal app interface.

## Use

1. Open **ImgHunt Browser Beta** and search Google Images, or enter a public HTTPS website.
2. Long-press an image and choose **Add to tray**, or tap **Collect loaded images**.
3. Open the native image tray, preview an image, and use the download button to keep a local copy in Pictures/ImgHunt.

Phones show the tray beneath the browser; wider screens show it beside the browser. Catalogs, saved-image bookmarks and native downloads remain separate tabs. The temporary tray is not permanent storage. The heart button saves a bookmark; downloading saves a local file.

## Honest limits

- This browser has an app-local session, not access to your existing Chrome profile or cookies. Google may require an external browser for sign-in. Use Open browser when a site cannot load in WebView.
- Collection is user-triggered and limited to up to 40 supported loaded images at a time, with an 80-image temporary tray. It does not automatically scroll, read hidden frames, bypass CAPTCHAs, or guarantee full-resolution originals.
- Supported embedded previews can be copied into private app cache. Downloads use public HTTPS links or those app-owned cached previews; browser cookies and account credentials are not copied into download jobs. Login-protected images may not save.
- An image result is not a reuse license. Check the source. Unsupported files, HTML disguised as images and oversized downloads are rejected.

## Installation and checks

The browser beta uses package com.sandeep.imghunt.browserbeta and installs separately from the older app/beta without deleting their data. It is a development build, not a store-ready production release. Production signing still requires your own securely managed keystore.

The workflow runs unit tests, lint, APK builds and Android-emulator UI tests using an offline HTML/image fixture. See the workflow run for actual pass/fail status. Live Google/site behavior still needs testing on your phone. The older gateway source is retained for experiments but is not required by this free browser flow.
