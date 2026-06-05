package com.eriskip.ble_pg414;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import android.util.Log;



public class GPS_service extends Service {
    public GPS_service() {
    }

    private LocationListener listener;
    private LocationManager locationManager;
    private Criteria criteria;
    private String provider;

    private int minDistance = 0;      //минимальная дистанция при которой необходимо обновлять GPS координаты
    public static String CHANNEL_ID = "My_channel";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
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
                sendLocation(location);
                Log.d("GPS", "Изменение координат");
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
        startTimer();       //запускаем таймер отправки пустых данных для пробуждения в фоне
    }

    //При старте, согласно тербованиям API Android v 8.0+ необходимо оповестить пользователя о работе
    public int onStartCommand(Intent intent, int flags, int startId) {
        //запускаем в потоке
        int mode = intent.getIntExtra("mode", 1);
        if (mode == 2) {
            provider = locationManager.getBestProvider(criteria, true);
            UpdateLocation();
        } else if (mode == 3) {
            provider = "network";
            UpdateLocation();
        }
        return START_NOT_STICKY;
    }

    private final Handler handler = new Handler();
    private Runnable timerRunnable;

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                sendDeSleeper();
                handler.postDelayed(this, 5000);  // перепланируем себя
            }
        };
        handler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    public void onDestroy() {
        stopTimer();
        super.onDestroy();
        if (locationManager != null) {
            //noinspection MissingPermission
            locationManager.removeUpdates(listener); //
        }
    }

    private void UpdateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("GPS", "Недостаточно разрешенеий для обновления кооординат");
            return;
        }
        locationManager.requestLocationUpdates(provider, 5000, minDistance, listener);
    }


    //Отправка пустого сообщения для того чтобы сработал broadcast reciver который выведет активити на передний план
    private void sendDeSleeper() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("GPS", "Недостаточно разрешенеий для обновления кооординат");
            return;
        }
        Location l = locationManager.getLastKnownLocation(provider);
        Log.d("GPS", "Отправляем периодические координаты");
        sendLocation(l);
    }

    private void sendLocation(Location location)
    {
        Intent broadcastIntent = new Intent(InfoPage.BROADCAST_ACTION);                     //интент для широковещательной передачи данных из сервиса в форму активити
        String lat;                                                                         //широта
        String longt;                                                                       //долгота
        if (location != null) {
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
    }
}
