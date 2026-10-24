package com.example.data.preference

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class UserProfileManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)

    fun getUserDisplayName(): String {
        val saved = prefs.getString("display_name", null)
        if (!saved.isNullOrBlank()) return saved

        val firebaseUser = Firebase.auth.currentUser
        return firebaseUser?.displayName
            ?: firebaseUser?.email?.substringBefore("@")
            ?: "المستخدم"
    }

    fun setUserDisplayName(name: String) {
        prefs.edit().putString("display_name", name.trim()).apply()
    }

    fun getUserPhotoUri(): String? {
        val saved = prefs.getString("photo_uri", null)
        if (!saved.isNullOrBlank()) return saved

        return Firebase.auth.currentUser?.photoUrl?.toString()
    }

    fun setUserPhotoUri(uriString: String?) {
        prefs.edit().putString("photo_uri", uriString).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

