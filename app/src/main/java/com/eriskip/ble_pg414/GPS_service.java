package com.eriskip.ble_pg414;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.icu.text.IDNA;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GPS_service extends Service {
    public GPS_service() {
    }

    public static String value;   //данные которые будем каждый раз передавать в приложение

    LocationManager mLocationManager;
    Location tLocation;
    String lat;
    String longt;

    private Location getLastKnownLocation() {
        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location l = mLocationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
            }
        }
        return bestLocation;
    }



    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    final String LOG_TAG = "myLogs";

    ExecutorService es;                                          //разделитель потоков

    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "GPS_service onCreate");
        es = Executors.newFixedThreadPool(1);           //число потоков
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "GPS_service onDestroy");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "GPS_service onStartCommand");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.bluetooth)
                .setContentTitle("Работа с геолокацией")
                .setContentText("Ведется фоновое определение геолокации")
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX);

        Notification notification = builder.build();

        this.startForeground(196, notification);            //показываем сообщение
        MyRun mr = new MyRun();
        es.execute(mr);                                         //запускаем в потоке
        return super.onStartCommand(intent, flags, startId);
    }

    //Поток для опроса GPS
    class MyRun implements Runnable {

        public MyRun() {
            Log.d(LOG_TAG, "MyRun create");
        }


        public void run() {
            Intent intent = new Intent(InfoPage.BROADCAST_ACTION);
            Log.d(LOG_TAG, "MyRun started");
            try {

                while(true) {
                    tLocation = getLastKnownLocation();
                    lat = tLocation.getLatitude() + "";
                    longt = tLocation.getLongitude() + "";
                    if (lat.length() > 12) lat = lat.substring(0, 11);
                    if (longt.length() > 12) longt = longt.substring(0, 11);
                    value = lat +", "+ longt;
                    // сообщаем о старте задачи
                    intent.putExtra(InfoPage.PARAM_TASK, value);
                    sendBroadcast(intent);
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            stop();
        }

        void stop() {
            Log.d(LOG_TAG, "MyRun#  stopSelfResult");
        }
    }
}
