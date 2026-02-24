package com.notioff.telegramsuper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class DownloadNotificationManager(private val context: Context) {
    private val channelId = "download_channel"
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Notifications for file downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    fun showDownloadProgress(fileId: Int, progressPercentage: Int, title: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setContentText("$progressPercentage%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercentage, false)
            .setOngoing(true)
            
        try {
            notificationManager.notify(fileId, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    fun downloadComplete(fileId: Int, title: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete: $title")
            .setContentText("File ready")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setOngoing(false)
            
        try {
            notificationManager.notify(fileId, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    fun cancelNotification(fileId: Int) {
        notificationManager.cancel(fileId)
    }
}
