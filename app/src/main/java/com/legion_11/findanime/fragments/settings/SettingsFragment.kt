package com.legion_11.findanime.fragments.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.legion_11.findanime.R
import com.legion_11.findanime.fragments.SharedInterfaces

/**
 * fragment for showing default preferences from root_preferences
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var fragmentListener: SharedInterfaces.FragmentListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            fragmentListener = context as SharedInterfaces.FragmentListener
        }catch(e: RuntimeException){
            throw RuntimeException(activity.toString()+" must implement method")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        fragmentListener.hideMainFab()
    }

    /**
     * open browser
     */
    private fun openUrl(url: String) {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

//        val showInGalleryPreference: SwitchPreferenceCompat? =
//            preferenceManager.findPreference("show_files_in_gallery")
//        showInGalleryPreference?.setOnPreferenceChangeListener { sharedPreferences, newValue ->
//            newValue as Boolean
//            if (newValue) {
//                LocalFilesRepository.deleteNoMediaFile(requireContext())
//            } else {
//                LocalFilesRepository.createNoMediaFile(requireContext())
//            }
//            true
//        }

        val myGithubPreference: Preference? = preferenceManager.findPreference("my_github")
        myGithubPreference?.setOnPreferenceClickListener {
            val url = "https://github.com/legion-11"
            openUrl(url)
            true
        }
        val sorulyGithub: Preference? = preferenceManager.findPreference("soruly_github")
        sorulyGithub?.setOnPreferenceClickListener {
            val url = "https://github.com/soruly"
            openUrl(url)
            true
        }

        val pikisuperstarFreePic: Preference? = preferenceManager.findPreference("app_logo_attribution")
        pikisuperstarFreePic?.setOnPreferenceClickListener {
            val url = "https://www.freepik.com/vectors/character"
            openUrl(url)
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        fragmentListener.prepareToolbar(
            androidx.appcompat.R.drawable.abc_ic_ab_back_material,
            false
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}