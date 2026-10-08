package com.preform.reader.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.preform.reader.container
import java.util.concurrent.TimeUnit

/**
 * The libraries' background sync, for when the app isn't open: send moves made here,
 * bring each catalog over, send the phone's own books, and (on Wi-Fi) download every
 * book of each library kept in full.
 */
class LibraryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = applicationContext.container.sync
        if (sync.settings.connectionsNow().isEmpty()) return Result.success()
        if (sync.sync().isFailure) return Result.retry()
        sync.hashLocalBooks()
        runCatching { sync.uploadLocalBooks() }
        // Whole libraries only over Wi-Fi or another unmetered network.
        if (unmetered()) sync.downloadAll()
        return Result.success()
    }

    private fun unmetered(): Boolean {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val PERIODIC = "library-sync"
        private const val NOW = "library-sync-now"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every hour or so while online; set up once at launch. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibraryWorker>(1, TimeUnit.HOURS).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** A pass soon, after a setting changed; runs even if the app closes. */
        fun runSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<LibraryWorker>().setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
