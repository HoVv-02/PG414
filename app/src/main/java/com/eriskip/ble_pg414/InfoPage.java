package com.eriskip.ble_pg414;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.renderscript.ScriptGroup;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;




public class InfoPage extends AppCompatActivity {

    public static String URL_reg = "/dev_add.php";
    public static String URL_event = "/event_add.php";
    private static final String TAG = "Connection PG414";
 //   PowerManager pm;                                //Power Manager для управления питанием устройства
 //   PowerManager.WakeLock wakeLock;                 //конкретный объект который управляет отключение экрана и тп.


    enum Sendind{eReg_info, eEvent, eNone}

    public static Sendind Send_Message = Sendind.eReg_info;                                               //переменная, которая отвечает за то какой сейчас пакет отправляется на сервер

    //>>>>>  UI ------------------------------------------------------------------------------------
    public static TextView tconc1, tconc2, tconc3, tconc4, tzavod, gaz1, gaz2, gaz3, gaz4, tgps, tstatus,
             charge, errcon;                                                                             //текстовые поля
    ImageView disconnect;
    //----------------------------------------------------------------------------------------------

    public static TaskDynRead readDynParam;         //поток чтения параметров
    public   MyTask_reg send_asynk;                 //поток отправки сообщений

    public static LocationManager manager;                        //менеджер локаций для работы с GPS
    public static LocationManager managerNet;                     //менеджер локаций для работы с сервисами от гугл

    public static boolean connect_server =  false;                //соединение с сервером
    public static boolean user_register =   true;                 //подошел ли пароль
    public static boolean has_be_register = false;                //было ли зарегистрированно устройство


    public int connToDev = 0;                      // попытки подключения

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info_page);
        if (!Connect.offline && !Connect.hideMode)
        Connect.myPG.clean_text();                      //Чистим текстовые переменные класса ПГ-414
        else
            Connect.read_pause = true;
        //Ассоциаируем UI объекты
        tconc1 = findViewById(R.id.tconc1);
        tconc2 = findViewById(R.id.tconc2);
        tconc3 = findViewById(R.id.tconc3);
        tconc4 = findViewById(R.id.tconc4);

        tzavod = findViewById(R.id.tzavod);
        tzavod.setText(Connect.myPG.zavod_number +"");

        gaz1 = findViewById(R.id.gaz1);
        gaz2 = findViewById(R.id.gaz2);
        gaz3 = findViewById(R.id.gaz3);
        gaz4 = findViewById(R.id.gaz4);

        errcon = findViewById(R.id.err_con);
        charge = findViewById(R.id.percent);

        disconnect = findViewById(R.id.disconnect);

        /************************Получаем параметры, введенные пользователем**********************/
        Connect.myPG.password    = MainActivity.Password;
        Connect.myPG.login       = MainActivity.Login;
        Connect.myPG.descriptor  = MainActivity.Description;
        /*---------------------------------------------------------------------------------------*/

        //***********************************************************************************************************************************
        //Отправка на сервер
        send_message_to_server(Sendind.eReg_info);


        //GPS
        tgps = findViewById(R.id.tgps);
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        managerNet = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tgps.setText("Запрет на геолокацию");
            return;
        }



        //Выводим описатели газа
        gaz1.setText(Connect.myPG.gazType[0] + ", " + Connect.myPG.gazUnit[0]); //  R.string.h2s)
        gaz2.setText(Connect.myPG.gazType[1] + ", " + Connect.myPG.gazUnit[1]);  // R.string.co);
        gaz3.setText(Connect.myPG.gazType[2] + ", " + Connect.myPG.gazUnit[2]);  // R.string.o2);
        gaz4.setText(Connect.myPG.gazType[3] + ", " + Connect.myPG.gazUnit[3]); //  R.string.ch4)

        //Статус
        tstatus = findViewById(R.id.tstate);

        readDynParam = new TaskDynRead();
        send_asynk = new MyTask_reg();

        startService(new Intent(this, PG_Service.class));
        //Обновляем GPS
        Location lastKnownLocation = getLastKnownLocation();
        UpdateLocation(lastKnownLocation);
        //----------------------------------
        //потоки
        if (!Connect.offline) {
            fon_val_refresh_start();
            readDynParam.execute();
            send_asynk.execute();


        }
    }

    public static void runGPS()
    {
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 2, listener);
        if (managerNet.getProvider(LocationManager.NETWORK_PROVIDER) != null)
        managerNet.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 2, listener);
    }

    @Override
    protected void onResume()
    {
        //При восстановлении работы вновь запускаем GPS & NEY провайдеров для определения координат
        super.onResume();
        runGPS();
        Log.d("ON RESUME RUN", "Я проснулся");
    }

    @Override
    protected void onPause()
    {
        Log.d("ON PAUSE RUN", "Я пошел спать");
        //При остановке отключаем GPS позиционирование
        super.onPause();
      //  manager.removeUpdates(listener);

    }

    //Обработчик кнопки Подробнее
    public void NullOut(View view){
    }

    /* Поток чтения динамических параметров */
    class TaskDynRead extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                while (true) {

                    if (isCancelled()) return null;

                    sending_reg_info();
                    //Если не пришла команда паузы чтения
                    if (!Connect.read_pause) {
                        if (Connect.State_pack == Connect.RX_pack.COMPLETE )  connToDev = 0;
                        Log.d("ПГ-414","Делаю запрос");
                        //Чтение статуса
                        Connect.myPG.reqDyn();
                        Connect.State_pack = Connect.RX_pack.DYNPARAM;
                        Thread.sleep(2300);
                        Connect.myPG.startRead();
                        while (Connect.State_pack != Connect.RX_pack.COMPLETE && abort_counter < 5)
                            ;                      //ждем пока не прочтется
                        if (abort_counter >= 5)
                            Log.d("ПГ-414","Не могу достучасться");
                        else
                            Log.d("ПГ-414","Прочитал");

                        abort_counter = 0;
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

        public byte cnt_sec = 0;
        byte abort_counter = 0;

        ///Таймер для обновления графического интерфейса в зависимости от показаний
        public  Timer timer;
        private void fon_val_refresh_start() {
            timer = new Timer();

            timer.scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            UI_update();            //Обновление UI и отправка на сервер
                            Construct_JobInfo();
                            abort_counter++;        //Увеличиваем счетчик сброса
                        }
                    });
                }
            }, 2000, 1500);
        }


        /**---------------------------------------------------------------*GPS*--------------------------------------------------------------**/
        private static  LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                UpdateLocation(location);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
            @Override
            public void onProviderEnabled(String provider) {
            }
            @Override
            public void onProviderDisabled(String provider) {
            }
        };


        static void UpdateLocation(Location location)
        {
            if (location!=null) {
                String lat;
                String longt;
                String result;
                lat = location.getLatitude() + "";
                longt = location.getLongitude() + "";
                if (lat.length() > 12) lat = lat.substring(0, 11);
                if (longt.length() > 12) longt = longt.substring(0, 11);
                result = lat +  ", " + longt;
                tgps.setText(lat + ", \r" + longt);
                Connect.myPG.gps = result;
            }
            else
            {
                tgps.setText("проверьте настройки GPS");
            }
        }
        /////////*******         Отправка данных на сервер           *********\\\\\\\\\\\\\
        class MyTask_reg extends AsyncTask<Void,Void,Void> {

            @Override
            protected Void doInBackground(Void... params) {
                while (true) {
                //    sending_reg_info();
                    if (isCancelled()) return null;
                    }

            }
        }
         public static byte[] sending_reg_info()
         {
             Log.d("На сервер","отправляю");
             int p = 0;
             if (Send_Message != Sendind.eNone) {
                 String params = "";
                 if (Send_Message == Sendind.eReg_info) {
                     params = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&description=" + Connect.myPG.descriptor + "&key=1562";
                 } else if (Send_Message == Sendind.eEvent) {
                     if (Connect.myPG.status.length() == 21)  Connect.myPG.status = "OK";
                    //Если статус пустой то шлем OK;
                     if (Connect.myPG.status.length() < 5) {
                         Connect.myPG.status = "OK";
                     }

                     params = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&login=" + Connect.myPG.login + "&password=" + Connect.myPG.password
                             + "&gps=" + Connect.myPG.gps + "&state=" + Connect.myPG.status
                             + "&channel1=<b>" + tconc1.getText().toString()+"</b><br>"+ gaz1.getText().toString()   //(R.string.h2s)
                             + "&channel2=<b>" + tconc2.getText().toString()+"</b><br>"+ gaz2.getText().toString()   //(R.string.co)
                             + "&channel3=<b>" + tconc3.getText().toString()+"</b><br>"+ gaz3.getText().toString()   //(R.string.o2)
                             + "&channel4=<b>" + tconc4.getText().toString()+"</b><br>"+ gaz4.getText().toString()   //(R.string.ch4)
                             + "&field1="+ Connect.myPG.percent_charge                                                            //заряд
                             + "&key=1562";
                 } else return null;
                 byte[] dataz = null;
                 InputStream is = null;

                 try {
                     URL url;
                     if (Send_Message == Sendind.eReg_info)
                         url = new URL(MainActivity.Server + URL_reg);
                     else
                         url = new URL(MainActivity.Server + URL_event);
                     HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                     conn.setRequestMethod("POST");
                     conn.setDoOutput(true);
                     conn.setDoInput(true);

                     conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));
                     OutputStream os = conn.getOutputStream();
                     dataz = params.getBytes("UTF-8");
                     os.write(dataz);
                     dataz = null;

                     conn.connect();
                     connect_server = true;
                     int responseCode = conn.getResponseCode();
                     if (responseCode == 200 && Send_Message == Sendind.eReg_info)
                         has_be_register = true;
                     if (responseCode != 200) connect_server = false;

                     ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     is = conn.getInputStream();

                     byte[] buffer = new byte[8192];
                     int bytesRead;
                     while ((bytesRead = is.read(buffer)) != -1) {
                         baos.write(buffer, 0, bytesRead);
                     }dataz = baos.toByteArray();

                     if (dataz[1] == 'e' && dataz[2] == 'r')
                     {
                         connect_server = false;
                     }
                 } catch
                         (Exception ex) {
                     Log.d(TAG, ex.toString());
                 } finally {
                     try {

                         if (is != null)
                             is.close();
                     } catch (Exception ex) {
                     }
                 }
                 Send_Message = Sendind.eNone;
                 if (connect_server && dataz[1] == 'n' && dataz[2] == 'o')
                     user_register = false;
                 else
                     user_register = true;
                 return dataz;
             }
             return null;

         }



         public void send_message_to_server(Sendind params)
         {
             Send_Message = params;
         }

    LocationManager mLocationManager;

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

    //Ввод адреса сервера
    protected void enter_server(View view)
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(InfoPage.this, R.style.AlertDialogCustom);

        alert.setTitle(getString(R.string.Config_but));
        alert.setMessage(getString(R.string.Enter_server));

        // Set an EditText view to get user input
        final EditText input = new EditText(InfoPage.this);
        input.setText(MainActivity.Server);
        input.setTextColor(Color.rgb(232, 228, 211));
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String resulty = input.getText().toString();
                //
                MainActivity.Server = resulty;
                SharedPreferences.Editor editor = MainActivity.mSettings.edit();
                editor.putString(MainActivity.SERVER_SETTING,  resulty);
                editor.commit();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    //*******************TEST ZONE***************************
    //*******************************************************
    public void Construct_JobInfo()
    {

    }

    public void UI_update()
    {
            if (!Connect.read_pause) {
                NumberFormat nf[] = new NumberFormat[4];
                for (byte j =0; j < 4; j++)
                {
                    nf[j] = NumberFormat.getInstance();
                    nf[j].setMaximumFractionDigits(Connect.myPG.gazDiskret[j]*(-1));
                    nf[j].setMinimumFractionDigits(Connect.myPG.gazDiskret[j]*(-1));
                    nf[j].setGroupingUsed(false);
                }
                //В данном коде выводим форматированную     концентрацию                 при этом учитываем          дискретность
                tconc1.setText(nf[0].format(Connect.myPG.conc1/(float)(Connect.myPG.gazDelitel[Connect.myPG.gazDiskret[0]*(-1)])));
                tconc2.setText(nf[1].format(Connect.myPG.conc2/(float)(Connect.myPG.gazDelitel[Connect.myPG.gazDiskret[1]*(-1)])));
                tconc3.setText(nf[2].format(Connect.myPG.conc3/(float)(Connect.myPG.gazDelitel[Connect.myPG.gazDiskret[2]*(-1)])));
                tconc4.setText(nf[3].format(Connect.myPG.conc4/(float)(Connect.myPG.gazDelitel[Connect.myPG.gazDiskret[3]*(-1)])));

                charge.setText(getResources().getString(R.string.Charge) + Connect.myPG.percent_charge + "%");

                tstatus.setText(Connect.myPG.Make_State());
                if (!user_register && connect_server)
                {
                    errcon.setText(R.string.un_logi);
                    errcon.setVisibility(View.VISIBLE);
                }
                else
                if (!connect_server) {
                    errcon.setText(R.string.Server_aerror);
                    errcon.setVisibility(View.VISIBLE);
                    disconnect.setVisibility(View.VISIBLE);
                }
                else {
                    errcon.setVisibility(View.INVISIBLE);
                    disconnect.setVisibility(View.INVISIBLE);
                }


                if (cnt_sec == 5) {
                    cnt_sec = 0;
                    if (!has_be_register)                               //В зависимости от того была ли регистрация
                        send_message_to_server(Sendind.eReg_info);      //Шлем регистрационные данные
                    else
                        send_message_to_server(Sendind.eEvent);        //Шлем данные о событиях
                }
                cnt_sec++;

                connToDev++;
                if (connToDev > 222) connToDev = 8;
                if (connToDev > 5)
                {
                    tstatus.setText(R.string.err_con_dev);
                    ShowMessageForDisconnect();
                    Connect.myPG.mBluetoothGatt.connect();
                }
            }
            else if (Connect.hideMode)
            {
                if (cnt_sec == 5) {
                    cnt_sec = 0;
                    if (!has_be_register)                               //В зависимости от того была ли регистрация
                        send_message_to_server(Sendind.eReg_info);      //Шлем регистрационные данные
                    else
                        send_message_to_server(Sendind.eEvent);        //Шлем данные о событиях
                }
                cnt_sec++;
            }

    }


    //Выводим уведомление
    void ShowMessageForDisconnect()
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.avrr)
                        .setContentTitle("Внимание")
                        .setContentText("Потеряна связь с устройством!")
                        .setAutoCancel(true)
                        .setPriority(Notification.PRIORITY_MAX);

        Notification notification = builder.build();

// Show Notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(196, notification);
    }
//-----------------------------------------------------------------------------------------------
}


