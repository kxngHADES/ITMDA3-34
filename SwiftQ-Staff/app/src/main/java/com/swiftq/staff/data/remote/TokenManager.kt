package com.swiftq.staff.data.remote

import android.content.Context

class TokenManager(context: Context) {

    private val preferences = context.getSharedPreferences(
        "swiftq_session",
        Context.MODE_PRIVATE
    )

    fun saveToken(token: String) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
    }

    fun getToken(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun clearToken() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .apply()
    }

    fun saveStaffProfile(fullName: String, clinicId: String?) {
        preferences.edit()
            .putString(KEY_STAFF_NAME, fullName)
            .putString(KEY_CLINIC_ID, clinicId)
            .apply()
    }

    fun getStaffName(): String? {
        return preferences.getString(KEY_STAFF_NAME, null)
    }

    fun saveClinicId(clinicId: String) {
        preferences.edit()
            .putString(KEY_CLINIC_ID, clinicId)
            .apply()
    }

    fun getClinicId(): String? {
        return preferences.getString(KEY_CLINIC_ID, null)
    }

    // Removes the token and everything tied to the signed-in staff member.
    fun clearSession() {
        preferences.edit()
            .clear()
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_STAFF_NAME = "staff_name"
        private const val KEY_CLINIC_ID = "clinic_id"
    }
}
