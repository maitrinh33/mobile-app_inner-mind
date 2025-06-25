package com.miu.meditationapp.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.miu.meditationapp.R
import com.miu.meditationapp.activities.LoginActivity
import com.miu.meditationapp.databinding.FragmentAboutBinding
import com.miu.meditationapp.viewmodels.AboutViewModel
import java.util.*
import android.widget.Toast
import com.squareup.picasso.Picasso
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
        binding.avatar.setImageResource(R.drawable.onboarding_community)

        // Load user data
        viewModel.getFirstName()
        viewModel.getProfilePictureField()

        // Observe user data changes
        viewModel.liveFirstName.observe(viewLifecycleOwner) { firstName ->
            if (firstName.isNotEmpty()) {
                binding.name.text = "Hi $firstName!"
            }
        }

        viewModel.liveProfilePicture.observe(viewLifecycleOwner) { profileUrl ->
            if (!profileUrl.isNullOrEmpty()) {
                Picasso.get()
                    .load(profileUrl)
                    .error(R.drawable.onboarding_community)
                    .into(binding.avatar)
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
