package com.miu.meditationapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.*
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.miu.meditationapp.databinding.ActivityLoginAddUserBinding
import java.util.*

class LoginAddUser : AppCompatActivity() {
    companion object {
        val TAG = "RegisterActivity"
    }

    private lateinit var binding: ActivityLoginAddUserBinding
    lateinit var firstNameEdt: TextInputEditText
    lateinit var lastNameEdt: TextInputEditText
    lateinit var emailEdt: TextInputEditText
    lateinit var passwordEdt: TextInputEditText
    lateinit var cpasswordEdt: TextInputEditText
    lateinit var loginTV: TextView
    lateinit var createuserBtn: Button
    lateinit var loadingPB: ProgressBar
    lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginAddUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firstNameEdt = binding.ptxtFirstname
        lastNameEdt = binding.ptxtLastname
        emailEdt = binding.ptxtEmail
        passwordEdt = binding.ptxtpassword
        cpasswordEdt = binding.ptxtCpassword
        createuserBtn = binding.btnCreateuser
        loadingPB = binding.progressbar
        loginTV = binding.idTVLogin
        mAuth = FirebaseAuth.getInstance()
        loginTV.setOnClickListener() {
            var i = Intent(this, LoginActivity::class.java)
            startActivity(i)
        }
        createuserBtn.setOnClickListener() {
            val fname:String = firstNameEdt.text.toString()
            val lname:String = lastNameEdt.text.toString()
            val email:String = emailEdt.text.toString()
            val pwd:String = passwordEdt.text.toString()
            val cpwd:String = cpasswordEdt.text.toString()

            if(!pwd.equals(cpwd)) {
                Toast.makeText(this, "Please check both password", Toast.LENGTH_SHORT).show()
            } else if(TextUtils.isEmpty(fname) || TextUtils.isEmpty(lname) || TextUtils.isEmpty(email) || TextUtils.isEmpty(pwd) || TextUtils.isEmpty(cpwd)) {
                Toast.makeText(this, "Please add fully information..", Toast.LENGTH_SHORT).show()
            } else {
                loadingPB.visibility = View.VISIBLE
                mAuth.createUserWithEmailAndPassword(email,pwd).addOnCompleteListener(this) { task ->
                    if(task.isSuccessful) {
                        saveUserToFirebaseDatabase("")
                    } else {
                        loadingPB.visibility = View.GONE
                        Toast.makeText(this, "Your internet connection is not stable. Try again...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveUserToFirebaseDatabase(profileImageUrl: String) {
        val uid = FirebaseAuth.getInstance().uid ?: ""
        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")

        val user = com.miu.meditationapp.models.User(uid, binding.ptxtFirstname.text.toString(), profileImageUrl, false)

        ref.setValue(user)
            .addOnSuccessListener {
                Log.d(TAG, "Finally we saved the user to Firebase Database")
                loadingPB.visibility = View.GONE
                Toast.makeText(this, "User Created...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            .addOnFailureListener {
                Log.d(TAG, "Failed to set value to database: ${it.message}")
                loadingPB.visibility = View.GONE
                Toast.makeText(this, "Failed to save user details.", Toast.LENGTH_SHORT).show()
            }
    }

}