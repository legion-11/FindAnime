package com.dmytroa.findanime.roomDB.dao

import androidx.room.*
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(i: SearchItem): Long

    @Query("DELETE FROM searchItem WHERE id=:id")
    suspend fun delete(id: Long)

    @Delete
    suspend fun delete(i: SearchItem)

    @Update
    suspend fun update(i: SearchItem)

    @Query("SELECT * FROM searchItem WHERE id=:id")
    suspend fun get(id: Long): SearchItem

    @Query("SELECT * FROM searchItem ORDER BY id DESC")
    fun getAll(): Flow<Array<SearchItem>>

    @Query("UPDATE searchItem SET isBookmarked=:b WHERE id=:id")
    suspend fun setIsBookmarked(id: Long, b: Boolean)
}