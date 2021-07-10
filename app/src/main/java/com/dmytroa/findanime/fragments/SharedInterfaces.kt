package com.dmytroa.findanime.fragments

object SharedInterfaces {

    /**
     * interface for activity, so fragments can change it's ui and send data
     */
    interface FragmentListener {
        var extraFabsIsExpanded: Boolean

        fun prepareToolbar(resId: Int, searchViewIsVisible: Boolean)
        fun openDrawer()

        fun hideMainFab()
        fun showMainFab()
        fun restoreDefaultState()
        fun restoreExpandableState()
        fun hideShowExtraFabsFunction()
        fun showSnackBar(message: String)
        fun setupFab(fabIconRes: Int, function: () ->  Unit)
    }
}