package com.dmytroa.findanime.repositories

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.dmytroa.findanime.dataClasses.Album
import com.dmytroa.findanime.roomDB.dao.SearchDao
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LocalFilesRepository(private val searchDao: SearchDao) {

    suspend fun insert(searchItem: SearchItem): Long = searchDao.insert(searchItem)

    fun delete(searchItem: SearchItem?) {
        if (searchItem == null) return
        CoroutineScope(Dispatchers.IO).launch{
            searchDao.delete(searchItem)
            deleteImage(searchItem.imageURI)
//            deleteVideo(searchItem.video)
        }
    }

    fun update(searchItem: SearchItem) =
        CoroutineScope(Dispatchers.IO).launch{ searchDao.update(searchItem) }

    suspend fun get(id: Long): SearchItem = searchDao.get(id)

    fun setIsBookmarked(searchItem: SearchItem, b: Boolean) {
        CoroutineScope(Dispatchers.IO).launch{ searchDao.setIsBookmarked(searchItem.id, b) }
    }

    fun getAll(): Flow<Array<SearchItem>> = searchDao.getAll()

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
         * copy image to internal storage and send path to new file
         **/
        fun copyImageToInternalStorage(imageUri: Uri, context: Context?): String? {
            val dstName = System.currentTimeMillis()
            var dstPath: String? = context?.filesDir.toString() + File.separatorChar + dstName
            val ins = context?.contentResolver?.openInputStream(imageUri)
            val outs = BufferedOutputStream(FileOutputStream(dstPath, false))

            try {
                val buf = ByteArray(1024)
                ins?.read(buf)
                do {
                    outs.write(buf)
                } while (ins?.read(buf) != -1)
            } catch (e: IOException) {
                e.printStackTrace()
                dstPath = null
            } finally {
                try {
                    ins?.close()
                    outs.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    dstPath = null
                }
                return dstPath
            }
        }

        fun saveVideo(body: ResponseBody, context: Context?): String? {
            val dstName = System.currentTimeMillis()
            val dstPath: String = context?.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                .toString() + File.separatorChar + dstName + ".mp4"

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
                    return dstPath
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                ins.close()
                Log.i(TAG, "saveVideo: success")
            }
            return null
        }
    }

    private fun deleteVideo(video: String?) {
        if (video == null) return
        val myFile = File(video)
        Log.i(TAG, "deleteVideo: ${myFile.exists()}")
        if (myFile.exists()) myFile.delete()
    }

    fun deleteImage(imageURI: String) {
        val myFile = File(imageURI)
        Log.i(TAG, "deleteImage: ${myFile.exists()}")
        if (myFile.exists()) myFile.delete()
    }

}