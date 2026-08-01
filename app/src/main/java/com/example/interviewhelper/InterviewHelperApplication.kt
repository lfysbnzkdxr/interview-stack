package com.example.interviewhelper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.interviewhelper.data.local.SeedDataInitializer
import com.example.interviewhelper.worker.AutoBackupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InterviewHelperApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var seedDataInitializer: SeedDataInitializer

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        seedDataInitializer.initialize()
        // 退出 App 时触发自动备份
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }

    inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            WorkManager.getInstance(this@InterviewHelperApplication)
                .enqueueUniqueWork(
                    "auto_backup",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<AutoBackupWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                )
        }
    }
}
