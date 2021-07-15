package com.legion_11.findanime.fragments.seeOtherOptions

import android.app.Application
import androidx.lifecycle.*
import com.legion_11.findanime.FindAnimeApplication
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchResult
import com.legion_11.findanime.repositories.LocalFilesRepository
import kotlinx.coroutines.launch

class SeeOtherOptionsViewModel(application: Application, id: Long) : AndroidViewModel(application) {
    private val repository: LocalFilesRepository = (application as FindAnimeApplication).repository
    private val _allResults = MutableLiveData<Array<SearchResult>>()
    val allResults: LiveData<Array<SearchResult>> = _allResults

    init {
        viewModelScope.launch {
            _allResults.value = repository.getAllResultsByItemId(id)
        }
    }

    suspend fun get(id: Long): SearchItemWithSelectedResult? {
        return repository.getSearchItemWithSelectedResult(id)
    }

    class SeeOtherOptionsViewModelFactory(private val application: Application, private val id: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SeeOtherOptionsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SeeOtherOptionsViewModel(application, id) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}