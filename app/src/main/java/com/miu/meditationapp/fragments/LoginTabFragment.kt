package com.miu.meditationapp.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.miu.meditationapp.R
import com.miu.meditationapp.activities.LoginForgotPassword
import com.miu.meditationapp.activities.MainActivity
import com.miu.meditationapp.activities.OnboardingActivity
import com.miu.meditationapp.databinding.FragmentLoginBinding
import com.miu.meditationapp.models.User

class LoginTabFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var preferences: SharedPreferences

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("LoginTabFragment", "Google sign in failed", e)
                Toast.makeText(requireContext(), "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()
        preferences = requireActivity().getSharedPreferences("ONBOARD", Context.MODE_PRIVATE)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.signInButton.setOnClickListener {
            val signInIntent = mGoogleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(requireContext(), LoginForgotPassword::class.java))
        }

        // Email & Password Login
        binding.btnLogin.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val pwd = binding.inputPassword.text.toString().trim()

            if (TextUtils.isEmpty(email)) {
                binding.inputEmail.error = "Email is required"
                return@setOnClickListener
            }

            if (TextUtils.isEmpty(pwd)) {
                binding.inputPassword.error = "Password is required"
                return@setOnClickListener
            }

            if (pwd.length < 6) {
                binding.inputPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            binding.progressbar.visibility = View.VISIBLE
            mAuth.signInWithEmailAndPassword(email, pwd).addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    checkUserRoleAndProceed()
                } else {
                    binding.progressbar.visibility = View.GONE
                    val errorMessage = when (task.exception?.message) {
                        "There is no user record corresponding to this identifier. The user may have been deleted." ->
                            "No account found with this email. Please register first."
                        "The password is invalid or the user does not have a password." ->
                            "Incorrect password. Please try again or use 'Forgot Password'"
                        "A network error (such as timeout, interrupted connection or unreachable host) has occurred." ->
                            "Network error. Please check your internet connection."
                        else -> task.exception?.message ?: "Login failed. Please try again."
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    Log.e("LoginTabFragment", "Login failed", task.exception)
                }
            }
        }
    }

    private fun checkUserRoleAndProceed() {
        val user = mAuth.currentUser
        if (user == null) {
            binding.progressbar.visibility = View.GONE
            return
        }

        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
        val userRef = database.getReference("users").child(user.uid)
        userRef.keepSynced(true)
        
        userRef.get().addOnSuccessListener { snapshot ->
            if (isAdded) {
                binding.progressbar.visibility = View.GONE
                if (snapshot.exists() && snapshot.child("isAdmin").value == true) {
                    Toast.makeText(requireContext(), "Welcome Admin!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Login Successful..", Toast.LENGTH_SHORT).show()
                }
                startMainActivity()
            }
        }.addOnFailureListener {
            if (isAdded) {
                binding.progressbar.visibility = View.GONE
                Toast.makeText(requireContext(), "Login successful, but failed to fetch user data.", Toast.LENGTH_SHORT).show()
                startMainActivity()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        binding.progressbar.visibility = View.VISIBLE
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = mAuth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser && user != null) {
                        saveGoogleUserToDb(user)
                    } else {
                        binding.progressbar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Google Sign-In Successful!", Toast.LENGTH_SHORT).show()
                        startMainActivity()
                    }
                } else {
                    binding.progressbar.visibility = View.GONE
                    Log.w("LoginTabFragment", "signInWithCredential:failure", task.exception)
                    Toast.makeText(requireContext(), "Google Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveGoogleUserToDb(firebaseUser: FirebaseUser) {
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("/users/${firebaseUser.uid}")
        val username = firebaseUser.displayName ?: firebaseUser.email?.split('@')?.get(0) ?: "User"
        val profileImageUrl = firebaseUser.photoUrl?.toString() ?: ""
        
        val userToSave = User(firebaseUser.uid, username, profileImageUrl, false)

        ref.setValue(userToSave)
            .addOnSuccessListener {
                binding.progressbar.visibility = View.GONE
                Log.d("LoginTabFragment", "Successfully saved new Google user to Firebase DB")
                Toast.makeText(requireContext(), "Welcome!", Toast.LENGTH_SHORT).show()
                startMainActivity()
            }
            .addOnFailureListener {
                binding.progressbar.visibility = View.GONE
                Log.e("LoginTabFragment", "Failed to save new Google user to DB", it)
                startMainActivity()
            }
    }

    private fun startMainActivity() {
        val intent = if (isSeenOnboard()) {
            Intent(requireContext(), MainActivity::class.java)
        } else {
            Intent(requireContext(), OnboardingActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun isSeenOnboard(): Boolean {
        return preferences.getBoolean("ISCOMPLETE", false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 