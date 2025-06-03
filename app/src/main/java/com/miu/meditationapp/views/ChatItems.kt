package com.miu.meditationapp.views

import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.ChatFromRowBinding
import com.miu.meditationapp.databinding.ChatToRowBinding
import com.miu.meditationapp.models.User
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item

class ChatFromItem(val text: String, val user: User) : Item<GroupieViewHolder>() {
    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        val binding = ChatFromRowBinding.bind(viewHolder.itemView)
        binding.textviewFromRow.text = text

        val uri = user.profileImageUrl
        Picasso.get().load(uri).into(binding.imageviewChatFromRow)
    }

    override fun getLayout(): Int {
        return R.layout.chat_from_row
    }
}

class ChatToItem(val text: String, val user: User) : Item<GroupieViewHolder>() {
    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        val binding = ChatToRowBinding.bind(viewHolder.itemView)
        binding.textviewToRow.text = text

        val uri = user.profileImageUrl
        Picasso.get().load(uri).into(binding.imageviewChatToRow)
    }

    override fun getLayout(): Int {
        return R.layout.chat_to_row
    }
}