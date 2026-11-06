package com.kedharsairam.langkraft

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File

val Context.userDataStore by preferencesDataStore(name = "langkraft_user")

class LangKraftApp : Application() {
    fun userStore(): DataStore<Preferences> = applicationContext.userDataStore
}
