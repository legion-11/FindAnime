package com.dmytroa.findanime.repositories

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.dmytroa.findanime.dataClasses.Album
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import com.dmytroa.findanime.roomDB.dao.SearchDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.*


class LocalFilesRepository(private val searchDao: SearchDao) {

    suspend fun insert(searchItem: SearchItem): Long = searchDao.insert(searchItem)

    suspend fun insertAllAndReturnIdOfFirst(list: List<SearchResult>): Long
    = searchDao.insertAllAndReturnIdOfFirst(list)

    fun delete(searchItem: SearchItem, context: Context) {
        CoroutineScope(Dispatchers.IO).launch{
            searchItem.imageFileName?.let { deleteImage(it, context) }
            searchDao.delete(searchItem)
            searchItem.videoFileName?.let { deleteVideo(it, context) }
        }
    }

    suspend fun getSearchItemById(id: Long) = searchDao.getSearchItemById(id)

    suspend fun update(searchItem: SearchItem) = searchDao.update(searchItem)

    suspend fun getSearchItemWithSelectedResult(id: Long): SearchItemWithSelectedResult? = searchDao.getSearchItemWithSelectedResult(id)

    fun setIsBookmarked(searchItem: SearchItem, b: Boolean) {
        CoroutineScope(Dispatchers.IO).launch{ searchDao.setIsBookmarked(searchItem.id, b) }
    }

    fun getAll(): Flow<Array<SearchItemWithSelectedResult>> {
        Log.i(TAG, "getAll: ")
        return searchDao.getAllItemsWithSelectedResult()
    }

    suspend fun getAllResultsByItemId(id: Long): Array<SearchResult> =
        searchDao.getAllResultsByItemId(id)



    companion object {
        private const val TAG = "LocalFilesRepository"

        fun createNoMediaFile(context: Context) {
            val dstDir = File(getVideoDirPath(context))
            if (!dstDir.exists())
                dstDir.mkdirs()
            val fileToCreate = File(dstDir, ".nomedia")
            if (!fileToCreate.exists())
                fileToCreate.createNewFile()
        }

        fun deleteNoMediaFile(context: Context) {
            val dstDir = File(getVideoDirPath(context))
            val fileToCreate = File(dstDir, ".nomedia")
            if (fileToCreate.exists())
                fileToCreate.delete()
        }

        /**
         * Obtain all albums on device
         **/
        @SuppressLint("InlinedApi") // ImageColumns.BUCKET_DISPLAY_NAME was available before api 29
        fun getAlbums(context: Context): ArrayList<Album> {
            val albums = arrayListOf<Album>()
            val albumsNames = arrayListOf<String>()

            val uriExternal = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
            )

            val cur = context.contentResolver.query(
                uriExternal,
                projection,
                null,
                null,
                null
            )

            if ( cur != null && cur.count > 0 ) {
                if (cur.moveToFirst()) {
                    val columnIndexID = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val columnIndexAlbumName = cur.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME )
                    while (cur.moveToNext()) {
                        val imageId = cur.getLong(columnIndexID)
                        val albumName = cur.getString(columnIndexAlbumName)
                        Log.i(TAG," imageId=${imageId} albumName=${albumName}")
                        if ( albumsNames.contains( albumName ) ) {
                            for (album in albums) {
                                if (album.name == albumName){
                                    album.imageIds.add(imageId)
                                    Log.i( TAG, "A photo $imageId was added to album => $album")
                                    break
                                }
                            }
                        } else {
                            val album = Album(albumName)
                            Log.i( TAG, "A new was Album added => $album")
                            album.imageIds.add(imageId)
                            albums.add(album)
                            albumsNames.add(albumName)
                            Log.i( TAG, "A photo $imageId was added to album => $album")
                        }
                    }
                }
            }
            cur?.close()
            // LIFO (last taken image will be first in list)
            for (album in albums) {
                album.imageIds.reverse()
            }
            Log.i( TAG, "all albums => $albums")
            return albums
        }

        /**
         * copy image to internal storage and send fileName
         **/
        fun copyImageToCacheDir(imageUri: Uri, context: Context): String? {
            val dstName = System.currentTimeMillis().toString()
            val dstDir = File(getImagesDirPath(context))
            if (!dstDir.exists())
                dstDir.mkdirs()
            Log.i(TAG, "copyImageToInternalStorage: ${dstDir.path}")
            val fileToCreate = File(dstDir, dstName)

            if (fileToCreate.createNewFile()) {
                Log.i(TAG, "copyImageToInternalStorage: created")
                return try {
                    context.contentResolver?.openInputStream(imageUri).use { input ->
                        fileToCreate.outputStream().use { output ->
                            input?.copyTo(output, 1024)
                            Log.i(TAG, "finished ${fileToCreate.path}")
                        }
                    }
                    dstName
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
            }
            return null
        }

        fun saveVideoToExternalStorage(body: ResponseBody, fileName: String, context: Context): String? {
            val dstDir = File(getVideoDirPath(context))
            if (!dstDir.exists())
                dstDir.mkdirs()
            val fileToCreate = File(dstDir, fileName)

            if (fileToCreate.createNewFile()) {
                Log.i(TAG, "saveVideo: created")
                return try {
                    body.byteStream().use { input ->
                        fileToCreate.outputStream().use { output ->
                            input.copyTo(output, 2 * 1024)
                            Log.i(TAG, "saveVideo: finished ${fileToCreate.path}")
                        }
                    }
                    fileName
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
            }
            return null
        }

        fun createTemporaryCopyInPublicStorage(file: File, context: Context): Uri? {
            val fileName = "tmp"
            return if(Build.VERSION.SDK_INT >= 29) {
                val uri = findCreatedTemporaryUri(context, fileName, TEMPORARY_DIR_Q)
                copyVideoQAndAbove(context, file, uri, fileName, TEMPORARY_DIR_Q)
            } else {

                val uri = findCreatedTemporaryUri(context, fileName, TEMPORARY_DIR_BELOWQ)
                copyVideoBelowQ(context, file, uri, fileName, TEMPORARY_DIR_BELOWQ)
            }
        }

        private fun findCreatedTemporaryUri(context: Context, fileName: String, path: String): Uri? {
            val collection = if(Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val selection = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.Video.Media.TITLE} = ? AND " +
                        "${MediaStore.Video.Media.RELATIVE_PATH} = ? "
            } else {
                "${MediaStore.Video.Media.TITLE} = ? AND " +
                        "${MediaStore.Video.Media.DATA} = ? "
            }

            val args = if (Build.VERSION.SDK_INT >= 29) {
                arrayOf(fileName, path)
            } else {
                arrayOf(fileName, File(path, fileName).absolutePath)
            }

            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID),
                selection,
                args,
                null
            ).use { cursor ->
                return if (cursor != null && cursor.moveToFirst()) {
                    val columnIndexID = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val id = cursor.getLong(columnIndexID)
                    Log.i(TAG, "saveVideoQAndAbove: contentUri was already added $id $path $fileName")
                    Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "$id")
                } else {
                    null
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun copyVideoQAndAbove(context: Context,
                                       fileToCopy: File,
                                       uri: Uri?,
                                       fileName: String,
                                       relPath: String): Uri? {

            val contentDetails = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val contentUri = if (uri != null) {
                context.contentResolver.update(uri, contentDetails, null, null)
                uri
            } else {
                contentDetails.apply {
                    Log.i(TAG, "saveVideoQAndAbove: contentUri insert")
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
                }
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver.insert(collection, contentDetails)
            }

            Log.i(TAG, "saveVideoQAndAbove: $contentUri")
            return contentUri?.let { createdUri ->
                try {
                    context.contentResolver.openFileDescriptor(createdUri, "w").use { pfd ->
                        ParcelFileDescriptor.AutoCloseOutputStream(pfd).write(fileToCopy.readBytes())
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                contentDetails.clear()
                contentDetails.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(createdUri, contentDetails, null, null)
                createdUri
            }
        }

        private fun copyVideoBelowQ(context: Context,
                                    fileToCopy: File,
                                    uri: Uri?,
                                    fileName: String,
                                    dstParentPath: String): Uri? {
            val dstDir = File(dstParentPath)
            if (!dstDir.exists())
                dstDir.mkdirs()

            val fileToCreate = File(dstDir, fileName)

            fileToCreate.delete()
            fileToCreate.createNewFile()
            Log.i(TAG, "saveVideo: created ${fileToCreate.name}")
            try {
                fileToCopy.inputStream().use { input ->
                    fileToCreate.outputStream().use { output ->
                        input.copyTo(output, 2 * 1024)
                        Log.i(TAG, "saveVideo: finished ${fileToCreate.path}")
                    }
                }
                return uri ?: let {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.TITLE, fileToCreate.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.DATA, fileToCreate.path)
                    }
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return null
        }

        private val TEMPORARY_DIR_Q = Environment.DIRECTORY_MOVIES + File.separator +
                "Find Anime" + File.separator +
                "temporary" + File.separator

        private val TEMPORARY_DIR_BELOWQ = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES).absolutePath + File.separator +
                "Find Anime" + File.separator +
                "temporary" + File.separator

        fun getVideoDirPath(context: Context) =
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString()

        fun getFullVideoPath(fileName: String, context: Context) =
            getVideoDirPath(context) + File.separatorChar + fileName

        fun getImagesDirPath(context: Context) =
            context.cacheDir.toString() + File.separatorChar + "temporary images"

        fun getFullImagePath(fileName: String, context: Context) =
            getImagesDirPath(context) + File.separatorChar + fileName

        fun deleteVideo(videoName: String, context: Context) {
            val videoURI = getFullVideoPath(videoName, context)
            val myFile = File(videoURI)
            Log.i(TAG, "deleteVideo: file $videoName existence = ${myFile.exists()}")
            if (myFile.exists()) myFile.delete()
        }

        fun deleteImage(imageName: String?, context: Context) {
            Log.i(TAG, "deleteImage: file $imageName")
            imageName?.let {
                val imageURI =  getFullImagePath(it, context)
                val myFile = File(imageURI)
                Log.i(TAG, "deleteImage: file existence = ${myFile.exists()}")
                if (myFile.exists()) myFile.delete()
            }
        }
    }
}