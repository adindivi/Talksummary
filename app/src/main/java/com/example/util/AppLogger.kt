package com.example.util

import android.util.Log

/**
 * Unified application logger providing structured tags and safe formatting.
 * Safely falls back to standard output in JVM unit test environments.
 */
object AppLogger {
    private const val DEFAULT_TAG = "TalkSummary"

    fun d(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: RuntimeException) {
            println("DEBUG: [$tag] $message")
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.i(tag, message)
        } catch (_: RuntimeException) {
            println("INFO: [$tag] $message")
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        } catch (_: RuntimeException) {
            println("WARN: [$tag] $message ${throwable?.message ?: ""}")
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } catch (_: RuntimeException) {
            println("ERROR: [$tag] $message ${throwable?.message ?: ""}")
        }
    }
}
