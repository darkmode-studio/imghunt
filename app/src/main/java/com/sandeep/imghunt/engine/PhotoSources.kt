package com.sandeep.imghunt.engine

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PhotoSources {
    const val APP_UA = "ImgHunt/1.1 (Android; darkmode-studio/imghunt)"
    fun createImageLoader(context: Context): ImageLoader {
        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).followRedirects(true)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", APP_UA).build()) }.build()
        return ImageLoader.Builder(context.applicationContext).components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }.build()
    }
    private fun enc(value: String) = URLEncoder.encode(value.trim(), "UTF-8")
    // Compatibility helper only. Real searches below always preserve the complete query.
    fun queryVariants(query: String): List<String> = buildList {
        add(query.trim())
        query.trim().split(Regex("\\s+")).takeIf { it.size > 1 }?.firstOrNull()?.let { add(it) }
    }.distinct()

    suspend fun fetchPage(provider: SourceProvider, query: String, page: Int = 1, apiKey: String? = null): CatalogPage {
        require(page >= 1)
        require(provider != SourceProvider.BRAVE) { "Web search requires the secure gateway" }
        require(!provider.requiresKey || !apiKey.isNullOrBlank()) { "API key required for ${provider.label}" }
        val q = enc(query)
        val headers = when (provider) {
            SourceProvider.UNSPLASH -> mapOf("Authorization" to "Client-ID ${apiKey.orEmpty()}")
            SourceProvider.PEXELS -> mapOf("Authorization" to apiKey.orEmpty())
            else -> emptyMap()
        }
        val url = when (provider) {
            SourceProvider.OPENVERSE -> "https://api.openverse.org/v1/images/?q=$q&page=$page&page_size=36"
            SourceProvider.WIKIMEDIA -> "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=36&gsroffset=${(page-1)*36}&gsrsearch=$q&prop=imageinfo&iiprop=url|size|mime|extmetadata&iiurlwidth=640"
            SourceProvider.ARCHIVE -> "https://archive.org/advancedsearch.php?q=${enc("mediatype:image AND ($query)")}&fl[]=identifier,title,creator&rows=36&page=$page&output=json"
            SourceProvider.NASA -> "https://images-api.nasa.gov/search?q=$q&media_type=image&page=$page&page_size=36"
            SourceProvider.MET -> "https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&q=$q"
            SourceProvider.UNSPLASH -> "https://api.unsplash.com/search/photos?query=$q&page=$page&per_page=30"
            SourceProvider.PEXELS -> "https://api.pexels.com/v1/search?query=$q&page=$page&per_page=30"
            SourceProvider.PIXABAY -> "https://pixabay.com/api/?key=${enc(apiKey.orEmpty())}&q=$q&image_type=photo&page=$page&per_page=30&safesearch=true"
            SourceProvider.BRAVE -> error("Use the gateway")
        }
        val root = JSONObject(SearchHttp.get(url, headers))
        if (root.has("error")) throw java.io.IOException("${provider.label} returned an API error")
        return when (provider) {
            SourceProvider.MET -> MetCatalog.parse(root, page)
            SourceProvider.UNSPLASH, SourceProvider.PEXELS, SourceProvider.PIXABAY -> StockCatalog.parse(provider, root, page)
            else -> OpenCatalog.parse(provider, root, page)
        }
    }
    suspend fun fetchProvider(provider: SourceProvider, query: String, page: Int = 1, apiKey: String? = null): List<PhotoItem> =
        fetchPage(provider, query, page, apiKey).items

    fun rankAndSort(items: List<PhotoItem>, sortBy: SortOrder): List<PhotoItem> = when (sortBy) {
        SortOrder.RELEVANCE -> items
        SortOrder.RESOLUTION -> items.sortedByDescending { it.width.toLong() * it.height }
        SortOrder.SOURCE -> items.sortedBy { it.sourceLabel }
    }
    suspend fun searchPage(query: String, page: Int, filters: SearchFilters,
        enabledProviders: Set<SourceProvider>, apiKeys: Map<SourceProvider, String>,
        allowFallback: Boolean = true): Pair<List<PhotoItem>, List<ProviderResult>> = coroutineScope {
        val providers = enabledProviders.filter { it != SourceProvider.BRAVE }.ifEmpty {
            listOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA)
        }
        val states = providers.map { provider -> async {
            try {
                val result = withTimeout(20000) { fetchPage(provider, query.trim(), page, apiKeys[provider]) }
                ProviderResult(provider, result.items, hasMore = result.hasMore)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) {
                ProviderResult(provider, emptyList(), error.message ?: "Source unavailable", hasMore = true)
            }
        } }.awaitAll()
        // Do not query disabled sources, silently alter names, or relax explicit filters.
        rankAndSort(states.flatMap { it.items }.filter(filters::matches).distinctBy { it.highResUrl }, filters.sort) to states
    }
}
