package com.legion_11.findanime

import android.app.Application
import com.legion_11.findanime.roomDB.AppDatabase
import com.legion_11.findanime.repositories.LocalFilesRepository

class FindAnimeApplication: Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { LocalFilesRepository(database.searchDao()) }
}