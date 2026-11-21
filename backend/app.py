"""ImgHunt personal-beta gateway. Provider credentials stay on the server."""
from __future__ import annotations
import asyncio
import hashlib
import hmac
import html
import ipaddress
import os
import re
import time
from collections import deque
from contextlib import asynccontextmanager
from dataclasses import dataclass
from urllib.parse import urlsplit, urlunsplit
import httpx
from fastapi import FastAPI, Header, HTTPException, Query

BRAVE_URL = "https://api.search.brave.com/res/v1/images/search"

@dataclass(frozen=True)
class Settings:
    brave_key: str = ""
    client_token: str = ""
    requests_per_minute: int = 20

    @classmethod
    def from_env(cls):
        return cls(brave_key=os.environ.get("BRAVE_SEARCH_API_KEY", "").strip(),
                   client_token=os.environ.get("IMGHUNT_CLIENT_TOKEN", "").strip())

def public_url(value, *, image=False):
    if not isinstance(value, str) or len(value) > 8192 or any(ord(c) < 32 for c in value):
        return None
    try:
        parts = urlsplit(value.strip())
        if parts.scheme not in (("https",) if image else ("http", "https")):
            return None
        host = (parts.hostname or "").lower()
        if not host or parts.username or parts.password or host == "localhost" or host.endswith((".localhost", ".local")):
            return None
        try:
            if not ipaddress.ip_address(host).is_global:
                return None
        except ValueError:
            if "." not in host:
                return None
        _ = parts.port
        return urlunsplit((parts.scheme, parts.netloc, parts.path, parts.query, ""))
    except ValueError:
        return None

def dimension(value):
    try:
        return min(max(int(value), 0), 100000)
    except (ValueError, TypeError, OverflowError):
        return 0

def normalize(payload, query, count):
    if not isinstance(payload, dict) or not isinstance(payload.get("results"), list):
        raise ValueError("Invalid search response")
    images, seen = [], set()
    for result in payload["results"]:
        if not isinstance(result, dict):
            continue
        properties, thumbnail = result.get("properties") or {}, result.get("thumbnail") or {}
        if not isinstance(properties, dict) or not isinstance(thumbnail, dict):
            continue
        image_url = public_url(properties.get("url"), image=True)
        page_url = public_url(result.get("url"))
        if not image_url or not page_url or image_url in seen:
            continue
        seen.add(image_url)
        title = html.unescape(re.sub(r"<[^>]*>", "", str(result.get("title") or "Image")))[:500]
        images.append({
            "id": "web_" + hashlib.sha256(image_url.encode()).hexdigest()[:24],
            "title": title,
            "thumbnailUrl": public_url(thumbnail.get("src"), image=True) or image_url,
            "imageUrl": image_url, "sourcePageUrl": page_url,
            "sourceName": urlsplit(page_url).hostname or "Web",
            "width": dimension(properties.get("width")), "height": dimension(properties.get("height")),
            "isOpenLicense": False, "licenseLabel": "Rights unknown; check the source",
        })
        if len(images) >= count:
            break
    qdata = payload.get("query")
    altered = qdata.get("altered") if isinstance(qdata, dict) else None
    return {"query": query, "correctedQuery": altered[:400] if isinstance(altered, str) else None,
            "provider": "Brave Image Search", "items": images, "hasMore": False, "maxResults": 200}

def create_app(settings=None, transport=None):
    config = settings or Settings.from_env()
    requests = deque()
    gate = asyncio.Semaphore(4)

    @asynccontextmanager
    async def lifespan(application):
        async with httpx.AsyncClient(transport=transport, timeout=httpx.Timeout(12.0, connect=5.0),
                limits=httpx.Limits(max_connections=4, max_keepalive_connections=4), follow_redirects=False) as client:
            application.state.client = client
            yield

    application = FastAPI(title="ImgHunt Search Gateway", version="0.1.0", lifespan=lifespan,
                          docs_url=None, redoc_url=None, openapi_url=None)

    @application.middleware("http")
    async def security_headers(request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @application.get("/health")
    async def health():
        return {"ok": True, "searchConfigured": bool(config.brave_key and len(config.client_token) >= 24)}

    @application.get("/v1/images/search")
    async def search(q: str = Query(min_length=1, max_length=400),
                     count: int = Query(default=200, ge=1, le=200),
                     authorization: str | None = Header(default=None)):
        if not config.brave_key or len(config.client_token) < 24:
            raise HTTPException(503, "Search is not configured. Contact the server administrator.")
        expected = "Bearer " + config.client_token
        if not authorization or not hmac.compare_digest(authorization.encode(), expected.encode()):
            raise HTTPException(401, "A valid app access token is required.", headers={"WWW-Authenticate": "Bearer"})
        query = q.strip()
        if not query or len(query.split()) > 50:
            raise HTTPException(422, "Use a query of 1 to 50 words, up to 400 characters.")
        now = time.monotonic()
        while requests and requests[0] <= now - 60:
            requests.popleft()
        if len(requests) >= config.requests_per_minute:
            raise HTTPException(429, "Search limit reached. Try again in a minute.", headers={"Retry-After": "60"})
        requests.append(now)
        try:
            await asyncio.wait_for(gate.acquire(), timeout=0.2)
        except TimeoutError:
            raise HTTPException(503, "Search is busy. Try again shortly.") from None
        try:
            response = await application.state.client.get(BRAVE_URL,
                params={"q": query, "count": count, "safesearch": "strict", "country": "ALL", "search_lang": "en"},
                headers={"X-Subscription-Token": config.brave_key, "Accept": "application/json", "User-Agent": "ImgHunt-Gateway/0.1"})
            if response.status_code == 429:
                raise HTTPException(429, "The search provider quota is temporarily unavailable.", headers={"Retry-After": "60"})
            if response.status_code != 200:
                raise HTTPException(502, "The search provider could not complete this request.")
            try:
                return normalize(response.json(), query, count)
            except (ValueError, TypeError):
                raise HTTPException(502, "The search provider returned an invalid response.") from None
        except httpx.TimeoutException:
            raise HTTPException(504, "The search provider timed out. Please retry.") from None
        except httpx.HTTPError:
            raise HTTPException(502, "The search provider is unavailable. Please retry.") from None
        finally:
            gate.release()
    return application

app = create_app()
