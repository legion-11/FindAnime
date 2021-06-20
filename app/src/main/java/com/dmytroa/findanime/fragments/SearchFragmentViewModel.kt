package com.dmytroa.findanime.fragments

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.dmytroa.findanime.dataClasses.retrofit.SearchByImageResult
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.repositories.LocalFilesRepository
import com.dmytroa.findanime.retrofit.RetrofitInstance
import com.dmytroa.findanime.retrofit.SearchService
import kotlinx.coroutines.*
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class SearchFragmentViewModel(private val repository: LocalFilesRepository) : ViewModel() {
    private val searchService = RetrofitInstance.getInstance().create(SearchService::class.java)
    val items: LiveData<Array<SearchItem>> = repository.getAll().asLiveData()

    private val calls: MutableList<Pair<Call<*>, Long>> = mutableListOf()

    suspend fun insert(searchItem: SearchItem): Long = repository.insert(searchItem)

    fun createNewAnimeSearchRequest(imageUri: Uri, context: Context) {
        viewModelScope.launch {
            Log.i(TAG, "createNewAnimeSearchRequest: ${calls.size}")
            val copyOfImageUri = LocalFilesRepository.copyImageToInternalStorage(imageUri, context)
            if (copyOfImageUri != null) {
                val newItem = SearchItem(copyOfImageUri)
                val newId = insert(newItem)
                searchByImage(copyOfImageUri, newId, context)
            } else {
                //https://stackoverflow.com/questions/53484781/android-mvvm-is-it-possible-to-display-a-message-toast-snackbar-etc-from-the
                //show one time error message
            }
        }
    }

    fun repeatAnimeSearchRequest(searchItem: SearchItem, context: Context) {
        viewModelScope.launch {
            Log.i(TAG, "repeatAnimeSearchRequest: ${calls.size}")
            if (calls.map { it.second }.contains(searchItem.id)) return@launch
            searchByImage(searchItem.imageURI, searchItem.id, context)
        }
    }

    private suspend fun searchByImage(copyOfImageUri: String, id: Long, context: Context) {
        val body = prepareMultipart(copyOfImageUri)
        while (calls.size != 0) { delay(1000) }
        val call = searchService.searchByImage(body)
        calls.add(Pair(call, id))

        call.enqueue(object : Callback<SearchByImageResult> {
            override fun onResponse(
                call: Call<SearchByImageResult>,
                response: Response<SearchByImageResult>
            ) {
                Log.i(TAG, "searchByImage.onResponse: ${response.body()?.result} ")
                calls.remove(Pair(call, id))
                Log.i(TAG, "updateSearchItemWithNewData: ${1} ")
                val responseBody = response.body()
                Log.i(TAG, "updateSearchItemWithNewData: ${2} ")
                if (responseBody != null) {
                    Log.i(TAG, "updateSearchItemWithNewData: ${3} ")
                    updateSearchItemWithNewData(responseBody, id, context)
                } else {
                    Log.i(TAG, "searchByImage.onResponse: response.isUnsuccessful")
                }
            }

            override fun onFailure(call: Call<SearchByImageResult>, t: Throwable) {
                calls.remove(Pair(call, id))
                if (call.isCanceled){
                    Log.i(TAG, "searchByImage.onFailure: response.canceled")
                } else {
                    Log.i(TAG, "searchByImage.onFailure: response.isUnsuccessful")
                }
            }
        })
    }

    private fun prepareMultipart(uri: String):  MultipartBody.Part {
        val file = File(uri)
        val requestFile = file.asRequestBody()
        return MultipartBody.Part.createFormData("image", file.name, requestFile)
    }

    private fun updateSearchItemWithNewData(searchItemResult: SearchByImageResult,
                                            id: Long,
                                            context: Context) {
        viewModelScope.launch {
            val mostSimilar = searchItemResult.result.firstOrNull()
            Log.i(TAG, "updateSearchItemWithNewData: ${mostSimilar} ")
            mostSimilar?.let {
                val newItem = repository.get(id).apply {
                    fileName = mostSimilar.filename
                    from = mostSimilar.from
                    episode = mostSimilar.episode?.toString()
                    similarity = mostSimilar.similarity
                }

                Log.i(TAG, "updateSearchItemWithNewData: ${newItem} ")
                repository.update(newItem)

                getVideoPreview(mostSimilar.video, id, context)
            }
        }
    }

    private fun getVideoPreview(url: String, id: Long, context: Context){

        val call = searchService.getVideoPreview(url)
        calls.add(Pair(call, id))

        call.enqueue(object : Callback<ResponseBody> {
            override  fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                calls.remove(Pair(call, id))
                val responseBody = response.body()
                if (responseBody != null) {
                    val fileUri = LocalFilesRepository.saveVideo(responseBody, context)
                    if (fileUri != null) {
                        updateSearchItemWithVideo(fileUri, id)
                    }
                } else {
                    Log.i(TAG, "getVideoPreview: response.isUnsuccessful")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                calls.remove(Pair(call, id))
                if (call.isCanceled){
                    Log.i(TAG, "getVideoPreview: response.canceled")
                } else {
                    Log.i(TAG, "getVideoPreview: response.isUnsuccessful")
                }
            }
        })
    }

    private fun updateSearchItemWithVideo(fileUri: String, id: Long) {
        viewModelScope.launch {
            val newItem = repository.get(id).apply {
                video = fileUri
                finished = true
            }
            repository.update(newItem)
        }
    }

    private fun cancelCall(id: Long) {
        for (pair in calls){
            if (pair.second == id) {
                pair.first.cancel()
                calls.remove(pair)
                break
            }
        }
    }

    class SearchFragmentViewModelFactory(private val repository: LocalFilesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchFragmentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchFragmentViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        const val TAG = "SearchFragmentViewModel"
    }
}