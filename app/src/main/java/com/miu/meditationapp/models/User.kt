package com.miu.meditationapp.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class User(val uid: String, val username: String, val profileImageUrl: String, val isAdmin: Boolean): Parcelable {
  constructor() : this("", "", "", false)
}