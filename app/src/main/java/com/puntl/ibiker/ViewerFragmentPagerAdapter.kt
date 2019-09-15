package com.puntl.ibiker

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.puntl.ibiker.fragments.EditorInfoFragment
import com.puntl.ibiker.fragments.PhotosFragment
import com.puntl.ibiker.fragments.ViewerCommentsFragment
import com.puntl.ibiker.fragments.ViewerInfoFragment

class ViewerFragmentPagerAdapter(var context: Context, fm: FragmentManager?, var tabCount: Int) : FragmentPagerAdapter(fm) {
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> ViewerInfoFragment()
            1 -> PhotosFragment()
            2 -> ViewerCommentsFragment()
            else -> ViewerInfoFragment()
        }
    }

    override fun getCount(): Int {
        return tabCount
    }

}