package com.legion_11.findanime.shared

import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.fragment.app.FragmentActivity

object Utils {

    private fun getStatusBarHeight(activity: FragmentActivity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            activity.resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    private fun getToolbarBarHeight(activity: FragmentActivity): Int {
        val tv = TypedValue()
        if (activity.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data, activity.resources.displayMetrics)
        }
        return 0
    }

    private fun getDisplayMetrics(activity: FragmentActivity): DisplayMetrics {
        val outMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = activity.display
            display?.getRealMetrics(outMetrics)
        } else {
            @Suppress("DEPRECATION")
            val display = activity.windowManager?.defaultDisplay
            @Suppress("DEPRECATION")
            display?.getMetrics(outMetrics)
        }
        return outMetrics
    }


    fun getVisibleHeight(activity: FragmentActivity): Int =
        getDisplayMetrics(activity).heightPixels - getStatusBarHeight(activity) - getToolbarBarHeight(activity)

}