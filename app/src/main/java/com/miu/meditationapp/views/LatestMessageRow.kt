package com.miu.meditationapp.views

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.LatestMessageRowBinding
import com.miu.meditationapp.models.ChatMessage
import com.miu.meditationapp.models.User
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item

class LatestMessageRow(val chatMessage: ChatMessage) : Item<GroupieViewHolder>() {
    var chatPartnerUser: User? = null

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        val binding = LatestMessageRowBinding.bind(viewHolder.itemView)
        binding.messageTextviewLatestMessage.text = chatMessage.text

        val chatPartnerId: String
        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
            chatPartnerId = chatMessage.toId
        } else {
            chatPartnerId = chatMessage.fromId
        }

        val ref = FirebaseDatabase.getInstance().getReference("/users/$chatPartnerId")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {
                chatPartnerUser = p0.getValue(User::class.java)
                binding.usernameTextviewLatestMessage.text = chatPartnerUser?.username

                Picasso.get().load(chatPartnerUser?.profileImageUrl).into(binding.imageviewLatestMessage)
            }

            override fun onCancelled(p0: DatabaseError) {
                // Handle error if needed
            }
        })
    }

    override fun getLayout(): Int {
        return R.layout.latest_message_row
    }
}