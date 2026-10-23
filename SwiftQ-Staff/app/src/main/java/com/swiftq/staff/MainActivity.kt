package com.swiftq.staff

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.swiftq.staff.data.remote.StaffSessionManager
import com.swiftq.staff.ui.applySystemBarInsets

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.splashContainer)
            .applySystemBarInsets(top = true, bottom = true)

        val splashContent =
            findViewById<View>(R.id.splashContent)

        val movement = resources.displayMetrics.density * 24f

        splashContent.translationY = movement
        splashContent.alpha = 0f

        splashContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400L)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({

            // Staff who are still signed in go straight to their queue.
            val destination =
                if (StaffSessionManager(applicationContext).hasSession()) {
                    QueueDashboardActivity::class.java
                } else {
                    LoginActivity::class.java
                }

            startActivity(
                Intent(this, destination)
            )

            finish()

        }, 1200L)
    }
}
