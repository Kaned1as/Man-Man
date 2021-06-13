package com.adonai.manman.misc

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import androidx.transition.Slide

/**
 * Overlays main view of the activity with the specified fragment.
 */
fun FragmentActivity.showFullscreenFragment(frag: Fragment) {
    frag.enterTransition = Slide(Gravity.END)
    frag.exitTransition = Slide(Gravity.START)

    supportFragmentManager.beginTransaction()
        .addToBackStack("Showing fragment: $frag")
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        //.add(R.id.ma, frag)
        .commit()
}

fun Context.resolveAttr(attr: Int): Int {
    val typedValue = TypedValue()
    this.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}