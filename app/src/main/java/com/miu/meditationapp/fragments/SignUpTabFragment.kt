package com.miu.meditationapp.fragments

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.miu.meditationapp.activities.MainActivity
import com.miu.meditationapp.databinding.FragmentSignUpBinding
import com.miu.meditationapp.helper.DropboxHelper
import java.util.*

class SignUpTabFragment : Fragment() {

    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private var selectedPhotoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            val inputStream = requireActivity().contentResolver.openInputStream(selectedPhotoUri!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.selectphotoImageviewRegister.setImageBitmap(bitmap)
            binding.selectphotoButtonRegister.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mAuth = FirebaseAuth.getInstance()

        binding.btnCreateuser.setOnClickListener {
            val fname = binding.ptxtFirstname.text.toString()
            val lname = binding.ptxtLastname.text.toString()
            val email = binding.ptxtEmail.text.toString()
            val pwd = binding.ptxtpassword.text.toString()
            val cpwd = binding.ptxtCpassword.text.toString()

            if (pwd != cpwd) {
                Toast.makeText(requireContext(), "Passwords do not match.", Toast.LENGTH_SHORT).show()
            } else if (TextUtils.isEmpty(fname) || TextUtils.isEmpty(lname) || TextUtils.isEmpty(email) || TextUtils.isEmpty(pwd)) {
                Toast.makeText(requireContext(), "Please fill all fields.", Toast.LENGTH_SHORT).show()
            } else {
                binding.progressbar.visibility = View.VISIBLE
                mAuth.createUserWithEmailAndPassword(email, pwd).addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful) {
                        uploadAvatarAndSaveUser(fname, lname)
                    } else {
                        binding.progressbar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.selectphotoButtonRegister.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.selectphotoImageviewRegister.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun uploadAvatarAndSaveUser(fname: String, lname: String) {
        if (selectedPhotoUri != null) {
            val inputStream = requireActivity().contentResolver.openInputStream(selectedPhotoUri!!)
            if (inputStream == null) {
                binding.progressbar.visibility = View.GONE
                Toast.makeText(requireContext(), "Invalid image.", Toast.LENGTH_SHORT).show()
                return
            }
            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            DropboxHelper.uploadFile(
                inputStream,
                "/avatars/$fileName",
                onSuccess = {
                    val profileImageUrl = DropboxHelper.getSharedLink("/avatars/$fileName")
                    saveUserToFirebaseDatabase(profileImageUrl, fname, lname)
                },
                onError = { e ->
                    requireActivity().runOnUiThread {
                        binding.progressbar.visibility = View.GONE
                        Log.e("SignUpTabFragment", "Dropbox upload error", e)
                        Toast.makeText(requireContext(), "Failed to upload avatar: ${e.message}", Toast.LENGTH_SHORT).show()
                        saveUserToFirebaseDatabase("", fname, lname) // Save without avatar
                    }
                }
            )
        } else {
            saveUserToFirebaseDatabase("", fname, lname)
        }
    }

    private fun saveUserToFirebaseDatabase(profileImageUrl: String, firstname: String, lastname: String) {
        val uid = mAuth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
        val user = mapOf(
            "uid" to uid,
            "username" to firstname,
            "firstname" to firstname,
            "lastname" to lastname,
            "profileImageUrl" to profileImageUrl,
            "isAdmin" to false
        )
        ref.setValue(user)
            .addOnSuccessListener {
                binding.progressbar.visibility = View.GONE
                Toast.makeText(requireContext(), "User created.", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .addOnFailureListener {
                binding.progressbar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to save user details.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 