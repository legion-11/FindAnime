package com.legion_11.findanime.shared

import android.os.SystemClock
import android.view.View

/**
 * prevent buttons to be clicked two times within second
 */
class SafeClickListener(private val onSafeCLick: (View) -> Unit): View.OnClickListener {
    private var defaultInterval: Int = 1000
    private var lastTimeClicked: Long = 0
    override fun onClick(v: View) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        onSafeCLick(v)
    }
}