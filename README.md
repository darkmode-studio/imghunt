# imghunt

Standalone Android image search and direct high-resolution downloader built with Kotlin, Jetpack Compose, Room, WorkManager, and Coil 3.

## Features
- **API-First Search & Automatic Fallback:** Queries Openverse, Wikimedia Commons, Internet Archive, NASA Images, and Met Museum out of the box (plus optional API keys for Unsplash, Pexels, and Pixabay), automatically trying fallback providers and query variants when results are thin.
- **Accurate Resolution, Aspect & Format Filters:** Checks both long and short sides against resolution thresholds (such as 4K), handles missing dimensions cleanly, and detects real image formats from MIME types and file magic bytes.
- **Pagination, Sorting, Favourites & History:** Load more pages (`Load more`), sort by relevance or resolution, keep search suggestions/history, save favourites, and persist UI state across screen rotations via `HuntViewModel`.
- **Smooth HQ Downloads & Viewer:** Non-blocking batch queuing, live `bytesDone` progress bars, retry/cancel/open saved file actions, pinch/double-tap zoom, share, and Set as Wallpaper in the viewer.
