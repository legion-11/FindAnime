package com.dmytroa.findanime

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.dmytroa.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment
import com.dmytroa.findanime.fragments.search.SearchFragment
import com.dmytroa.findanime.fragments.search.SearchFragment.Companion.REQUEST_PERMISSION
import com.dmytroa.findanime.fragments.seeOtherOptions.SeeOtherOptionsFragment
import com.dmytroa.findanime.shared.SafeClickListener
import com.dmytroa.findanime.shared.SharedViewModel
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar


class MainActivity : AppCompatActivity(), ImageDrawerListDialogFragment.OnImageClickListener,
    SearchFragment.OnSearchFragmentListener,
    SeeOtherOptionsFragment.OnOtherOptionsFragmentListener {

    private val FragmentManager.currentNavigationFragment: Fragment?
        get() = primaryNavigationFragment?.childFragmentManager?.fragments?.first()

    private lateinit var fab: FloatingActionButton
    private lateinit var toolbar: Toolbar
    private lateinit var appBar: AppBarLayout
    private var bookmarksMenuItem: MenuItem? = null
    private lateinit var searchView: SearchView
    private var bookmarksIsChecked = false

    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState?.getBoolean(KEY_BOOKMARKS_FILTER_IS_ENABLED)?.let {
            bookmarksIsChecked = it
        }
        fab = findViewById(R.id.floatingActionButton)
        fab.setOnSafeClickListener { requestPermission() }
        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)

        val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!.findNavController()

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            when(destination.id) {

                R.id.action_SearchFragment_to_SeeOtherOptionsFragment -> {
                    fab.setImageResource(R.drawable.ic_baseline_check_24)
                    fab.setOnSafeClickListener {
                        supportFragmentManager
                            .findFragmentById(R.id.nav_host_fragment)
                            ?.findNavController()
                            ?.navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
                    }
                }
            }
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24)
        searchView = findViewById(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                Log.i("MainActivity", "onQueryTextChange: $newText")
                sharedViewModel.setFilterText(newText)
                return true
            }
        })
    }

    private fun requestPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,  arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERMISSION
            )
            return
        } else {
            //TODO for now it always asks permission, change to getting from shared properties last answer
            showImageDrawerListDialogFragment()
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
                showImageDrawerListDialogFragment()
            } else {
                Snackbar.make(fab, "You can always enable gallery in settings", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        bookmarksMenuItem = menu.findItem(R.id.action_filter_bookmarks)
        bookmarksMenuItem!!.isChecked = bookmarksIsChecked
        if (bookmarksIsChecked) { bookmarksMenuItem!!.setIcon(R.drawable.ic_baseline_bookmark_24) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_filter_bookmarks -> {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    item.setIcon(R.drawable.ic_baseline_bookmark_24)
                } else {
                    item.setIcon(R.drawable.ic_baseline_bookmark_border_24)
                }
                sharedViewModel.setFilterBookmarks(item.isChecked)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_BOOKMARKS_FILTER_IS_ENABLED, bookmarksMenuItem?.isChecked ?: false)
        super.onSaveInstanceState(outState)
    }

    private fun showImageDrawerListDialogFragment() {
        ImageDrawerListDialogFragment
            .newInstance()
            .show(supportFragmentManager, "dialog")
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

    override fun hideFab() {
        if(!fab.isOrWillBeHidden){
            fab.hide()
        }
    }

    override fun showFab() {
        if(fab.isOrWillBeHidden){
            fab.show()
        }
    }

    override fun setSearchFragmentFun() {
        fab.setImageResource(android.R.drawable.ic_input_add)
        fab.setOnSafeClickListener { requestPermission() }
    }

    override fun setOtherOptionsFun() {
        fab.setImageResource(R.drawable.ic_baseline_check_24)
        fab.setOnSafeClickListener {
            val bundle = Bundle().apply { putBoolean(KEY_INVOKE_VIDEO_REPLACEMENT, true) }
            findNavController(R.id.nav_host_fragment)
                .navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment, bundle)
        }
    }


    companion object {
        const val KEY_BOOKMARKS_FILTER_IS_ENABLED = "bookmarks is enabled"
        const val KEY_INVOKE_VIDEO_REPLACEMENT = "invokeVideoReplacement"
    }
}