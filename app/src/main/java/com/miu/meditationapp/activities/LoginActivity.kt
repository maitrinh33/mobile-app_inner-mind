package com.miu.meditationapp.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.ActivityLoginBinding
import com.miu.meditationapp.models.User
import com.miu.meditationapp.activities.LoginAddUser

class LoginActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "login"
        private const val RC_SIGN_IN = 9001
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        preferences = getSharedPreferences("ONBOARD", Context.MODE_PRIVATE)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.signInButton.setOnClickListener {
            signIn()
        }

        binding.registerTV.setOnClickListener {
            startActivity(Intent(this, LoginAddUser::class.java))
        }

        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(this, LoginForgotPassword::class.java))
        }

        // Email & Password Login
        binding.btnLogin.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val pwd = binding.inputPassword.text.toString().trim()

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pwd)) {
                Toast.makeText(this, "Please enter your credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressbar.visibility = View.VISIBLE
            mAuth.signInWithEmailAndPassword(email, pwd).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    checkUserRoleAndProceed()
                } else {
                    binding.progressbar.visibility = View.GONE
                    Toast.makeText(this, "Email or Password is wrong. Please try again.", Toast.LENGTH_SHORT).show()
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

        val database = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
        database.child(user.uid).get().addOnSuccessListener { snapshot ->
            binding.progressbar.visibility = View.GONE
            if (snapshot.exists() && snapshot.child("isAdmin").value == true) {
                Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Login Successful..", Toast.LENGTH_SHORT).show()
            }
            startMainActivity()
        }.addOnFailureListener {
            binding.progressbar.visibility = View.GONE
            Toast.makeText(this, "Login successful, but failed to fetch user data.", Toast.LENGTH_SHORT).show()
            startMainActivity() // Proceed anyway
        }
    }

    private fun signIn() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        binding.progressbar.visibility = View.VISIBLE
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = mAuth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser && user != null) {
                        saveGoogleUserToDb(user) // Save new user to DB
                    } else {
                        binding.progressbar.visibility = View.GONE
                        Toast.makeText(this, "Google Sign-In Successful!", Toast.LENGTH_SHORT).show()
                        startMainActivity()
                    }
                } else {
                    binding.progressbar.visibility = View.GONE
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Google Authentication failed.", Toast.LENGTH_SHORT).show()
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
                Log.d(TAG, "Successfully saved new Google user to Firebase DB")
                Toast.makeText(this, "Welcome!", Toast.LENGTH_SHORT).show()
                startMainActivity()
            }
            .addOnFailureListener {
                binding.progressbar.visibility = View.GONE
                Log.e(TAG, "Failed to save new Google user to DB", it)
                startMainActivity() // Proceed even if saving fails
            }
    }

    private fun startMainActivity() {
        if (isSeenOnboard()) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        finish()
    }

    override fun onStart() {
        super.onStart()
        if (mAuth.currentUser != null) {
            startMainActivity()
        }
    }

    private fun isSeenOnboard(): Boolean {
        return preferences.getBoolean("ISCOMPLETE", false)
    }
} 