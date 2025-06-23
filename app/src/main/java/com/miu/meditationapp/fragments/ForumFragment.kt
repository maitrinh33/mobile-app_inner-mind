package com.miu.meditationapp.fragments

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.miu.meditationapp.activities.LoginAddUser
import com.miu.meditationapp.adapters.RecyclerAdapter
import com.miu.meditationapp.models.PostHistory
import com.miu.meditationapp.databinding.FragmentForumBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.widget.Toast

class ForumFragment : Fragment() {
    private var _binding: FragmentForumBinding? = null
    private val binding get() = _binding!!
    private var adapter: RecyclerAdapter? = null
    var items: MutableList<PostHistory> = ArrayList()
    private lateinit var auth: FirebaseAuth

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentForumBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(context, "Please sign in to access the forum", Toast.LENGTH_SHORT).show()
            return binding.root
        }

        // Set up RecyclerView with proper layout for chronological order
        binding.recyclerV.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        items = arrayListOf()

        val ref = FirebaseDatabase.getInstance().getReference("posts").orderByChild("posteddate")

        ref.keepSynced(true)
        val postListener: ValueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()
                for (courtSnapshot in dataSnapshot.children) {
                    val uid = courtSnapshot.child("uid").value as String?
                    val posteddate = courtSnapshot.child("posteddate").value as String?
                    val postbody = courtSnapshot.child("postbody").value as String?

                    if (uid != null && posteddate != null && postbody != null) {
                        val newPost = PostHistory(uid, posteddate, postbody)
                        items.add(newPost)
                    }
                }

                // Sort items by date to ensure chronological order
                items.sortBy { it.posteddate }

                adapter = activity?.let { RecyclerAdapter(it.applicationContext, items) }
                binding.recyclerV?.adapter = adapter
                adapter?.notifyDataSetChanged()

                // Scroll to the latest message
                if (items.isNotEmpty()) {
                    binding.recyclerV?.post {
                        binding.recyclerV?.smoothScrollToPosition(items.size - 1)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ForumFragment", "Database error: ${error.message}")
                context?.let {
                    Toast.makeText(it, "Error loading messages: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ref.addValueEventListener(postListener)

        binding.sendButton.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(context, "Please sign in to send messages", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.edittextChat.text.isNotEmpty()) {
                saveUserToFirebaseDatabase()
            }
        }
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveUserToFirebaseDatabase() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "Please sign in to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        val postid = UUID.randomUUID()
        val ref = FirebaseDatabase.getInstance().getReference("/posts/$postid")
        val uid = currentUser.uid
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss")
        var currdate = sdf.format(Date())
        val post = PostHistory(uid, currdate, binding.edittextChat.text.toString())

        ref.setValue(post)
            .addOnSuccessListener {
                Log.d(LoginAddUser.TAG, "Successfully saved the post to Firebase Database")
                binding.edittextChat.text.clear()
                // Scroll to the latest message after sending
                binding.recyclerV?.post {
                    if (items.isNotEmpty()) {
                        binding.recyclerV?.smoothScrollToPosition(items.size - 1)
                    }
                }
            }
            .addOnFailureListener {
                Log.d(LoginAddUser.TAG, "Failed to set value to database: ${it.message}")
                Toast.makeText(context, "Failed to send message: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 