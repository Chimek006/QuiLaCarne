package com.example.quilacarne.data.repository.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.local.entities.PendingRequestEntity
import com.example.quilacarne.data.remote.network.ApiLoggingInterceptor
import com.example.quilacarne.data.remote.network.AuthInterceptor
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.network.TokenAuthenticator
import com.example.quilacarne.data.repository.sync.PendingRequestRepository
import com.example.quilacarne.data.repository.sync.SyncRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.Locale

class PendingRequestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val pendingRequestDao by lazy { database.pendingRequestDao() }
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client by lazy {
        val tokenManager = TokenManager(applicationContext)
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(ApiLoggingInterceptor("PENDING_REQUEST_HTTP"))
            .authenticator(TokenAuthenticator(tokenManager))
            .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        drainQueue()
    }

    private suspend fun drainQueue(): Result {
        while (true) {
            val pending = pendingRequestDao.getNextPending() ?: return Result.success()
            pendingRequestDao.markAttempt(pending.id, System.currentTimeMillis())
            logSending(pending)

            try {
                sendPendingRequest(pending)
            } catch (e: IOException) {
                Log.w(
                    TAG,
                    "Network error for id=${pending.id} requestId=${pending.requestId}; keeping request pending",
                    e
                )
                return Result.retry()
            } catch (e: JsonParseException) {
                dropMalformedRequest(pending, e)
            } catch (e: IllegalArgumentException) {
                dropMalformedRequest(pending, e)
            } catch (e: IllegalStateException) {
                dropMalformedRequest(pending, e)
            }
        }
    }

    private fun logSending(pending: PendingRequestEntity) {
        Log.i(
            TAG,
            "Sending ${pending.method} ${pending.path} id=${pending.id} requestId=${pending.requestId}"
        )
    }

    private suspend fun sendPendingRequest(pending: PendingRequestEntity) {
        client.newCall(buildRequest(pending)).execute().use { response ->
            handleHttpResponse(
                pending = pending,
                code = response.code,
                isSuccessful = response.isSuccessful
            )
        }
    }

    private suspend fun handleHttpResponse(
        pending: PendingRequestEntity,
        code: Int,
        isSuccessful: Boolean
    ) {
        pendingRequestDao.deleteById(pending.id)

        if (isSuccessful) {
            Log.i(TAG, "Request succeeded $code id=${pending.id} requestId=${pending.requestId}")
            return
        }

        Log.w(TAG, "Request rejected HTTP $code id=${pending.id} requestId=${pending.requestId}")
        if (code in 400..599) {
            triggerOperationalSync("pending-request-http-$code")
        }
    }

    private suspend fun dropMalformedRequest(pending: PendingRequestEntity, error: RuntimeException) {
        Log.e(
            TAG,
            "Dropping malformed pending request id=${pending.id} requestId=${pending.requestId}",
            error
        )
        pendingRequestDao.deleteById(pending.id)
        triggerOperationalSync("pending-request-invalid")
    }

    private fun buildRequest(pending: PendingRequestEntity): Request {
        val url = buildUrl(pending)
        val body = pending.bodyJson?.toRequestBody(jsonMediaType)
        val emptyJsonBody = "{}".toRequestBody(jsonMediaType)

        val builder = Request.Builder()
            .url(url)
            .header("X-Request-ID", pending.requestId)

        return when (pending.method.uppercase(Locale.US)) {
            PendingRequestRepository.METHOD_POST -> builder
                .post(body ?: emptyJsonBody)
                .build()
            PendingRequestRepository.METHOD_PATCH -> builder
                .patch(body ?: emptyJsonBody)
                .build()
            PendingRequestRepository.METHOD_DELETE -> builder
                .delete(body)
                .build()
            else -> throw IllegalArgumentException("Unsupported method ${pending.method}")
        }
    }

    private fun buildUrl(pending: PendingRequestEntity): HttpUrl {
        val builder = BuildConfig.BASE_URL
            .toHttpUrl()
            .newBuilder()
            .addPathSegments(pending.path.trimStart('/'))

        addQueryParams(builder, pending.queryJson)
        return builder.build()
    }

    private fun addQueryParams(builder: HttpUrl.Builder, queryJson: String?) {
        if (queryJson.isNullOrBlank()) return

        val queryElement = JsonParser.parseString(queryJson)
        if (!queryElement.isJsonObject) return

        queryElement.asJsonObject.entrySet().forEach { (name, value) ->
            addQueryParamValue(builder, name, value)
        }
    }

    private fun addQueryParamValue(builder: HttpUrl.Builder, name: String, value: JsonElement) {
        when {
            value.isJsonNull -> Unit
            value.isJsonArray -> value.asJsonArray.forEach { element ->
                addQueryParamValue(builder, name, element)
            }
            value.isJsonPrimitive -> builder.addQueryParameter(name, value.asJsonPrimitive.asString)
            else -> builder.addQueryParameter(name, gson.toJson(value))
        }
    }

    private suspend fun triggerOperationalSync(reason: String) {
        runCatching {
            RetrofitClient.init(applicationContext)
            SyncRepository(database, applicationContext)
                .syncOperationalData(reason)
                .getOrThrow()
        }.onFailure { error ->
            Log.w(TAG, "Operational sync after pending request failure did not complete: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "PENDING_REQUESTS"
    }
}
