package com.eriskip.ble_pg414;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.text.IDNA;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GPS_service extends Service {
    public GPS_service() {
    }

    private LocationListener listener;
    private LocationManager locationManager;
    private Criteria criteria;
    private String provider;

    private int minDistance = 0;      //минимальная дистанция при которой необходимо обновлять GPS координаты
    String CHANNEL_ID = "My_channel";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        createNotificationChannel();            //создаем канал уведомлений, без него не получится содзать увдеомление на 8+ Андроид

        Log.d("ssServiceGPS", "GPS_service onStartCommand");
        NotificationCompat.Builder builder = new
                NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.bluetooth)
                .setContentTitle(getString(R.string.Geo_work))                          //заголовок
                .setContentText(getString(R.string.Geo_work_text))                      //текст уведомления
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_DEFAULT); //PRIORITY_MAX

        Notification notification = builder.build();

        this.startForeground(196, notification);             //показываем сообщение

        //слушатель - он будет ждать когда изменятся координаты и отправит их пользователю
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                Intent broadcastIntent = new Intent(InfoPage.BROADCAST_ACTION);                     //интент для широковещательной передачи данных из сервиса в форму активити
                String lat;                                                                         //широта
                String longt;                                                                       //долгота
                lat = location.getLatitude() + "";
                longt = location.getLongitude() + "";
                if (lat.length() > 12)
                    lat = lat.substring(0, 11);                                  //регулируем длинну полученных координат
                if (longt.length() > 12)
                    longt = longt.substring(0, 11);                            //регулируем длинну полученных координат
                broadcastIntent.putExtra(InfoPage.PARAM_TASK, lat + ", " + longt);                //формируем данные для отправки в активити
                Log.d("GPS", "обновил координаты:" + lat + ", " + longt);
                sendBroadcast(broadcastIntent);                                                     //отправляем
            }

            @Override
            public void onStatusChanged(String provider, int state, Bundle bundle) {
                Log.d("GPS", "статус провайдера изменен: " + provider + ". " + state);
            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String prov) {
                Log.d("GPS", "Провайдера отключен: " + prov);
                provider = locationManager.getBestProvider(criteria, true);                     //если провайдер недостпуен, пробуем получить другой
                UpdateLocation();
            }
        };
        //определяем по какому критерию будет выбираться лучший провайдер
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
         //---Выбираем лучший провайдер. Но исходя из того, что GPS потребляет много, включаем оптимальный режим качество/потребление
          criteria = new Criteria();
          criteria.setAccuracy(Criteria.ACCURACY_FINE);
          provider = locationManager.getBestProvider(criteria, true);
          UpdateLocation();
    }

    //При старте, согласно тербованиям API Android v 8.0+ необходимо оповестить пользователя о работе
    public int onStartCommand(Intent intent, int flags, int startId) {

                                                                 //запускаем в потоке
        int mode = intent.getIntExtra("mode", 1);
        if (mode == 2)
        {
            provider = locationManager.getBestProvider(criteria, true);
            UpdateLocation();
        } else
        if (mode == 3)
        {
            provider = "network";
            UpdateLocation();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        super.onDestroy();
        if(locationManager != null){
            //noinspection MissingPermission
            locationManager.removeUpdates(listener); //
        }
    }

    private void UpdateLocation()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("GPS", "Недостаточно разрешенеий для обновления кооординат");
            return;
        }
        locationManager.requestLocationUpdates(provider, 5000, minDistance, listener);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
