package com.legion_11.findanime.fragments.search

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.lifecycle.*
import com.legion_11.findanime.FindAnimeApplication
import com.legion_11.findanime.R
import com.legion_11.findanime.dataClasses.retrofit.SearchByImageRequestResult
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItem
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchResult
import com.legion_11.findanime.repositories.LocalFilesRepository
import com.legion_11.findanime.retrofit.RetrofitInstance
import com.legion_11.findanime.retrofit.SearchService
import com.legion_11.findanime.shared.Event
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

/**
 * viewModel for [com.legion_11.findanime.fragments.search.SearchFragment]
 */
class SearchFragmentViewModel(application: Application) : AndroidViewModel(application) {

    //retrofit service for calling trace.moe API
    private val searchService = RetrofitInstance.getInstance().create(SearchService::class.java)
    private val repository: LocalFilesRepository = (application as FindAnimeApplication).repository

    val items: LiveData<Array<SearchItemWithSelectedResult>> = repository.getAll().asLiveData()

    // uri from MediaStore of temporary created file in public storage
    private val _uriToShare = MutableLiveData<Event<Uri?>>()
    val uriToShare: LiveData<Event<Uri?>> = _uriToShare

    // if app is reinstalled, app looses rights to modify previously created files in publick storage
    // so we have to save fileName of the new video while user gives permission
    private var pendingVideo: String? = null
    private val _permissionNeededForUpdate = MutableLiveData<IntentSender?>()
    val permissionNeededForUpdate: LiveData<IntentSender?> = _permissionNeededForUpdate

    //not allowing enqueue more than 1 search call
    // more at https://soruly.github.io/trace.moe-api/#/limits
    private val semaphoreConcurrencyLimit = Semaphore(1,0)

    // do not allow checking calls executions while call is prepared or executing
    private val semaphoreForCheckingCalls = Semaphore(1,0)
    private val semaphoreVideoCall = Semaphore(1,0)

    // list of all calls that are currently enqueued with corresponding to them SearchItem id
    // call can be canceled using this list by knowing SearchItem id
    private val calls: MutableList<Pair<Call<*>, Long>> = mutableListOf()
    private val finishedSearchCalls = mutableListOf<Long>()

    // live data for messages to be displayed via snack bar
    val errorMessages = MutableLiveData<String>()

    // error messages
    val unsuccessfulApi =
        getApplication<FindAnimeApplication>().resources.getString(R.string.error_response_body_empty)
    private val hornyError =
        getApplication<FindAnimeApplication>().resources.getString(R.string.error_legal_age)
    private val gallerySaveError =
        getApplication<FindAnimeApplication>().resources.getString(R.string.error_save_image)
    private val unsuccessfullResults =
        getApplication<FindAnimeApplication>().resources.getString(R.string.error_no_response_results)
    val videoSaveError =
        getApplication<FindAnimeApplication>().resources.getString(R.string.error_save_video)

    /**
     * insert [SearchItem] to database
     */
    suspend fun insert(searchItem: SearchItem): Long = repository.insert(searchItem)

    /**
     * get [SearchItem] by it's id
     */
    suspend fun getSearchItemById(id: Long) = repository.getSearchItemById(id)

    /**
     * remove [SearchItem] from database
     * (also stops corresponding enqueued call)
     */
    fun delete(searchItem: SearchItem) {
        cancelCall(searchItem.id)
        repository.delete(searchItem, getApplication())
    }

    fun setIsBookmarked(b: Boolean, item: SearchItem) {
        repository.setIsBookmarked(item, b)
    }

    /**
     * create new [SearchItem] and start new call to trace.moe API
     * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=search-by-image-url)
     *
     * @param url url to the image hosted somewhere in public domain
     * @param size for future use in [getVideoPreview]
     * @param mute for future use in [getVideoPreview]
     * @param cutBlackBorders for future use in [getVideoPreview]
     * @param showHContent for future use in [updateSearchItemWithNewData]
     */
    fun createNewAnimeSearchRequest(url: String, size: String, mute: Boolean,
                                    cutBlackBorders: Boolean, showHContent: Boolean) {
        viewModelScope.launch {
                val newItem = SearchItem(url, null, size, mute, cutBlackBorders, showHContent)
                val newId = insert(newItem)
                newItem.id = newId
                searchByUrl(url, newItem)
        }
    }
    /**
     * create new [SearchItem] and start new call to trace.moe API
     * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=search-by-image-upload)
     *
     * @param imageUri uri to the image in public storage
     * @param size for future use in [getVideoPreview]
     * @param mute for future use in [getVideoPreview]
     * @param cutBlackBorders for future use in [getVideoPreview]
     * @param showHContent for future use in [updateSearchItemWithNewData]
     */
    fun createNewAnimeSearchRequest(imageUri: Uri, size: String, mute: Boolean,
                                    cutBlackBorders: Boolean, showHContent: Boolean) {
        viewModelScope.launch {
            //temporarily save image to cache dir, in case we need to repeat search request
            val imageCopyName = LocalFilesRepository.copyImageToCacheDir(imageUri, getApplication())
            if (imageCopyName != null) {
                val newItem = SearchItem(null, imageCopyName, size, mute, cutBlackBorders, showHContent)
                val newId = insert(newItem)
                newItem.id = newId
                searchByImage(imageCopyName, newItem)
            } else {
                errorMessages.value = gallerySaveError
            }
        }
    }

    /**
     * enqueue new search call and update SearchItem with new data once it is finished
     * @param url url to the image hosted somewhere in public domain
     * @param searchItemToUpdate [SearchItem] that will be updated once call is successful
     */
    private suspend fun searchByUrl(url: String, searchItemToUpdate: SearchItem) {
        semaphoreForCheckingCalls.acquire()
        // call is currently enqueued, so it does not need to be repeated
        if (calls.map { it.second }.contains(searchItemToUpdate.id)
            || finishedSearchCalls.contains(searchItemToUpdate.id)) {
            semaphoreForCheckingCalls.release()
            return
        }

        val cutBorders = if (searchItemToUpdate.cutBlackBorders) "" else null
        val call = searchService.searchByUrl(url, cutBorders)
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
                    errorMessages.value = unsuccessfulApi
                }
                finishedSearchCalls.add(searchItemToUpdate.id)
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }

            override fun onFailure(call: Call<SearchByImageRequestResult>, t: Throwable) {
                if (call.isCanceled){
                    delete(searchItemToUpdate)
                } else {
                    errorMessages.value = t.localizedMessage
                }
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }
        })
        semaphoreForCheckingCalls.release()
    }

    private suspend fun searchByImage(copyOfImageUri: String, searchItemToUpdate: SearchItem) {
        semaphoreForCheckingCalls.acquire()
        // call is currently enqueued, so it does not need to be repeated
        if (calls.map { it.second }.contains(searchItemToUpdate.id)
            || finishedSearchCalls.contains(searchItemToUpdate.id)) {
            semaphoreForCheckingCalls.release()
            return
        }

        val fullImageUri = LocalFilesRepository.getFullImagePath(copyOfImageUri, getApplication())

        // image was deleted from cache dir
        if (!File(fullImageUri).exists()) {
            delete(searchItemToUpdate)
            semaphoreForCheckingCalls.release()
            return
        }

        // prepare file to be send
        val body = prepareMultipart(fullImageUri)

        val cutBorders = if (searchItemToUpdate.cutBlackBorders) "" else null
        val call = searchService.searchByImage(body, cutBorders)
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
                    errorMessages.value = unsuccessfulApi
                }
                finishedSearchCalls.add(searchItemToUpdate.id)
                calls.remove(Pair(call, searchItemToUpdate.id))
                semaphoreConcurrencyLimit.release()
            }

            override fun onFailure(call: Call<SearchByImageRequestResult>, t: Throwable) {
                if (!call.isCanceled){
                    errorMessages.value = t.localizedMessage
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
            if (searchByImageRequestResult.error != "" || searchByImageRequestResult.result.isEmpty() ) {
                repository.delete(searchItemToUpdate, getApplication())
                errorMessages.value = searchByImageRequestResult.error ?: unsuccessfullResults
                return@launch
            }

            val fullListToInsert = searchByImageRequestResult.result.map {
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
            // filter list if showHContent preference was not ticked in root_preferences
            val filteredList = if (!searchItemToUpdate.showHContent) {
                fullListToInsert.filter { it.isAdult == false }
            } else fullListToInsert

            if (filteredList.isEmpty()) {
                repository.delete(searchItemToUpdate, getApplication())
                errorMessages.value = hornyError
            }

            LocalFilesRepository.deleteImage(searchItemToUpdate.imageFileName, getApplication())
            semaphoreVideoCall.acquire()
            val newInstance = searchItemToUpdate.apply {
                selectedResultId = repository.insertAllAndReturnIdOfFirst(filteredList)
                imageFileName = null
                url = null
            }

            repository.update(newInstance)
            getVideoPreview(filteredList[0].videoURL, newInstance)
        }
    }

    /**
     * repeat search request if it was unsuccessful (no videoFileName in [SearchItem])
     */
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

    /**
     * update [SearchItem] with new video
     */
    fun replaceWithNewVideo(searchItemId: Long, newSearchResult: SearchResult){
        viewModelScope.launch {
            semaphoreVideoCall.acquire()
            cancelCall(searchItemId)
            val searchItemToUpdate = repository.getSearchItemById(searchItemId)
            if (newSearchResult.id == searchItemToUpdate.selectedResultId) {
                semaphoreVideoCall.release()
                return@launch
            }
            searchItemToUpdate.videoFileName?.let { LocalFilesRepository.deleteVideo(it, getApplication()) }
            searchItemToUpdate.selectedResultId = newSearchResult.id
            searchItemToUpdate.videoFileName = null
            repository.update(searchItemToUpdate)
            getVideoPreview(newSearchResult.videoURL, searchItemToUpdate)
        }
    }

    /**
     * download video preview from server and save it to external dir
     * [See Api](https://soruly.github.io/trace.moe-api/#/docs?id=media-preview)
     */
    private suspend fun getVideoPreview(url: String, searchItemToUpdate: SearchItem){
        val fileName = "${System.currentTimeMillis()}.mp4"

        val mute = if (searchItemToUpdate.mute) "" else null
        val call = searchService.getVideoPreview(url, searchItemToUpdate.size, mute)
        val callWithId = Pair(call, searchItemToUpdate.id)
        calls.add(callWithId)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                CoroutineScope(Dispatchers.IO).launch {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val savedfileName = LocalFilesRepository
                            .saveVideoToExternalStorage(responseBody, fileName, getApplication())
                        if (savedfileName != null) {
                            updateSearchItemWithVideo(searchItemToUpdate, savedfileName)
                        } else {
                            LocalFilesRepository.deleteVideo(fileName, getApplication())
                            errorMessages.value = videoSaveError
                        }

                    } else {
                        errorMessages.value = unsuccessfulApi
                    }

                    calls.remove(callWithId)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                LocalFilesRepository.deleteVideo(fileName, getApplication())
                if (!call.isCanceled){
                    errorMessages.value = t.localizedMessage
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

    }

    /**
     * cancel call by [SearchItem] id
     */
    private fun cancelCall(id: Long): Boolean {
        for (pair in calls){
            if (pair.second == id) {
                pair.first.cancel()
                calls.remove(pair)
                return true
            }
        }
        return false
    }

    /**
     * temporarly save video to public storage and return its Uri from MediaStore
     */
    fun share(fileName: String, context: Context) {
        val originalFile = File(LocalFilesRepository.getFullVideoPath(fileName, context))
        try {
            val uri = LocalFilesRepository.createTemporaryCopyInPublicStorage(
                originalFile,
                context
            )
            _uriToShare.postValue(Event(uri))
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException

                // Signal to the Activity that it needs to request permission and
                // try the share again if it succeeds.
                pendingVideo = fileName
                _permissionNeededForUpdate.postValue(
                    recoverableSecurityException.userAction.actionIntent.intentSender
                )
            } else {
                throw securityException
            }
        }
    }

    fun sharePendingImage(context: Context) {
        pendingVideo?.let { image ->
            pendingVideo = null
            share(image, context)
        }
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