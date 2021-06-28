package com.dmytroa.findanime.fragments.search

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
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

class SearchFragmentViewModel(application: Application) : AndroidViewModel(application) {

    private val searchService = RetrofitInstance.getInstance().create(SearchService::class.java)
    private val repository: LocalFilesRepository = (application as FindAnimeApplication).repository

    val items: LiveData<Array<SearchItemWithSelectedResult>> = repository.getAll().asLiveData()

    //not allowing enqueue more than 1 search call
    private val semaphore = Semaphore(1,0)
    private val calls: MutableList<Pair<Call<*>, Long>> = mutableListOf()

    suspend fun insert(searchItem: SearchItem): Long = repository.insert(searchItem)

    fun delete(searchItem: SearchItem) {
        Log.i(TAG, "delete: $searchItem")
        cancelCall(searchItem.id)
        repository.delete(searchItem, getApplication())
    }

    fun setIsBookmarked(b: Boolean, item: SearchItemWithSelectedResult) {
        repository.setIsBookmarked(item.searchItem, b)
    }

    fun createNewAnimeSearchRequest(imageUri: Uri) {
        viewModelScope.launch {
            Log.i(TAG, "createNewAnimeSearchRequest: ${calls.size}")
            val imageCopyName = LocalFilesRepository.copyImageToInternalStorage(imageUri, getApplication())
            if (imageCopyName != null) {
                val newItem = SearchItem(imageCopyName)
                val newId = insert(newItem)
                newItem.id = newId
                searchByImage(imageCopyName, newItem)
            } else {
                //https://stackoverflow.com/questions/53484781/android-mvvm-is-it-possible-to-display-a-message-toast-snackbar-etc-from-the
                //show one time error message
            }
        }
    }

    fun repeatAnimeSearchRequest(item: SearchItemWithSelectedResult) {
        viewModelScope.launch {
            if (calls.map { it.second }.contains(item.searchItem.id)) return@launch
            item.searchResult?.videoURL?.let {
                getVideoPreview(it, item.searchItem)
            } ?: run {
                item.searchItem.imageFileName?.let {
                    searchByImage(it, item.searchItem)
                } ?: run {
                    delete(item.searchItem)
                }
            }
        }
    }

    private suspend fun searchByImage(copyOfImageUri: String,
                                      searchItemToUpdate: SearchItem) {
        val fullImageUri = getFullImageURI(copyOfImageUri)
        val body = prepareMultipart(fullImageUri)
        val call = searchService.searchByImage(body)
        calls.add(Pair(call, searchItemToUpdate.id))

        semaphore.acquire()

        call.enqueue(object : Callback<SearchByImageRequestResult> {
            override fun onResponse(
                call: Call<SearchByImageRequestResult>,
                response: Response<SearchByImageRequestResult>
            ) {
                semaphore.release()
                calls.remove(Pair(call, searchItemToUpdate.id))
                val responseBody = response.body()
                if (responseBody != null) {
                    updateSearchItemWithNewData(responseBody, searchItemToUpdate)
                } else {
                    Log.i(TAG, "searchByImage.onResponse: response.isUnsuccessful")
                }
            }

            override fun onFailure(call: Call<SearchByImageRequestResult>, t: Throwable) {
                semaphore.release()
                calls.remove(Pair(call, searchItemToUpdate.id))
                Log.i(TAG, "${t.message}")
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

    private fun updateSearchItemWithNewData(searchByImageRequestResult: SearchByImageRequestResult,
                                            searchItemToUpdate: SearchItem) {
        viewModelScope.launch {
            if (searchByImageRequestResult.error != "" || searchByImageRequestResult.result.isEmpty() )
                repository.delete(searchItemToUpdate, getApplication())

            val listToInsert = searchByImageRequestResult.result.map {
                SearchResult(
                    id = 0,
                    parentId = searchItemToUpdate.id,
                    fileName = it.filename,
                    english = it.anilist?.title?.english,
                    nativeTitle = it.anilist?.title?.native,
                    romaji = it.anilist?.title?.romaji,
                    idMal = it.anilist?.idMal,
                    isAdult = it.anilist?.isAdult,
                    episode = it.episode,
                    from = it.from,
                    similarity = it.similarity,
                    videoURL = it.video,
                    imageURL = it.image
                )
            }


            searchItemToUpdate.apply {
                selectedResultId = repository.insertAllAndReturnIdOfFirst(listToInsert)
            }
            repository.update(searchItemToUpdate)

            CoroutineScope(Dispatchers.IO).launch {
                repository.deleteImage(searchItemToUpdate.imageFileName, getApplication())
            }
            getVideoPreview(listToInsert[0].videoURL, searchItemToUpdate)
        }
    }

    fun replaceWithNewVideo(searchItemToUpdate: SearchItem, newSearchResult: SearchResult){
        viewModelScope.launch {
            searchItemToUpdate.videoFileName?.let {
                repository.deleteVideo(it, getApplication())
            }
            searchItemToUpdate.selectedResultId = newSearchResult.id
            searchItemToUpdate.videoFileName = null
            repository.update(searchItemToUpdate)
            getVideoPreview(newSearchResult.videoURL, searchItemToUpdate)
        }
    }

    private fun getVideoPreview(url: String, searchItemToUpdate: SearchItem){

        val call = searchService.getVideoPreview(url)
        val callWithId = Pair(call, searchItemToUpdate.id)
        calls.add(callWithId)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                calls.remove(callWithId)
                val responseBody = response.body()
                if (responseBody != null) {
                    val fileUri = LocalFilesRepository.saveVideo(responseBody, getApplication())
                    if (fileUri != null) {

                        updateSearchItemWithVideo(searchItemToUpdate, fileUri)
                    } else {
                        Log.i(TAG, "getVideoPreview: cannot save video")
                    }
                } else {
                    Log.i(TAG, "getVideoPreview: response.isUnsuccessful")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                calls.remove(callWithId)
                if (call.isCanceled){
                    Log.i(TAG, "getVideoPreview: response.canceled")
                } else {
                    Log.i(TAG, "getVideoPreview: response.isUnsuccessful")
                }
            }
        })
    }

    private fun updateSearchItemWithVideo(searchItemToUpdate: SearchItem, fileUri: String) {
        viewModelScope.launch {
            searchItemToUpdate.apply {
                videoFileName = fileUri
            }
            repository.update(searchItemToUpdate)
        }
    }

    private fun cancelCall(id: Long) {
        for (pair in calls){
            if (pair.second == id) {
                pair.first.cancel()
                Log.i(TAG, "cancelCall: call ${pair.second} canceled")
                calls.remove(pair)
                break
            }
        }
    }

    fun getFullVideoURI(fileName: String) =
        getApplication<FindAnimeApplication>().applicationContext
            .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            .toString() + File.separatorChar + fileName

    private fun getFullImageURI(fileName: String) =
        getApplication<FindAnimeApplication>().applicationContext
            .filesDir.toString() + File.separatorChar + fileName

    class SearchFragmentViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchFragmentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchFragmentViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        const val TAG = "SearchFragmentViewModel"
    }
}