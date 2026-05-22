package com.example.liveshoter.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.net.Uri
import android.util.Log

object ProjectionHolder {
    private const val PREFS_NAME = "projection_prefs"
    private const val KEY_RESULT_CODE = "result_code"
    private const val KEY_RESULT_DATA = "result_data"

    var mediaProjection: MediaProjection? = null
    private var savedResultCode: Int? = null
    private var savedResultData: Intent? = null

    fun hasProjection(): Boolean = mediaProjection != null

    fun hasSavedPermission(): Boolean {
        return savedResultCode != null && savedResultData != null
    }

    fun savePermissionData(context: Context, resultCode: Int, data: Intent) {
        savedResultCode = resultCode
        savedResultData = data

        val dataUri = data.toUri(Intent.URI_INTENT_SCHEME)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RESULT_CODE, resultCode)
            .putString(KEY_RESULT_DATA, dataUri)
            .apply()
        Log.d("ProjectionHolder", "Saved permission: code=$resultCode")
    }

    fun loadPermissionData(context: Context) {
        if (savedResultCode != null && savedResultData != null) {
            Log.d("ProjectionHolder", "Already loaded in memory")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getInt(KEY_RESULT_CODE, -1)
        val dataUri = prefs.getString(KEY_RESULT_DATA, null)

        if (code != -1 && dataUri != null) {
            try {
                savedResultCode = code
                savedResultData = Intent.parseUri(dataUri, Intent.URI_INTENT_SCHEME)
                Log.d("ProjectionHolder", "Loaded from prefs: code=$code")
            } catch (e: Exception) {
                Log.e("ProjectionHolder", "Failed to parse saved intent", e)
                clear()
            }
        } else {
            Log.d("ProjectionHolder", "No saved data found in prefs")
        }
    }

    fun getSavedPermissionData(): Pair<Int, Intent>? {
        return if (savedResultCode != null && savedResultData != null) {
            Pair(savedResultCode!!, savedResultData!!)
        } else null
    }

    fun clear() {
        Log.d("ProjectionHolder", "clear() called")
        mediaProjection?.stop()
        mediaProjection = null
    }

    fun clearPreferences(context: Context) {
        Log.d("ProjectionHolder", "clearPreferences() called")
        mediaProjection?.stop()
        mediaProjection = null
        savedResultCode = null
        savedResultData = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}