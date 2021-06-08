package com.dmytroa.findanime.repositories

import android.annotation.SuppressLint
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.dmytroa.findanime.dataClasses.Album

object LocalFilesRepository {

    const val TAG = "LocalFilesRepository"
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
        Log.i( TAG, "all albums => $albums")
        return albums
    }
}