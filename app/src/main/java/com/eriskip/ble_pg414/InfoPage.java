package com.eriskip.ble_pg414;

import android.Manifest;

import android.app.Notification;
import android.app.NotificationManager;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;

import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static java.lang.Math.abs;


public class InfoPage extends AppCompatActivity {

    public static String URL_reg = "/dev_add.php";                      //файл который отвечает за добавления устройства - и как таковой проверки регистрации
    public static String URL_event = "/event_add.php";                  //файл который отвечает за регстрацию события на сервере
    private static final String TAG = "Connection PG414";               //таг для логера
    private static final String FILE_NAME = "archive.dat";              //имя файла архива

    public final static String BROADCAST_ACTION = "com.eriskip.ble_pg414";
    BroadcastReceiver br;   //слушатель параметров геолокациии от нашего сервиса

    enum Sendind{eReg_info, eEvent, eArchive, eNone}

    public static Sendind Send_Message = Sendind.eReg_info;                                               //переменная, которая отвечает за то какой сейчас пакет отправляется на сервер

    //>>>>>  UI ------------------------------------------------------------------------------------
    public static TextView tconc1, tconc2, tconc3, tconc4, tzavod, gaz1, gaz2, gaz3, gaz4, tgps, tstatus, //текстовые поля
             charge, errcon, arch_cnt;
    ImageView disconnect, arch_alert;
    //----------------------------------------------------------------------------------------------

    CheckBox cbMoblity;

    public static Thread readDynParam;         //поток чтения параметров

    public static boolean connect_device =  false;                //соединение с устройством
    public static boolean connect_server =  false;                //соединение с сервером
    public static boolean user_register =   true;                 //подошел ли пароль
    public static boolean has_be_register = false;                //было ли зарегистрированно устройство

    public static int lines_archive = 0;                          //строк в архиве
    public final static String PARAM_TASK = "task";
    public static String param_lat_lon = "none";                  //координаты полученные от сервиса - Широта



    public int connToDev = 0;                      // попытки подключения
    public File arch_file;                         // полный путь файла архива

    @Override
    protected void onDestroy()
    {
        Log.d("InfoPage","Меня сломали. Гасим сервисы");
        stopService(new Intent(this, GPS_service.class));
        timer.cancel();
        stop_dyn = true;
        super.onDestroy();

    }

    LocationManager mLocationManager;

    private Location getLastKnownLocation() {
        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            try{
                Location l = mLocationManager.getLastKnownLocation(provider);

                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }


        }
        return bestLocation;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        stop_dyn = false;
        setContentView(R.layout.activity_info_page);
        if (!Connect.offline && !Connect.hideMode)
        {
            Connect.myPG.clean_text();                      //Чистим текстовые переменные класса ПГ-414
            Connect.read_pause = false;
        }
        else
            Connect.read_pause = true;
        Context context = getApplicationContext();
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
        arch_cnt = findViewById(R.id.arch_cnt);

        arch_alert = findViewById(R.id.arch_alert);         //иконка алерт у архива
        disconnect = findViewById(R.id.disconnect);         //картинка дисконнекта

        cbMoblity = findViewById(R.id.cbMoblity);
        if ((Connect.myPG.onoff2 & (1 << 10)) == 0) cbMoblity.setChecked(true);
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

        //Класс для приема сообщения из потока сервиса
        br = new BroadcastReceiver() {
            // действия при получении сообщений
            public void onReceive(Context context, Intent intent) {
                String coord = intent.getStringExtra(PARAM_TASK);
                if (coord.length() > 0)                             //Проверяем на длинну. Елси 0, то цель экого пакета просто разбудить активити
                    param_lat_lon = coord;
            }
        };
        // создаем фильтр для BroadcastReceiver
        IntentFilter intFilt = new IntentFilter(BROADCAST_ACTION);
        // регистрируем (включаем) BroadcastReceiver
        registerReceiver(br, intFilt);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tgps.setText("Запрет на геолокацию");
            return;
        }

        //Выводим описатели газа
        gaz1.setText(Connect.myPG.gazType[0] + ", " + Connect.myPG.gazUnit[0]);
        gaz2.setText(Connect.myPG.gazType[1] + ", " + Connect.myPG.gazUnit[1]);
        gaz3.setText(Connect.myPG.gazType[2] + ", " + Connect.myPG.gazUnit[2]);
        gaz4.setText(Connect.myPG.gazType[3] + ", " + Connect.myPG.gazUnit[3]);

        //Статус
        tstatus = findViewById(R.id.tstate);
        arch_file = context.getFileStreamPath(FILE_NAME);

        //Проверяем число неотправленных пакетов
        readCntLines();
        if (lines_archive > 0)
        {
            //Делаем видимыми картинку
            arch_cnt.setVisibility(View.VISIBLE);
            arch_alert.setVisibility(View.VISIBLE);
            arch_cnt.setText("" + lines_archive);
        }
        else
        {
            arch_cnt.setVisibility(View.INVISIBLE);
            arch_alert.setVisibility(View.INVISIBLE);
        }

        readDynParam = new Thread(runnableDynTask);
        //Делаем так что сервис будет запускаться в любом случае, а не только при сне
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             startForegroundService(new Intent(this, GPS_service.class));
         }
         else
         {
             startService(new Intent(this, GPS_service.class));
         }
        //Обновляем GPS
        Location lastKnownLocation = getLastKnownLocation();
        UpdateLocation(lastKnownLocation);
        //----------------------------------
        //потоки
        fonValRefreshStart();
        if (!Connect.offline) {

            readDynParam.start(); //TaskDynRead
        }
        cbMoblity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCheck();
            }
        });

    }
    //Обновление параметров GPS
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

    @Override
    protected void onResume()
    {
        //При восстановлении работы вновь запускаем GPS & NEY провайдеров для определения координат
        super.onResume();
        /*При выходе из сна выставляем режим лучшего доступного провайдора */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, GPS_service.class).putExtra("mode", 3));
        }
        Log.d("ON RESUME RUN", "Я проснулся");
    }

    @Override
    protected void onPause()
    {
        Log.d("ON PAUSE RUN", "Я пошел спать");
        /*При переходе в сон выставляем режим провайдора location NETWORK*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, GPS_service.class).putExtra("mode", 2));
        }
        //При остановке переводим GPS позиционирование на другой сервис
        super.onPause();
    }

    //Обработчик кнопки Подробнее
    public void NullOut(View view){
    }

    boolean cnt_con;           //можно ли увеличивать счетсчик подключения и писать в файл
    boolean sending_archive;   //идет отправка архива
    static public boolean stop_dyn;     //отключить динамическое чтение

    /* Поток чтения динамических параметров */
    Runnable runnableDynTask = new Runnable() {
        public void run() {
            try {
                while (true) {

                    if (stop_dyn) return;

                    sendingInfoToServ("");
                    //Если не пришла команда паузы чтения
                    if (!Connect.read_pause) {
                        if (Connect.State_pack == Connect.RX_pack.COMPLETE )
                        {
                            connToDev = 0;
                            connect_device = true;
                        }
                        Log.d("ПГ-414","Делаю запрос");
                        //Чтение статуса
                        Connect.myPG.reqDyn();
                        Connect.State_pack = Connect.RX_pack.DYNPARAM;
                        Thread.sleep(2300);
                        if (stop_dyn) return;
                        Connect.myPG.startRead();
                        while (Connect.State_pack != Connect.RX_pack.COMPLETE && abort_counter < 5)
                            ;                      //ждем пока не прочтется
                        if (abort_counter >= 5)
                            Log.d("ПГ-414","Не могу достучасться");
                        else {
                            Log.d("ПГ-414", "Прочитал");
                            connToDev = 0;
                        }

                        abort_counter = 0;
                        cnt_con = true;

                        try {
                            //Если есть подключение к серверу
                            if (connect_server) {
                                while (lines_archive > 0 && connect_server) {
                                    sending_archive = true;
                                    Thread.sleep(60);
                                    Send_Message = Sendind.eArchive;
                                    sendingInfoToServ(readLastLine(arch_file));      //отправляем на сервер и удалаям строку из файла
                                    lines_archive--;
                                }
                                sending_archive = false;
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return;
        }
    };

        public byte cnt_sec = 0;
        byte abort_counter = 0;

        ///Таймер для обновления графического интерфейса в зависимости от показаний
        public  Timer timer;
        private void fonValRefreshStart() {
            timer = new Timer();

            timer.scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            UIUpdate();            //Обновление UI и отправка на сервер
                            Construct_JobInfo();
                            abort_counter++;        //Увеличиваем счетчик сброса
                        }
                    });
                }
            }, 2000, 1500);
        }



        //Функция для отправки данных на сервер. Регистрационнная инфомрация отправыляется один раз, динаическая - периодически, сразу после опроса устрйоства
         public static byte[] sendingInfoToServ(String send_arch)
         {
             Log.d("На сервер","отправляю" + connect_server);
             int p = 0;
             if (Send_Message != Sendind.eNone) {
                 String params = "";
                 if (Send_Message == Sendind.eReg_info) {
                     params = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&description=" + Connect.myPG.descriptor + "&key=1b4c9cba071228296ff61d8938623bc9";
                 } else if (Send_Message == Sendind.eEvent) {
                     if (Connect.myPG.status.length() == 21)  Connect.myPG.status = "OK";
                    //Если статус пустой то шлем OK;
                     if (Connect.myPG.status.length() < 5) {
                         Connect.myPG.status = "OK";
                     }
                     params = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&login=" + Connect.myPG.login + "&password=" + Connect.myPG.password
                             + "&gps="  + Connect.myPG.gps + "&state=" + Connect.myPG.status                        //координаты
                             + "&channel1=<b>" + tconc1.getText().toString()+"</b><br>"+ gaz1.getText().toString()  //концентрация по каналу 1 с описателем газа и ед. изм
                             + "&channel2=<b>" + tconc2.getText().toString()+"</b><br>"+ gaz2.getText().toString()  //концентрация по каналу 2 с описателем газа и ед. изм
                             + "&channel3=<b>" + tconc3.getText().toString()+"</b><br>"+ gaz3.getText().toString()  //концентрация по каналу 3 с описателем газа и ед. изм
                             + "&channel4=<b>" + tconc4.getText().toString()+"</b><br>"+ gaz4.getText().toString()  //концентрация по каналу 4 с описателем газа и ед. изм
                             + "&field1="+ Connect.myPG.percent_charge                                              //заряд
                             + "&key=1b4c9cba071228296ff61d8938623bc9";                                                                         //ключ

                 } else if (Send_Message == Sendind.eArchive) {             //если ведется архиваня отправка данных
                     params = send_arch;
                 }
                 else return null;
                 byte[] dataz = null;
                 InputStream is = null;

                 try {
                     URL url;
                     if (Send_Message == Sendind.eReg_info)
                         url = new URL(MainActivity.Server);
                     else
                         url = new URL(MainActivity.Server);
                     HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                     conn.setRequestMethod("POST");
                     conn.setDoOutput(true);
                     conn.setDoInput(true);

                     conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));
                     conn.setRequestProperty("erkey", "1b4c9cba071228296ff61d8938623bc9");
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
                     Log.d("SERVER_RESPONSE", new String(dataz));
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
                 if (dataz != null)
                 {
                   if (dataz.length > 3)
                    if (connect_server && dataz[1] == 'n' && dataz[2] == 'o')
                     user_register = false;
                     else
                     user_register = true;
                 }
                 else
                     connect_server = false;
                 return dataz;
             }
             return null;

         }



         public void send_message_to_server(Sendind parametr)
         {
             Send_Message = parametr;
         }

    //Ввод адреса сервера
    public  void enterServer(View v)
    {
    try {
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
                    //Заполняем поле
                    MainActivity.Server = resulty;
                    MainActivity.editor.putString(MainActivity.SERVER_SETTING, resulty);
                    MainActivity.editor.commit();
                }
            });

            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });

            alert.show();

        }
        catch (Exception ex)
        {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //*******************TEST ZONE***************************
    //Вывод окна для запроса очистки файла
    public void clearBtn()
    {
        try {
            AlertDialog.Builder alert = new AlertDialog.Builder(InfoPage.this, R.style.AlertDialogCustom);

            alert.setTitle(getString(R.string.Alert_title));
            alert.setMessage(getString(R.string.Clear_file));

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    clearFile();
                }
            });

            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alert.show();
        }
        catch (Exception ex)
        {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    //*******************************************************
    public void Construct_JobInfo()
    {

    }

    public void UIUpdate()
    {
            String params = "";
            if (!Connect.read_pause) {

                //Применяем дискретность газов
                NumberFormat nf[] = new NumberFormat[4];
                for (byte j =0; j < 4; j++)
                {
                    nf[j] = NumberFormat.getInstance();
                    nf[j].setMaximumFractionDigits(Connect.myPG.gazDiskret[j]*(-1));
                    nf[j].setMinimumFractionDigits(Connect.myPG.gazDiskret[j]*(-1));
                    nf[j].setGroupingUsed(false);
                }
                //В данном коде выводим форматированную     концентрацию                 при этом учитываем          дискретность
                tconc1.setText(nf[0].format(Connect.myPG.conc1/(float)(Connect.myPG.gazDelitel[abs(Connect.myPG.gazDiskret[0])])));
                tconc2.setText(nf[1].format(Connect.myPG.conc2/(float)(Connect.myPG.gazDelitel[abs(Connect.myPG.gazDiskret[1])])));
                tconc3.setText(nf[2].format(Connect.myPG.conc3/(float)(Connect.myPG.gazDelitel[abs(Connect.myPG.gazDiskret[2])])));
                tconc4.setText(nf[3].format(Connect.myPG.conc4/(float)(Connect.myPG.gazDelitel[abs(Connect.myPG.gazDiskret[3])])));
                //Процент заряда батареи
                charge.setText(getResources().getString(R.string.Charge) + Connect.myPG.percent_charge + "%");
                //Статус
                String tState = Connect.myPG.Make_State();
                if (Connect.mConnectionState != STATE_CONNECTED) {
                    tState = getString(R.string.con_lost);
                    Connect.myPG.status = tState;
                }
                tstatus.setText(tState);
                if (!user_register && connect_server)
                {
                    errcon.setText(R.string.un_logi);            //если пользователь не зарегистрированный , то выводим ошибку авторизации
                    errcon.setVisibility(View.VISIBLE);
                }
                else
                if (!connect_server) {                                 //Если подключен к серверу
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
                        send_message_to_server(Sendind.eEvent);         //Шлем данные о событиях
                }
                cnt_sec++;

                connToDev++;                                            //увеличиываем счетчик попыток подключения. он сбрасывается при успешном чтении
                if (connToDev > 222) connToDev = 11;
                if (connToDev > 10 && !sending_archive)                  //если попыток было 5 а информации от ПГ-414 не поступала выводим сообщение и запускаем переподключение
                {
                    connect_device = false;
                  //  tstatus.setText(R.string.err_con_dev);            //КОСТЫЛЬ. на планшетах почему то постоянно пишет хоть связь и есть
                    try{
                        Connect.myPG.mBluetoothGatt.connect();
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }

                }

                if (lines_archive > 0)                                   //если есть записи в архиве, то выводим их количество на экран
                {
                    arch_cnt.setVisibility(View.VISIBLE);
                    arch_alert.setVisibility(View.VISIBLE);
                    arch_cnt.setText(lines_archive+"");
                    if (sending_archive)
                        tstatus.setText(R.string.Sendnig_Arch);
                } else
                {
                    arch_cnt.setVisibility(View.INVISIBLE);
                    arch_alert.setVisibility(View.INVISIBLE);
                }

            }
            else if (Connect.hideMode)
            {
                if (cnt_sec == 5) {
                    cnt_sec = 0;
                    if (!has_be_register)                               //В зависимости от того была ли регистрация
                        send_message_to_server(Sendind.eReg_info);      //Шлем регистрационные данные
                    else
                        send_message_to_server(Sendind.eEvent);         //Шлем данные о событиях
                }
                cnt_sec++;
            }
             String param = "";

            /*Обновляем информацию по GPS*/
            if (param_lat_lon != "none") {
                tgps.setText(param_lat_lon);
                Connect.myPG.gps = param_lat_lon;
            }

            if (cnt_con)
            {
            //Работа с файлами архива. В него пишем если нет соединения с сервером, но есть связь с ПГ. Эти данные регистрируем в отдельный файл
             if (!connect_server && connect_device) {
                    param = "id_type=1&znumber=" + Connect.myPG.zavod_number + "&login=" + Connect.myPG.login + "&password=" + Connect.myPG.password
                    + "&gps=" + Connect.myPG.gps + "&state=" + "Archive: " + Connect.myPG.status
                    + "&channel1=<b>" + tconc1.getText().toString() + "</b><br>" + gaz1.getText().toString()   //(R.string.h2s)
                    + "&channel2=<b>" + tconc2.getText().toString() + "</b><br>" + gaz2.getText().toString()   //(R.string.co)
                    + "&channel3=<b>" + tconc3.getText().toString() + "</b><br>" + gaz3.getText().toString()   //(R.string.o2)
                    + "&channel4=<b>" + tconc4.getText().toString() + "</b><br>" + gaz4.getText().toString()   //(R.string.ch4)
                    + "&field1=" + Connect.myPG.percent_charge                                                            //заряд
                    + "&key=1b4c9cba071228296ff61d8938623bc9";
                    writeToFile(param);      //пишем в файл и увеличиваем количество линий
                  }
                cnt_con = false;
            }

    }





    //Запись данных в файл.
    protected void writeToFile(String text)
    {
        try {
            text += '\n';
            FileOutputStream fos = null;
            fos = openFileOutput(FILE_NAME, MODE_APPEND);
            fos.write(text.getBytes("UTF8"));
            lines_archive++;
        }
        catch(IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    //Открываем файл и смотрим сколько там неотрпавленных пакетов
    protected void readCntLines()
    {
        try {
            FileInputStream fin = openFileInput(FILE_NAME);
            byte[] buffer = new byte[fin.available()];
            // считаем файл в буфер
            fin.read(buffer, 0, fin.available());
            int linesCount = 0;
            for(int i = 0; i < buffer.length;i++){
                if (buffer[i] == '\n')
                    linesCount++;
            }
            lines_archive = linesCount;         //Число не отправленных пакетов после включения программы
        }
        catch(IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            lines_archive = 0;
        }
    }

    //Чтение последней строки файла. А затем ее удаление
    private static String readLastLine(File file) throws FileNotFoundException, IOException {
        String result = null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            long startIdx = file.length();
            while (startIdx >= 0 && (result == null || result.length() == 0)) {
                raf.seek(startIdx);
                if (startIdx > 0)
                    raf.readLine();
                result = raf.readLine();
                startIdx--;
            }
            raf.setLength(file.length() - result.length());
        }
        return result;
    }

    //Задать размер файлу
    private  static void SetLen(int size)
    {

    }


    //Очистка файла
    protected void clearFile()
    {
     try {
            FileOutputStream fos = null;
            fos = openFileOutput(FILE_NAME, MODE_PRIVATE);
            fos.write(0);
        }
        catch(IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            lines_archive = 0;
        }
    }

    int Desc_counter = 0;          //показывает сколько раз вызван дескриптор

    //Печать текстового дескриптора катринки
    public void printToast(View view)
    {
        Desc_counter++;
        if (Desc_counter > 8)
        {
            clearBtn();
            lines_archive = 0;
        }
        Toast.makeText(this, view.getContentDescription(), Toast.LENGTH_SHORT).show();

    }
    ///

    private void setCheck()
    {
        Connect.read_pause = true;
        Connect.State_pack = Connect.RX_pack.PARAMS;
        if (!cbMoblity.isChecked())
        {
            Connect.myPG.setParamOnOff((short) 0, (short)(1 << 10),'+');
        }
        else
        {
            Connect.myPG.setParamOnOff((short)0, (short)(1 << 10),'-');
        }

    }

//-----------------------------------------------------------------------------------------------
}



