package com.dmytroa.findanime.shared

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.*
import com.dmytroa.findanime.FindAnimeApplication
import com.dmytroa.findanime.dataClasses.retrofit.SearchByImageRequestResult
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import com.dmytroa.findanime.repositories.LocalFilesRepository
import com.dmytroa.findanime.retrofit.RetrofitInstance
import com.dmytroa.findanime.retrofit.SearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class SharedViewModel : ViewModel() {
    var selectedItem: SearchItemWithSelectedResult? = null
    lateinit var newSelectedResult: SearchResult
    val filterText: MutableLiveData<String> = MutableLiveData("")
    val filterBookmarks: MutableLiveData<Boolean> = MutableLiveData(false)
    
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