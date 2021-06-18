package com.dmytroa.findanime.roomDB

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dmytroa.findanime.roomDB.dao.SearchDao
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem



@Database(
    entities = [SearchItem::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun searchDao(): SearchDao

    companion object {

        @Volatile private var INSTANCE: AppDatabase? = null
        /**
         * get singleton of db
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "com.cmedical.database"
                ).build()

                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}