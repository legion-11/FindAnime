package com.dmytroa.findanime.fragments.search

import android.app.Application
import android.net.Uri
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
    private val semaphoreConcurrencyLimit = Semaphore(1,0)
    private val semaphoreForCheckingCalls = Semaphore(1,0)
    private val semaphoreVideoCall = Semaphore(1,0)

    private val calls: MutableList<Pair<Call<*>, Long>> = mutableListOf()
    private val finishedSearchCalls = mutableListOf<Long>()

    suspend fun insert(searchItem: SearchItem): Long = repository.insert(searchItem)

    suspend fun getSearchItemById(id: Long) = repository.getSearchItemById(id)

    fun delete(searchItem: SearchItem) {
        Log.i(TAG, "delete: $searchItem")
        cancelCall(searchItem.id)
        repository.delete(searchItem, getApplication())
    }

    fun setIsBookmarked(b: Boolean, item: SearchItem) {
        repository.setIsBookmarked(item, b)
    }

    fun createNewAnimeSearchRequest(url: String) {
        viewModelScope.launch {
                val newItem = SearchItem(url, null)
                val newId = insert(newItem)
                newItem.id = newId
                searchByUrl(url, newItem)
        }
    }
    private suspend fun searchByUrl(url: String, searchItemToUpdate: SearchItem) {
        semaphoreForCheckingCalls.acquire()
        if (calls.map { it.second }.contains(searchItemToUpdate.id)
            || finishedSearchCalls.contains(searchItemToUpdate.id)) {
            semaphoreForCheckingCalls.release()
            return
        }

        val call = searchService.searchByUrl(url)
        calls.add(Pair(call, searchItemToUpdate.id))
        semaphoreConcurrencyLimit.acquire()
        call.enqueue(object : Callback<SearchByImageRequestResult> {
            override fun onResponse(
                call: Call<SearchByImageRequestResult>,
                response: Response<SearchByImageRequestResult>
            ) {
                val responseBody = response.body()
                if (responseBody != null) {
                    updateSearchItemWithNewData(responseBody, searchItemToUpdate)
                } else {
                    delete(searchItemToUpdate)
                    Log.i(TAG, "searchByImage.onResponse: response. unsuccessful")
                }
                finishedSearchCalls.add(searchItemToUpdate.id)
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }

            override fun onFailure(call: Call<SearchByImageRequestResult>, t: Throwable) {
                Log.i(TAG, "${t.message}")
                if (call.isCanceled){
                    delete(searchItemToUpdate)
                } else {
                    Log.i(TAG, "searchByImage.onFailure: response.isUnsuccessful")
                }
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }
        })
        semaphoreForCheckingCalls.release()
    }

    fun createNewAnimeSearchRequest(imageUri: Uri) {
        viewModelScope.launch {
            Log.i(TAG, "createNewAnimeSearchRequest: calls size = ${calls.size}")
            val imageCopyName = LocalFilesRepository.copyImageToCacheDir(imageUri, getApplication())
            if (imageCopyName != null) {
                val newItem = SearchItem(null, imageCopyName)
                val newId = insert(newItem)
                newItem.id = newId
                //<--- stop 1
                searchByImage(imageCopyName, newItem)
            } else {
                //https://stackoverflow.com/questions/53484781/android-mvvm-is-it-possible-to-display-a-message-toast-snackbar-etc-from-the
                //show one time error message
            }
        }
    }

    private suspend fun searchByImage(copyOfImageUri: String,
                                      searchItemToUpdate: SearchItem) {
        semaphoreForCheckingCalls.acquire()
        if (calls.map { it.second }.contains(searchItemToUpdate.id)
            || finishedSearchCalls.contains(searchItemToUpdate.id)) {
            semaphoreForCheckingCalls.release()
            return
        }

        val fullImageUri = LocalFilesRepository.getFullImagePath(copyOfImageUri, getApplication())

        if (!File(fullImageUri).exists()) {
            delete(searchItemToUpdate)
            semaphoreForCheckingCalls.release()
            return
        }

        val body = prepareMultipart(fullImageUri)
        val call = searchService.searchByImage(body)
        calls.add(Pair(call, searchItemToUpdate.id))

        semaphoreConcurrencyLimit.acquire()
        call.enqueue(object : Callback<SearchByImageRequestResult> {
            override fun onResponse(
                call: Call<SearchByImageRequestResult>,
                response: Response<SearchByImageRequestResult>
            ) {
                val responseBody = response.body()
                if (responseBody != null) {
                    updateSearchItemWithNewData(responseBody, searchItemToUpdate)
                    Log.i(TAG, "searchByImage.onResponse: response. successful")
                } else {
                    Log.i(TAG, "searchByImage.onResponse: response.isUnsuccessful")
                }
                finishedSearchCalls.add(searchItemToUpdate.id)
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }

            override fun onFailure(call: Call<SearchByImageRequestResult>, t: Throwable) {
                Log.i(TAG, "${t.message}")
                if (call.isCanceled){
                    Log.i(TAG, "searchByImage.onFailure: response.canceled")
                } else {
                    Log.i(TAG, "searchByImage.onFailure: response.isUnsuccessful")
                }
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }
        })
        semaphoreForCheckingCalls.release()
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

            Log.i(TAG, "updateSearchItemWithNewData: deleteImage ${searchItemToUpdate.imageFileName} ")
            LocalFilesRepository.deleteImage(searchItemToUpdate.imageFileName, getApplication())
            semaphoreVideoCall.acquire()
            val newInstance = searchItemToUpdate.apply {
                selectedResultId = repository.insertAllAndReturnIdOfFirst(listToInsert)
                imageFileName = null
                url = null
            }

            repository.update(newInstance)
            getVideoPreview(listToInsert[0].videoURL, newInstance)
        }
    }

    fun repeatAnimeSearchRequest(item: SearchItemWithSelectedResult) {
        viewModelScope.launch {
            item.searchResult?.videoURL?.let {
                semaphoreVideoCall.acquire()
                if (calls.map {p-> p.second }.contains(item.searchItem.id)) {
                    semaphoreVideoCall.release()
                    return@launch
                }
                val searchItemToUpdate = repository.getSearchItemById(item.searchItem.id)
                searchItemToUpdate.videoFileName?.let {
                    semaphoreVideoCall.release()
                    return@launch
                }
                getVideoPreview(it, item.searchItem.copy())
            } ?: run {
                item.searchItem.imageFileName?.let {
                    searchByImage(it, item.searchItem.copy())
                } ?: run {
                    item.searchItem.url?.let {
                        searchByUrl(it, item.searchItem.copy())
                    } ?: run {
                        delete(item.searchItem)
                    }
                }
            }
        }
    }

    fun replaceWithNewVideo(searchItemId: Long, newSearchResult: SearchResult){
        viewModelScope.launch {
            semaphoreVideoCall.acquire()
            cancelCall(searchItemId)
            val searchItemToUpdate = repository.getSearchItemById(searchItemId)
            Log.i(TAG, "replaceWithNewVideo: ${searchItemToUpdate.videoFileName} " +
                    "${calls.map { it.second }} ${searchItemToUpdate.id}")

            searchItemToUpdate.videoFileName?.let { LocalFilesRepository.deleteVideo(it, getApplication()) }
            searchItemToUpdate.selectedResultId = newSearchResult.id
            searchItemToUpdate.videoFileName = null
            repository.update(searchItemToUpdate)
            getVideoPreview(newSearchResult.videoURL, searchItemToUpdate)
        }
    }

    private suspend fun getVideoPreview(url: String, searchItemToUpdate: SearchItem){
        val fileName = "${System.currentTimeMillis()}.mp4"
        Log.i(TAG, "getVideoPreview: $fileName, ${calls.map { it.second }}")

        val call = searchService.getVideoPreview(url)
        val callWithId = Pair(call, searchItemToUpdate.id)
        calls.add(callWithId)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "getVideoPreview: onResponse $fileName")
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val savedfileName = LocalFilesRepository
                            .saveVideoToExternalStorage(responseBody, fileName, getApplication())

                        if (savedfileName != null) {
                            updateSearchItemWithVideo(searchItemToUpdate, savedfileName)
                        } else {
                            Log.i(TAG, "getVideoPreview: canceled during saving video $fileName")
                            LocalFilesRepository.deleteVideo(fileName, getApplication())
                        }

                    } else {
                        Log.i(TAG, "getVideoPreview: response.isUnsuccessful $fileName")
                    }

                    calls.remove(callWithId)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                LocalFilesRepository.deleteVideo(fileName, getApplication())
                if (call.isCanceled){
                    Log.i(TAG, "getVideoPreview: response.canceled $fileName")
                } else {
                    Log.i(TAG, "getVideoPreview: response.isUnsuccessful $fileName")
                }
                calls.remove(callWithId)
            }
        })
        semaphoreVideoCall.release()
    }

    private suspend fun updateSearchItemWithVideo(searchItemToUpdate: SearchItem, fileUri: String) {
        searchItemToUpdate.apply {
            videoFileName = fileUri
        }
        repository.update(searchItemToUpdate)
        Log.i(TAG, "updateSearchItemWithVideo: $fileUri")

    }

    private fun cancelCall(id: Long): Boolean {
        for (pair in calls){
            if (pair.second == id) {
                pair.first.cancel()
                calls.remove(pair)
                Log.i(TAG, "cancelCall: call ${pair.second} canceled ${calls.map {it.second}}")
                return true
            }
        }
        return false
    }

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