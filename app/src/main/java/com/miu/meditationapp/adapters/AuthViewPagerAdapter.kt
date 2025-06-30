package com.miu.meditationapp.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.miu.meditationapp.fragments.LoginTabFragment
import com.miu.meditationapp.fragments.SignUpTabFragment

class AuthViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LoginTabFragment()
            1 -> SignUpTabFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
} 