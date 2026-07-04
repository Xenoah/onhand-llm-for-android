package com.onhand.llm.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.onhand.llm.MainActivity
import com.onhand.llm.OnHandApp
import com.onhand.llm.R
import kotlinx.coroutines.runBlocking

/**
 * API サーバーを動かし続けるためのフォアグラウンドサービス。
 * 実際のサーバー本体は [ServerController] がプロセス内シングルトンとして保持する。
 */
class ServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = OnHandApp.from(this)
        val port = runBlocking { container.settings.current().serverPort }

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(port))

        val result = container.server.start(port)
        if (result.isFailure) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        OnHandApp.from(this).server.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "API サーバー",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(port: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("ローカル API サーバー実行中 (ポート $port)")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "api_server"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ServerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }
    }
}
