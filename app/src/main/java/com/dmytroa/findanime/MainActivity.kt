package com.dmytroa.findanime

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.dmytroa.findanime.fragments.ImageDrawerListDialogFragment
import com.dmytroa.findanime.shared.SafeClickListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar


class MainActivity : AppCompatActivity(), ImageDrawerListDialogFragment.OnImageClickListener {

    private val FragmentManager.currentNavigationFragment: Fragment?
        get() = primaryNavigationFragment?.childFragmentManager?.fragments?.first()

    private lateinit var fab: FloatingActionButton
    private lateinit var toolbar: Toolbar
    private lateinit var appBar: AppBarLayout
    private var mActionMode: ActionMode? = null
    private lateinit var mActionModeCallback: MyActionModeCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val bookmarked = savedInstanceState?.getBoolean(BOOKMARKED_KEY)
        mActionModeCallback = MyActionModeCallback(bookmarked)
        showContextualActionBar(savedInstanceState?.getBoolean(CALL_ACTION_MODE) , bookmarked)
        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24)

        fab = findViewById(R.id.fab)
        fab.setOnSafeClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,  arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_PERMISSION)
                return@setOnSafeClickListener
            } else {
                //TODO for now it always asks permission, change to getting from shared properties last answer
                ImageDrawerListDialogFragment
                    .newInstance()
                    .show(supportFragmentManager, "dialog")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_filter_bookmarks -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun hideFab() {
        if (fab.isShown) {
            fab.hide()
        }
    }

    fun showFab() {
        if(fab.isOrWillBeHidden) {
            fab.show()
        }
    }

    fun showContextualActionBar(showMenuForSelection: Boolean?, isBookmarked: Boolean?) {
        if (showMenuForSelection == null) return
        if (showMenuForSelection) {
            isBookmarked?.let { mActionModeCallback.bookmarked=it }
            if (mActionMode != null) {
                mActionModeCallback.setBookmarksButtonIcon()
                return
            }
            mActionMode = startSupportActionMode(mActionModeCallback)
        } else {
            mActionMode?.finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ImageDrawerListDialogFragment
                    .newInstance()
                    .show(supportFragmentManager, "dialog")
            } else {
                Snackbar.make(fab, "You can always enable gallery in settings", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(BOOKMARKED_KEY, mActionModeCallback.bookmarked)
        outState.putBoolean(CALL_ACTION_MODE, mActionMode != null)
        super.onSaveInstanceState(outState)

    }

    companion object {
        const val REQUEST_PERMISSION = 100
        const val BOOKMARKED_KEY = "bookmarked"
        const val CALL_ACTION_MODE = "call action mode"
    }

    override fun onImageClick(imageUri: Uri) {
        supportFragmentManager.currentNavigationFragment?.let {fragment ->
            (fragment as ImageDrawerListDialogFragment.OnImageClickListener).onImageClick(imageUri)
        }
    }

    private fun View.setOnSafeClickListener(onSafeClick: (View) -> Unit) {
        val safeClickListener = SafeClickListener {
            onSafeClick(it)
        }
        setOnClickListener(safeClickListener)
    }

    private inner class MyActionModeCallback(bookmarked: Boolean?): ActionMode.Callback {
        var bookmarked = bookmarked ?: false
        private lateinit var bookmarkItem: MenuItem

        fun setBookmarksButtonIcon(){
            bookmarkItem.setIcon(
                if (bookmarked)
                    R.drawable.ic_baseline_bookmark_24
                else
                    R.drawable.ic_baseline_bookmark_border_24
            )
        }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
            mode?.menuInflater?.inflate(R.menu.menu_for_selection, menu)
            bookmarkItem = menu.findItem(R.id.action_bookmarks)
            setBookmarksButtonIcon()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val fragment = (supportFragmentManager.currentNavigationFragment as OnActionBarCallback)
            return when(item?.itemId) {
                R.id.action_delete -> {
                    fragment.delete()
                    mode?.finish()
                    true
                }
                R.id.action_see -> {
                    //TODO
                    mode?.finish()
                    true
                }
                R.id.action_bookmarks -> {
                    bookmarked = !bookmarked
                    setBookmarksButtonIcon()
                    fragment.setIsBookmarked(bookmarked)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            val fragment = (supportFragmentManager.currentNavigationFragment as OnActionBarCallback)
            fragment.unselectAll()
            mActionMode = null
        }
    }

    interface OnActionBarCallback {
        fun unselectAll()
        fun setIsBookmarked(b: Boolean)
        fun delete()
    }

}