   package com.miu.meditationapp.activities
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.*
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.miu.meditationapp.databinding.ActivityLoginAddUserBinding
import java.util.*
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import com.miu.meditationapp.helper.DropboxHelper
import com.miu.meditationapp.activities.LoginActivity
import com.miu.meditationapp.activities.MainActivity

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
    private var selectedPhotoUri: Uri? = null
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            Log.d("RegisterActivity", "Selected photo uri: $selectedPhotoUri")
            val inputStream = contentResolver.openInputStream(selectedPhotoUri!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.selectphotoImageviewRegister.setImageBitmap(bitmap)
            binding.selectphotoButtonRegister.visibility = View.GONE
        }
    }

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
                        if (selectedPhotoUri != null) {
                            val inputStream = contentResolver.openInputStream(selectedPhotoUri!!)
                            if (inputStream == null) {
                                Log.e("RegisterActivity", "inputStream is null!")
                                runOnUiThread {
                                    loadingPB.visibility = View.GONE
                                    Toast.makeText(this, "Ảnh không hợp lệ hoặc không đọc được!", Toast.LENGTH_SHORT).show()
                                }
                                return@addOnCompleteListener
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
                                    runOnUiThread {
                                        loadingPB.visibility = View.GONE
                                        Log.e("RegisterActivity", "Dropbox upload error", e)
                                        Toast.makeText(this, "Failed to upload avatar: ${e.message ?: e.toString()}", Toast.LENGTH_SHORT).show()
                                        saveUserToFirebaseDatabase("", fname, lname)
                                    }
                                }
                            )
                        } else {
                            saveUserToFirebaseDatabase("", fname, lname)
                        }
                    } else {
                        loadingPB.visibility = View.GONE
                        Toast.makeText(this, "Your internet connection is not stable. Try again...", Toast.LENGTH_SHORT).show()
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

    private fun saveUserToFirebaseDatabase(profileImageUrl: String, firstname: String, lastname: String) {
        val uid = FirebaseAuth.getInstance().uid ?: ""
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