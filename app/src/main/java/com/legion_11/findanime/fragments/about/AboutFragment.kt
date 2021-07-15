package com.legion_11.findanime.fragments.about

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.legion_11.findanime.R
import com.legion_11.findanime.fragments.SharedInterfaces


/**
 * A simple [Fragment] subclass.
 * Used for presenting screen with basic information about program
 */
class AboutFragment : Fragment() {
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
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    /**
     * clear toolbar menu and set back button icon
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        fragmentListener.prepareToolbar(
            androidx.appcompat.R.drawable.abc_ic_ab_back_material,
            false
        )
    }

    /**
     * set back button function in toolbar
     */
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