package com.example.autoreview.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private const val MAX_LOG_SIZE = 1 * 1024 * 1024 // 1 MB

    fun init(context: Context) {
        if (logFile == null) {
            logFile = File(context.filesDir, "debug_logs.txt")
        }
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        appendLog("DEBUG", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        val fullMsg = if (t != null) "$msg\n${Log.getStackTraceString(t)}" else msg
        appendLog("ERROR", tag, fullMsg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        appendLog("WARN", tag, msg)
    }

    @Synchronized
    private fun appendLog(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        
        try {
            // Roll over if too large
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                val oldFile = File(file.parent, "debug_logs_old.txt")
                if (oldFile.exists()) oldFile.delete()
                file.renameTo(oldFile)
            }
            
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp [$level] $tag: $msg\n"
            file.appendText(logLine)
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to write log", e)
        }
    }

    fun getLogs(context: Context): String {
        val file = logFile ?: File(context.filesDir, "debug_logs.txt")
        return try {
            if (file.exists()) file.readText() else "No logs found."
        } catch (e: Exception) {
            "Failed to read logs: ${e.message}"
        }
    }

    fun getLogFile(context: Context): File {
        return logFile ?: File(context.filesDir, "debug_logs.txt")
    }

    fun clearLogs(context: Context) {
        val file = logFile ?: File(context.filesDir, "debug_logs.txt")
        if (file.exists()) {
            file.delete()
        }
        val oldFile = File(context.filesDir, "debug_logs_old.txt")
        if (oldFile.exists()) {
            oldFile.delete()
        }
    }
}
