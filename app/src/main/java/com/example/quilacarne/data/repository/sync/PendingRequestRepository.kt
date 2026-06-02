package com.example.quilacarne.data.repository.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.quilacarne.data.local.dao.PendingRequestDao
import com.example.quilacarne.data.local.entities.PendingRequestEntity
import com.example.quilacarne.data.repository.sync.worker.PendingRequestWorker
import com.google.gson.Gson
import java.util.Locale
import java.util.UUID

class PendingRequestRepository(
    private val pendingRequestDao: PendingRequestDao,
    context: Context,
    private val gson: Gson = Gson()
) {
    private val appContext = context.applicationContext

    suspend fun enqueue(
        method: String,
        path: String,
        query: Map<String, Any?>? = null,
        body: Any? = null,
        entityType: String? = null,
        entityLocalId: String? = null,
        optimisticAction: String? = null
    ): PendingRequestEntity {
        val normalizedMethod = method.uppercase(Locale.US)
        require(normalizedMethod in SUPPORTED_METHODS) {
            "Unsupported queued request method: $method"
        }

        val request = PendingRequestEntity(
            id = UUID.randomUUID().toString(),
            requestId = UUID.randomUUID().toString(),
            method = normalizedMethod,
            path = path.trimStart('/'),
            queryJson = query?.takeIf { it.isNotEmpty() }?.let(gson::toJson),
            bodyJson = body?.let(gson::toJson),
            createdAt = System.currentTimeMillis(),
            entityType = entityType,
            entityLocalId = entityLocalId,
            optimisticAction = optimisticAction
        )

        pendingRequestDao.insert(request)
        Log.i(
            TAG,
            "Queued ${request.method} ${request.path} id=${request.id} requestId=${request.requestId}"
        )
        scheduleWorker()
        return request
    }

    private fun scheduleWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<PendingRequestWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
    }

    companion object {
        const val WORK_NAME = "pending-request-worker"
        const val METHOD_POST = "POST"
        const val METHOD_PATCH = "PATCH"
        const val METHOD_DELETE = "DELETE"
        const val ENTITY_TABLE = "TABLE"
        const val ACTION_MARK_AVAILABLE = "MARK_AVAILABLE"
        const val ACTION_MARK_CLEANING = "MARK_CLEANING"
        const val ACTION_MARK_OUT_OF_SERVICE = "MARK_OUT_OF_SERVICE"

        private const val TAG = "PENDING_REQUESTS"
        private val SUPPORTED_METHODS = setOf(METHOD_POST, METHOD_PATCH, METHOD_DELETE)
    }
}
