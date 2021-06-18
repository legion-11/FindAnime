package com.dmytroa.findanime

import android.app.Application
import com.dmytroa.findanime.roomDB.AppDatabase
import com.dmytroa.findanime.repositories.LocalFilesRepository

class FindAnimeApplication: Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { LocalFilesRepository(database.searchDao()) }
}