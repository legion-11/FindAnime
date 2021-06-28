package com.dmytroa.findanime.fragments.seeOtherOptions

import android.app.Application
import androidx.lifecycle.*
import com.dmytroa.findanime.FindAnimeApplication
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import com.dmytroa.findanime.repositories.LocalFilesRepository
import com.dmytroa.findanime.shared.SharedViewModel
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