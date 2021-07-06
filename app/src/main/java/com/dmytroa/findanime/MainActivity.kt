package com.dmytroa.findanime

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.URLUtil
import android.widget.ImageButton
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
import com.dmytroa.findanime.fragments.SharedInterfaces
import com.dmytroa.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment
import com.dmytroa.findanime.fragments.search.Interfaces
import com.dmytroa.findanime.fragments.search.SearchFragment.Companion.READ_MEDIA_PERMISSION_REQUEST
import com.dmytroa.findanime.repositories.LocalFilesRepository
import com.dmytroa.findanime.retrofit.RetrofitInstance
import com.dmytroa.findanime.retrofit.SearchService
import com.dmytroa.findanime.shared.SafeClickListener
import com.dmytroa.findanime.shared.SharedViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.MalformedURLException
import java.net.URL


class MainActivity : AppCompatActivity(), ImageDrawerListDialogFragment.OnImageClickListener,
    SharedInterfaces.FragmentListener,
    NavigationView.OnNavigationItemSelectedListener,
    SharedInterfaces.OnCreateToolbar {

    private val searchService = RetrofitInstance.getInstance().create(SearchService::class.java)
    private val FragmentManager.currentNavigationFragment: Fragment?
        get() = primaryNavigationFragment?.childFragmentManager?.fragments?.first()

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabImages: FloatingActionButton
    private lateinit var fabUrl: FloatingActionButton

    private lateinit var animationFwd: Animation
    private lateinit var animationBwd: Animation

    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var clipboard: ClipboardManager

    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fabMain = findViewById(R.id.floatingActionButton)
        fabMain.setOnSafeClickListener { requestPermission() }
        fabImages = findViewById(R.id.floatingActionButtonFromImage)
        fabUrl = findViewById(R.id.floatingActionButtonFromUrl)

        fabImages.setOnClickListener { requestPermission() }

        fabUrl.setOnClickListener { buildUrlInputDialog() }

        animationFwd = AnimationUtils.loadAnimation(this, R.anim.fab_rotate_fwd)
        animationBwd = AnimationUtils.loadAnimation(this, R.anim.fab_rotate_bwd)
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

        LocalFilesRepository.createNoMediaFile(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }


    @SuppressLint("InflateParams")
    private fun buildUrlInputDialog(){

        fun getTextFromClipBoard(): String {
            if (!clipboard.hasPrimaryClip()) return ""
            if (!(clipboard.primaryClipDescription!!.hasMimeType(MIMETYPE_TEXT_PLAIN))) return ""
            val item = clipboard.primaryClip?.getItemAt(0)
            return (item?.text?.trim() ?: "").toString()
        }

        fun isEmpty(textInput: TextInputEditText): Boolean {
            return  textInput.text.toString().matches(Regex(""))
        }

        fun changeImage(button: ImageButton, textInput: TextInputEditText) {
            if (isEmpty(textInput)) {
                button.setImageResource(R.drawable.ic_baseline_content_paste_24)
            } else {
                button.setImageResource(R.drawable.ic_baseline_close_24)
            }
        }

        fun isValidUrl(urlString: String): Boolean {
            try {
                URL(urlString)
                return URLUtil.isValidUrl(urlString) && Patterns.WEB_URL.matcher(urlString).matches()
            } catch (ignored: MalformedURLException) {}
            return false
        }

        val layout = layoutInflater.inflate(R.layout.dialog_input_url, null)
        val textInput = layout.findViewById<TextInputEditText>(R.id.dialog_uri_input)
        val pasteButton = layout.findViewById<ImageButton>(R.id.paste_button)
        textInput.setText(getTextFromClipBoard())
        changeImage(pasteButton, textInput)
        pasteButton.setOnClickListener {
            if (isEmpty(textInput)) {
                textInput.setText(getTextFromClipBoard())
            } else {
                textInput.setText("")
            }
            changeImage(pasteButton, textInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Search with Url")
            .setView(layout)
            .setPositiveButton("Search") { dialog: DialogInterface, i: Int ->
                submitRequest(textInput.text.toString())
                dialog.dismiss()
            }
            .show()

        val posButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        posButton.isEnabled = isValidUrl(textInput.text.toString())
        textInput.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                posButton.isEnabled = isValidUrl(s.toString())
                changeImage(pasteButton, textInput)
            }
        })
    }

    private fun requestPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,  arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                READ_MEDIA_PERMISSION_REQUEST
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
        if (requestCode == READ_MEDIA_PERMISSION_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImageDrawerListDialogFragment()
            } else {
                Snackbar.make(fabMain, "You can always enable gallery in settings", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }
        }
    }

    private fun showImageDrawerListDialogFragment() {
        ImageDrawerListDialogFragment
            .newInstance()
            .show(supportFragmentManager, "dialog")
    }

    override fun onDrawerImageClick(imageUri: Uri) {
        submitRequest(imageUri)
    }

    private fun submitRequest(imageUri: Uri) {
        supportFragmentManager.currentNavigationFragment?.let {fragment ->
            if (fragment is Interfaces.SubmitSearchRequest) {
                (fragment as Interfaces.SubmitSearchRequest).imageRequest(imageUri)
            }
        }
    }

    fun submitRequest(url: String) {
        supportFragmentManager.currentNavigationFragment?.let {fragment ->
            if (fragment is Interfaces.SubmitSearchRequest) {
                (fragment as Interfaces.SubmitSearchRequest).urlRequest(url)
            }
        }
    }

    private fun View.setOnSafeClickListener(onSafeClick: (View) -> Unit) {
        val safeClickListener = SafeClickListener {
            onSafeClick(it)
        }
        setOnClickListener(safeClickListener)
    }

    override var extraFabsIsExpanded: Boolean
        get() = sharedViewModel.extraFabsIsExpanded
        set(value) {sharedViewModel.extraFabsIsExpanded = value}

    override fun restoreDefaultState() {
        if (extraFabsIsExpanded) {
            fabMain.startAnimation(animationBwd)
            fabImages.hide()
            fabUrl.hide()
        }
    }

    override fun restoreExpandableState() {
        if (extraFabsIsExpanded) {
            fabMain.startAnimation(animationFwd)
            fabImages.show()
            fabUrl.show()
        } else {
            fabImages.hide()
            fabUrl.hide()
        }
    }

    override fun hideMainFab() {
        if(!fabMain.isOrWillBeHidden){
            fabMain.hide()
            if (extraFabsIsExpanded) {
                fabImages.hide()
                fabUrl.hide()
            }
        }
    }

    override fun showMainFab() {
        if(fabMain.isOrWillBeHidden){
            fabMain.show()
            if (extraFabsIsExpanded) {
                fabImages.show()
                fabUrl.show()
            }
        }
    }

    override fun hideShowExtraFabsFunction() {
        extraFabsIsExpanded = if (extraFabsIsExpanded) {
            fabImages.hide()
            fabUrl.hide()
            fabMain.startAnimation(animationBwd)
            false
        } else {
            fabImages.show()
            fabUrl.show()
            fabMain.startAnimation(animationFwd)
            true
        }
    }

    override fun showSnackBar() {
        //TODO("Not yet implemented")
    }

    override fun setupFab(fabIconRes: Int, function: () -> Unit) {
        fabMain.setImageResource(fabIconRes)
        fabMain.setOnSafeClickListener { function() }
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
                        Snackbar.make(fabMain, "Something went wrong", Snackbar.LENGTH_LONG).show()
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