package ni.devotion.floaty_head.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ni.devotion.floaty_head.FloatyHeadPlugin.Companion.context
import ni.devotion.floaty_head.MainActivity
import ni.devotion.floaty_head.R
import ni.devotion.floaty_head.utils.Managment

class FloatyIconService: Service() {
    companion object {
        lateinit var instance: FloatyIconService
        var notificationManager: NotificationManager? = null
        var notification: Notification? = null
    }
    val channel_id = "2208"
    val floaty_notification_id = 2208

    override fun onCreate() {
        instance = this
        super.onCreate()
    }

    @SuppressLint("NewApi")
    private fun initNotificationManager() {
        notificationManager ?: run {
             context?.let {
                notificationManager = it.getSystemService(NotificationManager::class.java)
            } ?: run {
                Log.e("TAG", "Context is null. Can't show the FloatyNotification")
                return
            }
        }
    }

    fun createNotificationChannel() {
        initNotificationManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                    channel_id,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager?.createNotificationChannel(serviceChannel)
        }
    }

    fun showNotificationManager() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0)
        notification = if(Managment.notificationIcon == null) {
            NotificationCompat.Builder(this, "ForegroundServiceChannel")
                    .setContentTitle("${Managment.notificationTitle} is Currently Running")
                    .setSmallIcon(R.drawable.ic_chathead)
                    .setContentIntent(pendingIntent)
                    .build()
        }else{
            NotificationCompat.Builder(this, "ForegroundServiceChannel")
                    .setContentTitle("${Managment.notificationTitle} is Currently Running")
                    .setLargeIcon(Managment.notificationIcon)
                    .setContentIntent(pendingIntent)
                    .build()
        }
        startForeground(floaty_notification_id, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        showNotificationManager()
        return START_NOT_STICKY
    }
}