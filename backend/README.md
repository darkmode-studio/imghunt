# ImgHunt search gateway: personal beta

The Android UI remains native. This service protects the Brave provider credential. No server or subscription is activated by committing these files.

## Setup

Configure BRAVE_SEARCH_API_KEY in the hosting server's secret settings. Configure a separate strong random IMGHUNT_CLIENT_TOKEN with at least 32 random bytes. Never put the Brave key in Android, repository source, or chat.

Install requirements.txt and run uvicorn app:app --host 0.0.0.0 --port 8080 --workers 1, or use the Dockerfile. Use a trusted HTTPS reverse proxy with request-size limits.

In Android Search settings, enter the HTTPS gateway base URL and the separate app access token, not the Brave key. The app encrypts this access token using Android Keystore.

GET /health reports configuration status. GET /v1/images/search requires Bearer authorization and accepts q and count (up to 200).

## Limits and public-release requirements

Brave image search has no upstream pagination. Android progressively reveals one response and does not repeat a paid request for each scroll. The server does not persist or cache search responses. SafeSearch is strict. Web images have unknown reuse rights and source hosts may block downloads.

This personal beta uses one rotating token, four concurrent calls and an instance-wide in-memory 20 requests/minute limit. Use one worker. Public release requires real user authentication, per-user quotas, distributed rate limiting, monitoring and a security/provider-terms review. Never embed a shared access token in an APK or log authorization headers.

## Tests

Install requirements-dev.txt and run python -m pytest -q from this directory. Tests use a mocked provider with no Brave charges. Hosting, real credentials and live phone testing remain separate steps.
