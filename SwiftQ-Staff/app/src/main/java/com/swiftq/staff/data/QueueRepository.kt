package com.swiftq.staff.data

import android.util.Log

class QueueRepository {

    companion object {
        private const val TAG = "SwiftQRepository"
    }

    fun loadQueue(): QueueLoadState {

        Log.d(
            TAG,
            "Queue data requested. Awaiting backend integration."
        )
        return QueueLoadState.Loading
    }
}