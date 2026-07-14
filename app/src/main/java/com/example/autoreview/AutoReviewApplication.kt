package com.example.autoreview

import android.app.Application
import com.aptabase.Aptabase

private const val APTABASE_KEY = "A-EU-2667676621"

class AutoReviewApplication : Application() {

   override fun onCreate() {
      super.onCreate()
      // Initialize Aptabase object
      Aptabase.instance.initialize(applicationContext, APTABASE_KEY)
      // Track app launch on startup
      Aptabase.instance.trackEvent("app_started")
   }

}
