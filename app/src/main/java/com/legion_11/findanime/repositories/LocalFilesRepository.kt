package com.legion_11.findanime.repositories

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.legion_11.findanime.dataClasses.Album
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItem
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchResult
import com.legion_11.findanime.roomDB.dao.SearchDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.*

/**
 * repository for saving data to room db
 */
class LocalFilesRepository(private val searchDao: SearchDao) {

    suspend fun insert(searchItem: SearchItem): Long = searchDao.insert(searchItem)

    suspend fun insertAllAndReturnIdOfFirst(list: List<SearchResult>): Long =
        searchDao.insertAllAndReturnIdOfFirst(list)

    fun delete(searchItem: SearchItem, context: Context) {
        CoroutineScope(Dispatchers.IO).launch{
            searchItem.imageFileName?.let { deleteImage(it, context) }
            searchDao.delete(searchItem)
            searchItem.videoFileName?.let { deleteVideo(it, context) }
        }
    }

    suspend fun getSearchItemById(id: Long) = searchDao.getSearchItemById(id)

    suspend fun update(searchItem: SearchItem) = searchDao.update(searchItem)

    suspend fun getSearchItemWithSelectedResult(id: Long): SearchItemWithSelectedResult? =
        searchDao.getSearchItemWithSelectedResult(id)

    fun setIsBookmarked(searchItem: SearchItem, b: Boolean) {
        CoroutineScope(Dispatchers.IO).launch{ searchDao.setIsBookmarked(searchItem.id, b) }
    }

    fun getAll(): Flow<Array<SearchItemWithSelectedResult>> {
        return searchDao.getAllItemsWithSelectedResult()
    }

    suspend fun getAllResultsByItemId(id: Long): Array<SearchResult> =
        searchDao.getAllResultsByItemId(id)

    companion object {
        private const val TAG = "LocalFilesRepository"

        /**
         * prevent application media files to be shown in gallery
         */
        fun createNoMediaFile(context: Context) {
            val dstDir = File(getVideoDirPath(context))
            if (!dstDir.exists())
                dstDir.mkdirs()
            val fileToCreate = File(dstDir, ".nomedia")
            if (!fileToCreate.exists())
                fileToCreate.createNewFile()
        }

        /**
         * show application media files in gallery
         */
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
                        if ( albumsNames.contains( albumName ) ) {
                            for (album in albums) {
                                if (album.name == albumName){
                                    album.imageIds.add(imageId)
                                    break
                                }
                            }
                        } else {
                            val album = Album(albumName)
                            album.imageIds.add(imageId)
                            albums.add(album)
                            albumsNames.add(albumName)
                        }
                    }
                }
            }
            cur?.close()
            // LIFO (last taken image will be first in list)
            for (album in albums) {
                album.imageIds.reverse()
            }
            return albums
        }

        /**
         * copy image to internal storage and send fileName
         * @return filename of saved file or null if file was not created
         **/
        fun copyImageToCacheDir(imageUri: Uri, context: Context): String? {
            val dstName = System.currentTimeMillis().toString()
            val dstDir = File(getImagesDirPath(context))
            if (!dstDir.exists())
                dstDir.mkdirs()
            val fileToCreate = File(dstDir, dstName)

            if (fileToCreate.createNewFile()) {
                return try {
                    context.contentResolver?.openInputStream(imageUri).use { input ->
                        fileToCreate.outputStream().use { output ->
                            input?.copyTo(output, 1024)
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

        /**
         * save video preview to external storage
         * @return filename of saved file or null if file was not created
         **/
        fun saveVideoToExternalStorage(body: ResponseBody, fileName: String, context: Context): String? {
            val dstDir = File(getVideoDirPath(context))
            if (!dstDir.exists())
                dstDir.mkdirs()
            val fileToCreate = File(dstDir, fileName)

            if (fileToCreate.createNewFile()) {
                return try {
                    body.byteStream().use { input ->
                        fileToCreate.outputStream().use { output ->
                            input.copyTo(output, 2 * 1024)
                        }
                    }
                    body.close()
                    fileName
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
            }
            return null
        }

        /**
         * save video file to public storage
         */
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

        private fun showAllUri(context: Context) {
            val collection = if(Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val pathcol = if(Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.RELATIVE_PATH
            } else {
                MediaStore.Video.Media.DATA
            }

            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    pathcol
                ),
                null,
                null,
                null
            ).use { cursor ->
                 if (cursor != null && cursor.moveToFirst()) {
                    do {
                        val columnIndexID = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val id = cursor.getLong(columnIndexID)
                        val columnIndexTitle = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                        val title = cursor.getString(columnIndexTitle)
                        val columnIndexpath = cursor.getColumnIndexOrThrow(pathcol)
                        val path2 = cursor.getString(columnIndexpath)

                    } while (cursor.moveToNext())

                } else {
                    null
                }
            }
        }

        private fun deleteUri(context: Context, fileName: String, path: String) {
            val collection = if(Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val selection = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.Video.Media.TITLE} = ? " +
                        "AND " +
                        "${MediaStore.Video.Media.RELATIVE_PATH} = ? "
            } else {
                "${MediaStore.Video.Media.TITLE} = ? " +
                        "AND " +
                        "${MediaStore.Video.Media.DATA} = ? "
            }

            val args = if (Build.VERSION.SDK_INT >= 29) {
                arrayOf(fileName, path)
            } else {
                arrayOf(fileName, File(path, fileName).absolutePath)
            }
            context.contentResolver.delete(
                collection,
                selection,
                args
            )
        }

        /**
         * querry contentResolver for file
         * @return uri to founded file or null if there is no file in MediaStore
         */
        private fun findCreatedTemporaryUri(context: Context, fileName: String, path: String): Uri? {
            val collection = if(Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val selection = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.Video.Media.TITLE} = ? " +
                        "AND " +
                        "${MediaStore.Video.Media.RELATIVE_PATH} = ? "
            } else {
                "${MediaStore.Video.Media.TITLE} = ? " +
                        "AND " +
                        "${MediaStore.Video.Media.DATA} = ? "
            }

            val args = if (Build.VERSION.SDK_INT >= 29) {
                arrayOf(fileName, path)
            } else {
                arrayOf(fileName, File(path, fileName).absolutePath)
            }

            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID
                ),
                selection,
                args,
                null
            ).use { cursor ->
                return if (cursor != null && cursor.moveToFirst()) {
                    val columnIndexID = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val id = cursor.getLong(columnIndexID)

                    Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "$id")
                } else {
                    null
                }
            }
        }

        /**
         * save file to publick storage for android 10 and higher
         */
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
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
                }
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver.insert(collection, contentDetails)
            }

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

        /**
         * save file to publick storage for android 9 and lower
         */
        private fun copyVideoBelowQ(context: Context,
                                    fileToCopy: File,
                                    uri: Uri?,
                                    fileName: String,
                                    dstParentPath: String): Uri? {
            val dstDir = File(dstParentPath)
            if (!dstDir.exists())
                dstDir.mkdirs()

            val fileToCreate = File(dstDir, fileName)

            fileToCreate.createNewFile()
            try {
                fileToCopy.inputStream().use { input ->
                    fileToCreate.outputStream().use { output ->
                        input.copyTo(output, 2 * 1024)
                    }
                }
                return uri ?: let {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.TITLE, fileToCreate.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.DATA, fileToCreate.path)
                    }
                    val insert = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

                    insert
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return null
        }

        // relative path to temporary dir
        private val TEMPORARY_DIR_Q = Environment.DIRECTORY_MOVIES + File.separator +
                "Find Anime" + File.separator +
                "temporary" + File.separator

        private val TEMPORARY_DIR_BELOWQ = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES).absolutePath + File.separator +
                "Find Anime" + File.separator +
                "temporary" + File.separator

        private fun getVideoDirPath(context: Context) =
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString()

        fun getFullVideoPath(fileName: String, context: Context) =
            getVideoDirPath(context) + File.separatorChar + fileName

        private fun getImagesDirPath(context: Context) =
            context.cacheDir.toString() + File.separatorChar + "temporary images"

        fun getFullImagePath(fileName: String, context: Context) =
            getImagesDirPath(context) + File.separatorChar + fileName

        fun deleteVideo(videoName: String, context: Context) {
            val videoURI = getFullVideoPath(videoName, context)
            val myFile = File(videoURI)
            if (myFile.exists()) myFile.delete()
        }

        fun deleteImage(imageName: String?, context: Context) {
            imageName?.let {
                val imageURI =  getFullImagePath(it, context)
                val myFile = File(imageURI)
                if (myFile.exists()) myFile.delete()
            }
        }
    }
}