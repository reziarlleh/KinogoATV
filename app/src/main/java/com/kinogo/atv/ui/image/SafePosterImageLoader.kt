package com.kinogo.atv.ui.image

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.kinogo.atv.player.PublicOnlyDns
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient

/** Application-wide poster loader with memory/disk caching and an SSRF-safe network stack. */
object SafePosterImageLoader {
    private val client: OkHttpClient by lazy {
        buildClient(PublicOnlyDns())
    }

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { client },
                    ),
                )
            }
            .build()
            .also { instance = it }
    }

    internal fun buildClient(dns: Dns): OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                throw IOException("Poster URL must use credential-free HTTPS")
            }
            chain.proceed(
                request.newBuilder()
                    .header("User-Agent", "KinogoATV/0.5 (Android TV; poster loader)")
                    .build(),
            )
        }
        .build()
}
