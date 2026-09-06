package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.util.AppLogger

/**
 * Helper utility to manage Android Doze mode, App Standby,
 * and Battery Optimization exemption policies for seamless background AI tasks.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimizationHelper"

    /**
     * Checks if the application is currently exempted from Android battery optimizations (Doze mode).
     * Returns true if battery optimization is ignored (unrestricted) or on API < 23.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking battery optimization status: ${e.message}", e)
            false
        }
    }

    /**
     * Requests battery optimization exemption for the app.
     * Tries direct dialog via ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS first.
     * Falls back to general battery optimization settings if disallowed or unavailable.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Direct request failed (${e.message}), falling back to settings screen")
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * Opens system battery optimization settings screen.
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to open battery optimization settings: ${e.message}", e)
            try {
                val appDetailIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(appDetailIntent)
                true
            } catch (ex: Exception) {
                AppLogger.e(TAG, "Failed to open app detail settings: ${ex.message}", ex)
                false
            }
        }
    }

    /**
     * Returns human-friendly status text and color tokens for the battery optimization state.
     */
    fun getStatusDescription(isIgnored: Boolean): Pair<String, String> {
        return if (isIgnored) {
            "절전 예외 허용됨" to "화면이 꺼지거나 다른 앱을 사용하는 동안에도 긴 대화 요약이 중단 없이 안전하게 완료돼요."
        } else {
            "절전 최적화 켜짐" to "화면이 꺼지면 안드로이드 Doze 정책에 의해 AI 요약 속도가 느려지거나 일시 정지될 수 있어요."
        }
    }
}
