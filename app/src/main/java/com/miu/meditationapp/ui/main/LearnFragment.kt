package com.miu.meditationapp.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.FragmentLearnBinding

class LearnFragment : Fragment(), MyAdapter.ItemClickListener {
    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LearnViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)

        viewModel = ViewModelProvider(this).get(LearnViewModel::class.java)

        binding.recyclerView1.layoutManager = LinearLayoutManager(context)
        val adapter = MyAdapter(viewModel.getData(), this)
        binding.recyclerView1.adapter = adapter

        return binding.root
    }

    override fun onItemClick (position: Int){
        val intent = Intent(context, VideosView::class.java)
        intent.putExtra("videos", viewModel.getData()[position])
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}