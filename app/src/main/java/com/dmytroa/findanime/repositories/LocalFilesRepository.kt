package com.dmytroa.findanime.repositories

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
        fun copyImageToInternalStorage(imageUri: Uri, context: Context): String? {
            val dstName: Long = System.currentTimeMillis()
            val dstPath: String = context.filesDir.toString() + File.separatorChar + dstName
            val ins = context.contentResolver?.openInputStream(imageUri)
            val outs = BufferedOutputStream(FileOutputStream(dstPath, false))

            try {
                val buf = ByteArray(1024)
                ins?.read(buf)
                do {
                    outs.write(buf)
                } while (ins?.read(buf) != -1)
                outs.flush()
                return dstName.toString()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    ins?.close()
                    outs.close()
                    Log.i(TAG, "saveImage: success")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return null
        }

        fun saveVideo(body: ResponseBody, fileName: String, context: Context): String? {
            val dstPath = getFullVideoURI(fileName, context)

            val ins = body.byteStream()
            val outs = FileOutputStream(dstPath)
            try {
                outs.use { output ->
                    val buf = ByteArray(2 * 1024)
                    var read: Int
                    while (ins.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                    output.flush()
                }
                Log.i(TAG, "saveVideo: $fileName success")
                return fileName
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    ins.close()
                    outs.close()
//                    insertInGallery(context, dstName)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return null
        }


        fun getFullVideoURI(fileName: String, context: Context) =
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                .toString() + File.separatorChar + fileName

        fun getFullImageURI(fileName: String, context: Context) =
            context.filesDir.toString() + File.separatorChar + fileName

        private fun insertInGallery(context: Context, videoFileName: String) {
            val valuesvideos = ContentValues()
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString()

            val fullPath = directory + File.separatorChar + videoFileName

            valuesvideos.put(MediaStore.Video.Media.TITLE, videoFileName)
            valuesvideos.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
            valuesvideos.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            valuesvideos.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            val uriSavedVideo = if (Build.VERSION.SDK_INT >= 29) {
                valuesvideos.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "Folder")
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver?.insert(collection, valuesvideos)
            } else {
                val createdvideo = File(directory, videoFileName)
                valuesvideos.put(MediaStore.Video.Media.DATA, createdvideo.absolutePath)
                context.contentResolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valuesvideos)
            }

            if (Build.VERSION.SDK_INT >= 29) {
                valuesvideos.put(MediaStore.Video.Media.DATE_TAKEN, videoFileName);
                valuesvideos.put(MediaStore.Video.Media.IS_PENDING, 1);
            }
            try {
                uriSavedVideo?.let {
                    context.contentResolver?.openFileDescriptor(it, "w")?.let { pfd ->
                        val out = FileOutputStream(pfd.fileDescriptor)
                        val storageDir = File(directory)

                        //Directory and the name of your video file to copy
                        val videoFile = File(fullPath)

                        val ins = FileInputStream(videoFile)

                        val buf = ByteArray(2*1024)
                        var len: Int
                        while (ins.read(buf).also { len = it } > 0) {
                            out.write(buf, 0, len)
                        }
                        out.close()
                        ins.close()
                        pfd.close()
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        valuesvideos.clear();
                        valuesvideos.put(MediaStore.Video.Media.IS_PENDING, 0);
                        context.contentResolver?.update(uriSavedVideo, valuesvideos, null, null);

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun deleteVideo(videoName: String, context: Context) {
            val videoURI = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                .toString() + File.separatorChar + videoName

            val myFile = File(videoURI)
            Log.i(TAG, "deleteVideo: file $videoName existence = ${myFile.exists()}")
            if (myFile.exists()) myFile.delete()
        }

        fun deleteImage(imageName: String?, context: Context) {
            Log.i(TAG, "deleteImage: file $imageName")
            imageName?.let {
                val imageURI =  getFullImageURI(it, context)
                val myFile = File(imageURI)
                Log.i(TAG, "deleteImage: file existence = ${myFile.exists()}")
                if (myFile.exists()) myFile.delete()
            }
        }
    }
}