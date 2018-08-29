package com.eriskip.ble_pg414;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.renderscript.ScriptGroup;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Timer;
import java.util.TimerTask;




public class InfoPage extends AppCompatActivity {

    public String URL_reg = "http://89.250.220.50:8001/dev_add.php";
    public String URL_event = "http://89.250.220.50:8001/event_add.php";
    private static final String TAG = "Connection PG414";

    enum Sendind{eReg_info, eEvent, eNone}

    Sendind Send_Message = Sendind.eReg_info;                                               //переменная, которая отвечает за то какой сейчас пакет отправляется на сервер


    TextView tconc1, tconc2, tconc3, tconc4, tzavod, gaz1, gaz2, gaz3, gaz4, tgps, tstatus, errcon; //текстовые поля

    public static TaskDynRead readDynParam;         //поток чтения параметров
    LocationManager manager;                        //мэнеджер локаций для работы с GPS и NetService

    boolean connect_server = false;                 //соединение с сервером
    boolean has_be_register = false;
    public   MyTask_reg send_asynk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info_page);
        Connect.myPG.clean_text();
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


        /***********************TEMP***********************************/
        Connect.myPG.password = "test";  Connect.myPG.login = "test";
        Connect.myPG.descriptor = "ПГ-414. Отдел разработок";

        //Отправка на сервер
        send_message_to_server(Sendind.eReg_info);


        //GPS
        tgps = findViewById(R.id.tgps);
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tgps.setText("GPS отключен");
            return;
        }

        //Выводим описатели газа
        gaz1.setText(Connect.myPG.gazType[0] + ", " + Connect.myPG.gazUnit[0]);
        gaz2.setText(Connect.myPG.gazType[1] + ", " + Connect.myPG.gazUnit[1]);
        gaz3.setText(Connect.myPG.gazType[2] + ", " + Connect.myPG.gazUnit[2]);
        gaz4.setText(Connect.myPG.gazType[3] + ", " + Connect.myPG.gazUnit[3]);



        //Статус
        tstatus = findViewById(R.id.tstate);

        readDynParam = new TaskDynRead();
        send_asynk = new MyTask_reg();
        //потоки
        if (!Connect.offline) {
            fon_val_refresh_start();
            readDynParam.execute();
            send_asynk.execute();

        }
    }

    @Override
    protected void onResume()
    {
        //При восстановлении работы внось запускаем GPS & NEY провайдеров для определения координат
        super.onResume();
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 2, listener);
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 2, listener);
    }

    @Override
    protected void onPause()
    {
        //При остановке отключаем GPS позиционирование
        super.onPause();
        manager.removeUpdates(listener);

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
                        //Чтение статуса
                        Connect.myPG.reqDyn();
                        Connect.State_pack = Connect.RX_pack.DYNPARAM;
                        Thread.sleep(1300);
                        Connect.myPG.startRead();
                        while (Connect.State_pack != Connect.RX_pack.COMPLETE)
                            ;                      //ждем пока не прочтется
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

        public  Timer timer_server_sender;
        public byte cnt_sec = 0;

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

                                tstatus.setText(Connect.myPG.Make_State());
                                if (!connect_server)
                                    errcon.setVisibility(View.VISIBLE);
                                else
                                    errcon.setVisibility(View.INVISIBLE);

                                if (cnt_sec == 4) {
                                    cnt_sec = 0;
                                    if (!has_be_register)                               //В зависимости от того была ли регистрация
                                        send_message_to_server(Sendind.eReg_info);      //Шлем регистрационные данные
                                    else
                                        send_message_to_server(Sendind.eEvent);        //Шлем данные о событиях
                                }
                                cnt_sec++;
                            }
                        }
                    });
                }
            }, 2000, 1500);
        }


        /**---------------------------------------------------------------*GPS*--------------------------------------------------------------**/
        private LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location!=null) {
                    tgps.setText(location.getLatitude() + ", \r" + location.getLongitude());
                    Connect.myPG.gps = (location.getLatitude() + ", " + location.getLongitude());
                }
                else
                {
                    tgps.setText("x");
                }
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
         public byte[] sending_reg_info()
         {
             if (Send_Message != Sendind.eNone) {
                 String params = "";
                 if (Send_Message == Sendind.eReg_info) {
                     params = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&description=" + Connect.myPG.descriptor + "&key=1562";
                 } else if (Send_Message == Sendind.eEvent) {
                    //Если статус пустой то шлем OK;
                     if (Connect.myPG.status.length() < 5) Connect.myPG.status = "OK";
                     params = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&login=" + Connect.myPG.login + "&password=" + Connect.myPG.password
                             + "&gps=" + Connect.myPG.gps + "&state=" + Connect.myPG.status
                             + "&channel1=<b>" + tconc1.getText().toString()+"</b><br>"+ gaz1.getText().toString()
                             + "&channel2=<b>" + tconc2.getText().toString()+"</b><br>"+ gaz2.getText().toString()
                             + "&channel3=<b>" + tconc3.getText().toString()+"</b><br>"+ gaz3.getText().toString()
                             + "&channel4=<b>" + tconc4.getText().toString()+"</b><br>"+ gaz4.getText().toString()
                             + "&key=1562";
                 } else return null;
                 byte[] dataz = null;
                 InputStream is = null;

                 try {
                     URL url;
                     if (Send_Message == Sendind.eReg_info)
                         url = new URL(URL_reg);
                     else
                         url = new URL(URL_event);
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
                     }
                     dataz = baos.toByteArray();
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
                 return dataz;
             }
             return null;

         }



         public void send_message_to_server(Sendind params)
         {
             Send_Message = params;
         }
}


