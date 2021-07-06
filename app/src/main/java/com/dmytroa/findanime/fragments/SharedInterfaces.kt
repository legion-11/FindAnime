package com.dmytroa.findanime.fragments

object SharedInterfaces {
    interface OnCreateToolbar {
        fun prepareToolbar(resId: Int, searchViewIsVisible: Boolean)
        fun openDrawer()
    }

    interface FragmentListener {
        var extraFabsIsExpanded: Boolean

        fun hideMainFab()
        fun showMainFab()
        fun hideExtraFabs()
        fun showExtraFabs()
        fun hideShowExtraFabsFunction() {
            extraFabsIsExpanded = if (extraFabsIsExpanded) {
                hideExtraFabs()
                false
            } else {
                showExtraFabs()
                true
            }
        }
        fun showSnackBar()
        fun setupFab(fabIconRes: Int, function: () ->  Unit)
    }
}