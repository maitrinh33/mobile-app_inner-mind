package com.miu.meditationapp.ui.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.miu.meditationapp.LoginActivity
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.FragmentAboutBinding
import java.net.URL
import java.util.*
import android.widget.Toast
import com.google.firebase.database.DatabaseError
import com.squareup.picasso.Picasso
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener
import android.util.Log

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AboutViewModel
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this).get(AboutViewModel::class.java)

        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(context, "Please sign in to view profile", Toast.LENGTH_SHORT).show()
            return binding.root
        }

        // Set default values
        binding.name.text = "Hi there!"
        binding.avatar.setImageResource(R.drawable.img_communi)

        // Load user data
        viewModel.getFirstName()
        viewModel.liveFirstName.observe(viewLifecycleOwner) { firstName ->
            if (firstName.isNotEmpty()) {
                binding.name.text = "Hi $firstName!"
            }
        }

        viewModel.getProfilePictureField()
        viewModel.liveProfilePicture.observe(viewLifecycleOwner) { profileUrl ->
            if (profileUrl.isNotEmpty()) {
                try {
                    Picasso.get()
                        .load(profileUrl)
                        .error(R.drawable.img_communi)
                        .into(binding.avatar)
                } catch (e: Exception) {
                    Log.e("AboutFragment", "Error loading profile picture: ${e.message}")
                    binding.avatar.setImageResource(R.drawable.img_communi)
                }
            }
        }

        val calendar = Calendar.getInstance()
        binding.cal.date = calendar.timeInMillis

        binding.logout.setOnClickListener {
            try {
                auth.signOut()
                val preferences = context?.getSharedPreferences("ONBOARD", Context.MODE_PRIVATE)
                preferences?.edit()?.remove("ISCOMPLETE")?.apply()
                
                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                activity?.finish()
            } catch (e: Exception) {
                Log.e("AboutFragment", "Error during logout: ${e.message}")
                Toast.makeText(context, "Error during logout: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
