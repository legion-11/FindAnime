package com.dmytroa.findanime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.findNavController
import com.dmytroa.findanime.dataClasses.retrofit.Quota
import com.dmytroa.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment
import com.dmytroa.findanime.fragments.search.SearchFragment
import com.dmytroa.findanime.fragments.search.SearchFragment.Companion.REQUEST_PERMISSION
import com.dmytroa.findanime.fragments.seeOtherOptions.SeeOtherOptionsFragment
import com.dmytroa.findanime.retrofit.RetrofitInstance
import com.dmytroa.findanime.retrofit.SearchService
import com.dmytroa.findanime.shared.OnFragmentListener
import com.dmytroa.findanime.shared.SafeClickListener
import com.dmytroa.findanime.shared.SharedViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity(), ImageDrawerListDialogFragment.OnImageClickListener,
    OnFragmentListener,
    NavigationView.OnNavigationItemSelectedListener,
    SearchFragment.OnCreateToolbar {

    private val searchService = RetrofitInstance.getInstance().create(SearchService::class.java)
    private val FragmentManager.currentNavigationFragment: Fragment?
        get() = primaryNavigationFragment?.childFragmentManager?.fragments?.first()

    private lateinit var fab: FloatingActionButton
    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var drawerLayout: DrawerLayout

    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab = findViewById(R.id.floatingActionButton)
        fab.setOnSafeClickListener { requestPermission() }
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        drawerLayout = findViewById(R.id.drawer_layout)

        findViewById<NavigationView>(R.id.nav_view).setNavigationItemSelectedListener(this)

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

    private fun showImageDrawerListDialogFragment() {
        ImageDrawerListDialogFragment
            .newInstance()
            .show(supportFragmentManager, "dialog")
    }

    override fun onImageClick(imageUri: Uri) {
        supportFragmentManager.currentNavigationFragment?.let {fragment ->
            if (fragment is ImageDrawerListDialogFragment.OnImageClickListener) {
                (fragment as ImageDrawerListDialogFragment.OnImageClickListener).onImageClick(imageUri)
            }
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

            sharedViewModel.makeReplacement = true
            findNavController(R.id.nav_host_fragment).navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.settings_menu_item -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.SettingsFragment)
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            }
            R.id.about_menu_item -> {
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            }
            R.id.quota_menu_item -> {
                val call = searchService.getQuota()
                call.enqueue(object: Callback<Quota> {
                    override fun onResponse(call: Call<Quota>, response: Response<Quota>) {
                        val body = response.body()!!
                        AlertDialog.Builder(this@MainActivity as Context)
                            .setTitle("Quota for ${body.id}")
                            .setMessage("You used ${body.quotaUsed} out of ${body.quota}")
                            .setPositiveButton("Close") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                    override fun onFailure(call: Call<Quota>, t: Throwable) {
                        Snackbar.make(fab, "Something went wrong", Snackbar.LENGTH_LONG).show()
                    }
                })
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            }
            else -> {false}
        }
    }

    override fun prepareToolbar(resId: Int, searchViewIsVisible: Boolean) {
        searchView.visibility = if (searchViewIsVisible) View.VISIBLE else View.INVISIBLE
        toolbar.setNavigationIcon(resId)
    }

    override fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }
}