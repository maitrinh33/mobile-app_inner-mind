package com.miu.meditationapp.ui.main

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class AboutViewModel : ViewModel() {
    private lateinit var database: DatabaseReference
    private var photoURL: String = ""
    private var firstname: String = ""
    private var lastname: String = ""
    private var email: String = ""

    var liveFirstName = MutableLiveData<String>()
    var liveProfilePicture = MutableLiveData<String>()

    fun getProfilePictureField() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Log.e("AboutViewModel", "User not logged in")
            return
        }

        database = FirebaseDatabase.getInstance().getReference("users")
        database.child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val profileImageUrl = snapshot.child("profileImageUrl").value.toString()
                    if (profileImageUrl.isNotEmpty()) {
                        liveProfilePicture.value = profileImageUrl
                    }
                } else {
                    Log.d("AboutViewModel", "User data not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AboutViewModel", "Failed to load profile picture: ${e.message}")
            }
    }

    fun getFirstName(): String {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Log.e("AboutViewModel", "User not logged in")
            return ""
        }

        database = FirebaseDatabase.getInstance().getReference("users")
        database.child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstname").value.toString()
                    if (firstName.isNotEmpty()) {
                        firstname = firstName
                        liveFirstName.value = firstname
                    }
                } else {
                    Log.d("AboutViewModel", "User data not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AboutViewModel", "Failed to load first name: ${e.message}")
            }
        return firstname
    }

    fun getLastName() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Log.e("AboutViewModel", "User not logged in")
            return
        }

        database = FirebaseDatabase.getInstance().getReference("users")
        database.child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    lastname = snapshot.child("lastname").value.toString()
                } else {
                    Log.d("AboutViewModel", "User data not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AboutViewModel", "Failed to load last name: ${e.message}")
            }
    }

    fun getEmail() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Log.e("AboutViewModel", "User not logged in")
            return
        }

        database = FirebaseDatabase.getInstance().getReference("users")
        database.child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    email = snapshot.child("username").value.toString()
                } else {
                    Log.d("AboutViewModel", "User data not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AboutViewModel", "Failed to load email: ${e.message}")
            }
    }
}