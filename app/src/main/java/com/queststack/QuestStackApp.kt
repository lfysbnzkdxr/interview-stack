package com.queststack

import android.app.Application
import com.queststack.data.DataContainer
import com.queststack.data.Seed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuestStackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DataContainer.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            Seed.seedCategories(DataContainer.database.categoryDao())
        }
    }
}
