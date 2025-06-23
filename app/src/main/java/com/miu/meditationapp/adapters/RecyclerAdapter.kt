package com.miu.meditationapp.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.miu.meditationapp.R
import com.miu.meditationapp.models.PostHistory
import com.squareup.picasso.Picasso

private lateinit var database: DatabaseReference

class RecyclerAdapter(var context: Context, items: List<PostHistory>) :
    RecyclerView.Adapter<RecyclerAdapter.ViewHolder?>() {

    lateinit var items: List<PostHistory>
    private val mLayoutInflater: LayoutInflater
    private val currentUserId = FirebaseAuth.getInstance().uid

    init {
        this.items = items
        mLayoutInflater = LayoutInflater.from(context)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var username: TextView
        var postbody: TextView
        var posteddate: TextView
        var image: ImageView
        var rootLayout: ConstraintLayout

        init {
            username = itemView.findViewById(R.id.name)
            postbody = itemView.findViewById(R.id.chatContent)
            posteddate = itemView.findViewById(R.id.date)
            image = itemView.findViewById(R.id.imageview_chat)
            rootLayout = itemView.findViewById(R.id.root_layout)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (currentUserId == items[position].uid) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == 0) R.layout.post_history_items_sent else R.layout.post_history_items_received
        val view: View = mLayoutInflater.inflate(layout, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ResourceAsColor")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val message = items[position]
            holder.username.text = message.uid
            holder.postbody.text = message.postbody
            holder.posteddate.text = message.posteddate

            // Set background and text color based on message type
            if (getItemViewType(position) == 0) {
                // Sent message
                holder.postbody.setBackgroundResource(R.drawable.chat_bubble_sent)
                holder.postbody.setTextColor(android.graphics.Color.WHITE)

                // Align sent messages to the right
                try {
                    val params = holder.rootLayout.layoutParams as? ConstraintLayout.LayoutParams
                    if (params != null) {
                        params.startToStart = ConstraintLayout.LayoutParams.UNSET
                        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        holder.rootLayout.layoutParams = params
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting layout params for sent message: ${e.message}")
                }
            } else {
                // Received message
                holder.postbody.setBackgroundResource(R.drawable.chat_bubble_received)
                holder.postbody.setTextColor(android.graphics.Color.BLACK)

                // Align received messages to the left
                try {
                    val params = holder.rootLayout.layoutParams as? ConstraintLayout.LayoutParams
                    if (params != null) {
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        params.endToEnd = ConstraintLayout.LayoutParams.UNSET
                        holder.rootLayout.layoutParams = params
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting layout params for received message: ${e.message}")
                }
            }

            // Load user profile image
            if (message.uid != null) {
                database = FirebaseDatabase.getInstance().getReference("users")
                database.child(message.uid).get().addOnSuccessListener {
                    if (it.exists()) {
                        val imageUrl = it.child("profileImageUrl").value.toString()
                        if (imageUrl.isNotEmpty()) {
                            Picasso.get()
                                .load(imageUrl)
                                .error(R.drawable.onboarding_community)
                                .into(holder.image)
                        } else {
                            holder.image.setImageResource(R.drawable.onboarding_community)
                        }
                    } else {
                        holder.image.setImageResource(R.drawable.onboarding_community)
                    }
                }.addOnFailureListener {
                    holder.image.setImageResource(R.drawable.onboarding_community)
                }
            } else {
                holder.image.setImageResource(R.drawable.onboarding_community)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onBindViewHolder: ${e.message}")
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    companion object {
        private const val TAG = "RecyclerAdapter"
    }
} 