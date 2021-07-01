package com.dmytroa.findanime.roomDB.dao

import androidx.room.*
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(i: SearchItem): Long

    @Insert
    suspend fun insert(i: SearchResult): Long

    @Insert
    suspend fun insertAll(list: List<SearchResult>)

    @Transaction
    suspend fun insertAllAndReturnIdOfFirst(list: List<SearchResult>): Long {
        val idOfFirstInsert = insert(list[0])
        CoroutineScope(Dispatchers.IO).launch {
            val subList = list.subList(1, list.lastIndex)
            insertAll(subList)
        }
        return idOfFirstInsert
    }

    @Delete
    suspend fun delete(i: SearchItem)

    @Update
    suspend fun update(i: SearchItem)

    @Query("SELECT * FROM searchItem WHERE id=:id")
    suspend fun getSearchItemWithSelectedResult(id: Long): SearchItemWithSelectedResult?

    @Query("SELECT * FROM searchItem WHERE id=:id")
    suspend fun getSearchItemById(id: Long): SearchItem

    @Transaction
    @Query("SELECT * FROM searchItem ORDER BY id DESC")
    fun getAllItemsWithSelectedResult(): Flow<Array<SearchItemWithSelectedResult>>

    @Query("SELECT * FROM searchResult WHERE parentId=:id")
    suspend fun getAllResultsByItemId(id: Long): Array<SearchResult>

    @Query("UPDATE searchItem SET isBookmarked=:b WHERE id=:id")
    suspend fun setIsBookmarked(id: Long, b: Boolean)

    @Query("UPDATE searchItem SET selectedResultId=:selectedResultId WHERE id=:itemId")
    suspend fun setNewSelectedId(itemId: Long, selectedResultId: Long)
}