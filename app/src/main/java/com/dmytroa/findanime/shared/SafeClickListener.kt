package com.dmytroa.findanime.shared

import android.os.SystemClock
import android.view.View

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