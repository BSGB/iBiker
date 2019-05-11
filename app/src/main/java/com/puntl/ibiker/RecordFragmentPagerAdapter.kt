package com.puntl.ibiker

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.puntl.ibiker.fragments.InfoFragment
import com.puntl.ibiker.fragments.PhotosFragment


class RecordFragmentPagerAdapter(var context: Context, fm: FragmentManager?, var tabCount: Int) : FragmentPagerAdapter(fm) {
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> InfoFragment()
            1 -> PhotosFragment()
            else -> InfoFragment()
        }
    }

    override fun getCount(): Int {
        return tabCount
    }
}