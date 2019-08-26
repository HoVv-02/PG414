package com.eriskip.ble_pg414;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.icu.text.IDNA;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        //слушатель - он будет ждать когда изменятся координаты и отправит их пользователю
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Intent broadcastIntent = new Intent(InfoPage.BROADCAST_ACTION);                     //интент для широковещательной передачи данных из сервиса в форму активити
                String lat;                                                                         //широта
                String longt;                                                                       //долгота
                lat = location.getLatitude() + "";
                longt = location.getLongitude() + "";
                if (lat.length() > 12) lat = lat.substring(0, 11);                                  //регулируем длинну полученных координат
                if (longt.length() > 12) longt = longt.substring(0, 11);                            //регулируем длинну полученных координат
                broadcastIntent.putExtra(InfoPage.PARAM_TASK,lat+", "+ longt);                //формируем данные для отправки в активити
                sendBroadcast(broadcastIntent);                                                     //отправляем
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {
                provider = locationManager.getBestProvider(criteria, true);                     //если провайдер не достпуен, пробуем получить другой
                locationManager.requestLocationUpdates(provider,5000,0,listener);
            }
        };
        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);                                                               //определяем по какому критерию будет выбираться лучший провайдер
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        //noinspection MissingPermission
        provider = locationManager.getBestProvider(criteria, true);
        locationManager.requestLocationUpdates(provider,5000,0,listener);
    }

    //При старте, согласно тербованиям API Android v 8.0+ необходимо оповестить пользователя о работе
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ssServiceGPS", "GPS_service onStartCommand");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.bluetooth)
                .setContentTitle(getString(R.string.Geo_work))                          //заголовок
                .setContentText(getString(R.string.Geo_work_text))                      //текст уведомления
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX);

        Notification notification = builder.build();

        this.startForeground(196, notification);             //показываем сообщение
                                                                 //запускаем в потоке
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        super.onDestroy();
        if(locationManager != null){
            //noinspection MissingPermission
            locationManager.removeUpdates(listener);
        }
    }


}
