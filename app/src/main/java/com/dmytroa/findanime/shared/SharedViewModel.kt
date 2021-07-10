package com.dmytroa.findanime.shared

import androidx.lifecycle.*
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult

/**
 * view model for sharing items between fragments and activity
 */
class SharedViewModel : ViewModel() {
    val selectedItemId: MutableLiveData<Long?> = MutableLiveData()
    var newSelectedResult: SearchResult? = null
    var makeReplacement = false

    var extraFabsIsExpanded = false

    val filterText: MutableLiveData<String> = MutableLiveData("")
    val filterBookmarks: MutableLiveData<Boolean> = MutableLiveData(false)
    var bookmarksIsChecked = false

    fun setFilterText(text: String){
        filterText.value = text
    }

    fun setFilterBookmarks(isActivated: Boolean){
        filterBookmarks.value = isActivated
    }

    companion object {
        const val TAG = "SharedViewModel"
    }
}