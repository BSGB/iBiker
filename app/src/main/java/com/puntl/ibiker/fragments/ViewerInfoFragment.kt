package com.puntl.ibiker.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.puntl.ibiker.R
import kotlinx.android.synthetic.main.fragment_viewer_info.*

class ViewerInfoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_viewer_info, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bikeTypeSpinner.isEnabled = false
        super.onActivityCreated(savedInstanceState)
    }
}
