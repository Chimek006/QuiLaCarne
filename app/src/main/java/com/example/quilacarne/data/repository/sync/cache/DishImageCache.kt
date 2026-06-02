package com.example.quilacarne.data.repository.sync.cache

import android.content.Context
import android.net.Uri
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.network.ApiLoggingInterceptor
import com.example.quilacarne.data.remote.network.AuthInterceptor
import com.example.quilacarne.data.remote.network.TokenAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.UUID

internal class DishImageCache(
    private val appContext: Context?
) {
    private val imageClient: OkHttpClient? by lazy {
        appContext?.let { context ->
            val tokenManager = TokenManager(context)
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenManager))
                .addInterceptor(ApiLoggingInterceptor("API_IMAGE_HTTP"))
                .authenticator(TokenAuthenticator(tokenManager))
                .build()
        }
    }

    suspend fun cacheDishImage(dishId: UUID, imageUrl: String?): String? {
        val remoteUrl = normalizeImageUrl(imageUrl)
        return if (remoteUrl == null) {
            null
        } else {
            cacheImageOrUseRemote(dishId, remoteUrl)
        }
    }

    private suspend fun cacheImageOrUseRemote(dishId: UUID, remoteUrl: String): String {
        val context = appContext
        val client = imageClient

        return if (context == null || client == null) {
            remoteUrl
        } else {
            withContext(Dispatchers.IO) {
                runCatching { cacheRemoteImage(context, client, dishId, remoteUrl) }
                    .getOrElse { remoteUrl }
            }
        }
    }

    private fun cacheRemoteImage(
        context: Context,
        client: OkHttpClient,
        dishId: UUID,
        remoteUrl: String
    ): String {
        val imageFile = localImageFile(context, dishId, remoteUrl)
        val existingImageUri = imageFile.cachedUri()

        return existingImageUri ?: downloadImage(client, imageFile, remoteUrl)
    }

    private fun downloadImage(client: OkHttpClient, imageFile: File, remoteUrl: String): String {
        val request = Request.Builder()
            .url(remoteUrl)
            .build()
        var resolvedUrl = remoteUrl
        client.newCall(request).execute().use { response ->
            val stream = response.body?.byteStream()
            if (response.isSuccessful && stream != null) {
                imageFile.outputStream().use { output ->
                    stream.use { input ->
                        input.copyTo(output)
                    }
                }
                resolvedUrl = imageFile.cachedUri() ?: remoteUrl
            }
        }

        return resolvedUrl
    }

    private fun localImageFile(context: Context, dishId: UUID, remoteUrl: String): File {
        val extension = getImageExtension(remoteUrl)
        val imageDir = File(context.filesDir, "dish_images")
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        return File(imageDir, "$dishId.$extension")
    }

    private fun File.cachedUri(): String? {
        return takeIf { exists() && length() > 0L }
            ?.let { Uri.fromFile(it).toString() }
    }

    private fun normalizeImageUrl(imageUrl: String?): String? {
        val trimmedUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null

        val resolvedUrl = if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            trimmedUrl
        } else {
            runCatching {
                URI(BuildConfig.BASE_URL).resolve(trimmedUrl.trimStart('/')).toString()
            }.getOrElse {
                trimmedUrl
            }
        }

        return resolvedUrl
            .replace("localhost", URI(BuildConfig.BASE_URL).host)
            .replace("127.0.0.1", URI(BuildConfig.BASE_URL).host)
    }

    private fun getImageExtension(imageUrl: String): String {
        val path = runCatching { URI(imageUrl).path }.getOrNull().orEmpty()
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp") }

        return extension ?: "jpg"
    }
}
