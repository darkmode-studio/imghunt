import httpx
import pytest
from fastapi.testclient import TestClient
from app import Settings, create_app, normalize, public_url

TOKEN = "test-access-token-not-for-deployment"
AUTH = {"Authorization": "Bearer " + TOKEN}

def result(url="https://cdn.example.com/photo.jpg"):
    return {"title": "A &amp; B", "url": "https://example.com/article",
            "thumbnail": {"src": "https://cdn.example.com/thumb.jpg"},
            "properties": {"url": url, "width": 2000, "height": 1500}}

def client_for(handler=None, **settings):
    handler = handler or (lambda request: httpx.Response(200, json={"results": [result()]}))
    return TestClient(create_app(Settings(brave_key="test-provider-secret", client_token=TOKEN, **settings),
                                 transport=httpx.MockTransport(handler)))

def test_requires_configuration():
    with TestClient(create_app(Settings())) as client:
        assert client.get("/health").json()["searchConfigured"] is False
        assert client.get("/v1/images/search?q=mountains").status_code == 503

def test_requires_authentication():
    with client_for() as client:
        assert client.get("/v1/images/search?q=mountains").status_code == 401
        assert client.get("/v1/images/search?q=mountains", headers={"Authorization": "Bearer wrong"}).status_code == 401

def test_normalizes_original_image_and_rights():
    with client_for() as client:
        response = client.get("/v1/images/search?q=mountains", headers=AUTH)
        assert response.status_code == 200
        data = response.json()
        photo = data["items"][0]
        assert photo["imageUrl"] == "https://cdn.example.com/photo.jpg"
        assert photo["sourcePageUrl"] == "https://example.com/article"
        assert photo["title"] == "A & B"
        assert photo["isOpenLicense"] is False
        assert data["hasMore"] is False
        assert response.headers["cache-control"] == "no-store"
        assert "test-provider-secret" not in response.text

def test_respects_real_provider_limits():
    def handler(request):
        assert request.url.params["count"] == "200"
        assert request.url.params["safesearch"] == "strict"
        assert "offset" not in request.url.params
        assert request.headers["X-Subscription-Token"] == "test-provider-secret"
        return httpx.Response(200, json={"results": [], "query": {"altered": "mountain"}})
    with client_for(handler) as client:
        data = client.get("/v1/images/search?q=mountans", headers=AUTH).json()
        assert data["correctedQuery"] == "mountain"
        assert data["hasMore"] is False

@pytest.mark.parametrize("query", [" ", "word " * 51, "a" * 401])
def test_validates_queries(query):
    with client_for() as client:
        assert client.get("/v1/images/search", params={"q": query}, headers=AUTH).status_code == 422

def test_count_limit():
    with client_for() as client:
        assert client.get("/v1/images/search?q=forest&count=201", headers=AUTH).status_code == 422

def test_deduplication_and_unsafe_urls():
    data = normalize({"results": [result(), result(), result("http://cdn.example.com/a.jpg"),
                                  result("https://127.0.0.1/a.jpg"), result("file:///etc/passwd")]}, "forest", 200)
    assert len(data["items"]) == 1
    for url in ["https://user:secret@example.com/a.jpg", "https://localhost/a.jpg", "https://192.168.1.2/a.jpg"]:
        assert public_url(url, image=True) is None

def test_local_rate_limit():
    with client_for(requests_per_minute=1) as client:
        assert client.get("/v1/images/search?q=forest", headers=AUTH).status_code == 200
        response = client.get("/v1/images/search?q=forest", headers=AUTH)
        assert response.status_code == 429
        assert response.headers["retry-after"] == "60"

@pytest.mark.parametrize("upstream,expected", [(429, 429), (401, 502), (500, 502)])
def test_sanitizes_upstream_errors(upstream, expected):
    with client_for(lambda request: httpx.Response(upstream, text="secret-diagnostic")) as client:
        response = client.get("/v1/images/search?q=forest", headers=AUTH)
        assert response.status_code == expected
        assert "secret-diagnostic" not in response.text

def test_timeout():
    def handler(request):
        raise httpx.ReadTimeout("sensitive diagnostic", request=request)
    with client_for(handler) as client:
        response = client.get("/v1/images/search?q=forest", headers=AUTH)
        assert response.status_code == 504
        assert "sensitive diagnostic" not in response.text

def test_malformed_response_is_not_empty_success():
    with client_for(lambda request: httpx.Response(200, json={"unexpected": []})) as client:
        assert client.get("/v1/images/search?q=forest", headers=AUTH).status_code == 502
