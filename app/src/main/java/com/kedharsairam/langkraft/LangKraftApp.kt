package com.kedharsairam.langkraft

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.kedharsairam.langkraft.data.ContentRepository

val Context.userDataStore by preferencesDataStore(name = "langkraft_user")

class LangKraftApp : Application() {
    fun userStore(): DataStore<Preferences> = applicationContext.userDataStore

    /** One cached content reader for the whole process. */
    lateinit var content: ContentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        content = ContentRepository(applicationContext)
    }
}
