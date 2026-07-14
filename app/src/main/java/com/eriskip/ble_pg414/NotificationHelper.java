package com.eriskip.ble_pg414;

import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    Context context;
    public NotificationHelper (Context context){
        this.context = context.getApplicationContext();
    }

    //-----------------------------------------------------------------
    //                  Выводим уведомление об отключении
    //-----------------------------------------------------------------
    public void showDisconnectNotification(Context context){
        Log.d("showMessage", "Показываю уведомление об отлючении");
        Notification notification;
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        long[] pattern = {0, 100, 1000, 200, 2000};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder = new Notification.Builder(context, GPS_service.CHANNEL_ID)
                    .setSmallIcon(R.drawable.avrr)
                    .setContentTitle(context.getString(R.string.Alert_title))
                    .setContentText(context.getString(R.string.con_lost))
                    .setAutoCancel(true)
                    .setVisibility(VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_ALARM);
//                    .setFullScreenIntent(fullScreenPendingIntent, true);
            notification = builder.build();
        }
        else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, GPS_service.CHANNEL_ID)
                    .setSmallIcon(R.drawable.avrr)
                    .setContentTitle(context.getString(R.string.Alert_title))
                    .setContentText(context.getString(R.string.con_lost))
                    .setAutoCancel(true)
                    .setVibrate(pattern)
                    .setSound(alarmSound)
                    .setPriority(Notification.PRIORITY_MAX);
//                    .setFullScreenIntent(fullScreenPendingIntent, true);

            notification = builder.build();

        }
// Show Notification
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(197, notification);
    }

    public void createNotificationChannel(){
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(GPS_service.CHANNEL_ID, name, importance);
            channel.setDescription(description);
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            channel.setSound(alarmSound, attributes);
            channel.setLockscreenVisibility(VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            channel.enableLights(true);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
