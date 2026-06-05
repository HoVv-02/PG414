package com.eriskip.ble_pg414;
import android.Manifest;

import android.annotation.SuppressLint;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static java.lang.Math.abs;

import com.eriskip.ble_pg414.library.PG414;

import org.json.JSONException;
import org.json.JSONObject;


public class InfoPage extends AppCompatActivity {
    private static final String FILE_NAME = "archive.dat";              //имя файла архива

    public final static String BROADCAST_ACTION = "com.eriskip.ble_pg414";
    BroadcastReceiver br;   //слушатель параметров геолокациии от нашего сервиса

    public enum Sending{eEvent, eArchive}

    public static Sending Send_Message = Sending.eEvent;                                               //переменная, которая отвечает за то какой сейчас пакет отправляется на сервер

    //>>>>>  UI ------------------------------------------------------------------------------------
    //>>>>>  UI ------------------------------------------------------------------------------------
    public TextView tconc1, tconc2, tconc3, tconc4, tzavod, gaz1, gaz2, gaz3, gaz4, tgps, tstatus, //текстовые поля
             charge, errcon, arch_cnt;
    ImageView disconnect, arch_alert;
    //----------------------------------------------------------------------------------------------

    CheckBox cbMoblity;

    public static Thread readDynParam;         //поток чтения параметров

    public static volatile boolean connect_device =  false;                //соединение с устройством
    public static boolean connect_server =  false;                //соединение с сервером
    private boolean alarmMode = false;                              // аварийный режим, сработка порога
    private boolean lastAlarmState = false;                         // штатный режим

    private boolean isAlarm(int err) {
        if (err == 0) return false;

        // проверяем только нужные биты
        return checkBit(err, 0)  || // Порог 1 - Сенсор 1
                checkBit(err, 1)  ||
                checkBit(err, 2)  || // Превышение диапазона
                checkBit(err, 3)  ||
                checkBit(err, 4)  ||
                checkBit(err, 5)  ||
                checkBit(err, 6)  ||
                checkBit(err, 7)  ||
                checkBit(err, 8)  ||
                checkBit(err, 9)  ||
                checkBit(err,10)  ||
                checkBit(err,11)  ||
                checkBit(err,12)  ||
                checkBit(err,13)  ||
                checkBit(err,14)  ||

                checkBit(err,23)  || // Температура
                checkBit(err,24)  || // Давление

                checkBit(err,27)  || // Падение человека

                checkBit(err,30);    // Неисправность сенсора
    }

    private boolean checkBit(int err, int bit) {
        return (err & (1 << bit)) != 0;
    }

    public static int lines_archive = 0;                          //строк в архиве
    public final static String PARAM_TASK = "task";
    public static String param_lat_lon = "none";                  //координаты полученные от сервиса - Широта

    public int fCnt = 0;                                    //номер пакета

    public int connToDev = 0;                      // попытки подключения
    public File arch_file;                         // полный путь файла архива

    private PG414 myPG;



    @Override
    protected void onDestroy()
    {
        Log.d("InfoPage","Меня сломали. Гасим сервисы");
        stopService(new Intent(this, GPS_service.class));
        handler.removeCallbacksAndMessages(null);
        serverHandler.removeCallbacksAndMessages(null);
        stop_dyn = true;
        PG414.removeInstance();

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
                    // Found best last known location: %s, l);
                    bestLocation = l;
                }
            } catch (SecurityException e) {
                Log.e("Location", "getlocation error");
            }


        }
        return bestLocation;
    }

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        stop_dyn = false;
        fCnt = 0;                                           //счетчик пакетов

        myPG = PG414.getInstance();

        setContentView(R.layout.activity_info_page);
        if (!Connect.offline && !Connect.hideMode)
        {
            myPG.clean_text();                      //Чистим текстовые переменные класса ПГ-414
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
        tzavod.setText(String.valueOf(myPG.zavod_number));

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
        if ((myPG.onoff2 & (1 << 10)) == 0) cbMoblity.setChecked(true);
        //***********************************************************************************************************************************
        //Отправка на сервер
//        send_message_to_server(Sending.eReg_info);

        //GPS
        tgps = findViewById(R.id.tgps);

        //Класс для приема сообщения из потока сервиса
        br = new BroadcastReceiver() {
            // действия при получении сообщений
            public void onReceive(Context context, Intent intent) {
                String coord = intent.getStringExtra(PARAM_TASK);
                if (coord != null && !coord.isEmpty())                             //Проверяем на длинну. Елси 0, то цель экого пакета просто разбудить активити
                    param_lat_lon = coord;
            }
        };
        // создаем фильтр для BroadcastReceiver
        IntentFilter intFilt = new IntentFilter(BROADCAST_ACTION);
        // регистрируем (включаем) BroadcastReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(br, intFilt, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(br, intFilt);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tgps.setText("Запрет на геолокацию");
            return;
        }

        //Выводим описатели газа
        gaz1.setText(myPG.gazType[0] + ", " + myPG.gazUnit[0]);
        gaz2.setText(myPG.gazType[1] + ", " + myPG.gazUnit[1]);
        gaz3.setText(myPG.gazType[2] + ", " + myPG.gazUnit[2]);
        gaz4.setText(myPG.gazType[3] + ", " + myPG.gazUnit[3]);

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

        startServerTimer();
        cbMoblity.setOnClickListener(view -> setCheck());

    }


    //Обновление параметров GPS
    @SuppressLint("SetTextI18n")
    private void UpdateLocation(Location location)
    {
        if (location!=null) {
            String lat;
            String longt;
            String result;
            lat = location.getLatitude() + "";
            longt = location.getLongitude() + "";
            if (lat.length() > 12) lat = lat.substring(0, 11);
            if (longt.length() > 12) longt = longt.substring(0, 11);
            tgps.setText(lat + ", \r" + longt);
            result = lat + ", " + longt;
            PG414.getInstance().gps = result;
            PG414.getInstance().lat = lat;
            PG414.getInstance().lng = longt;
        }
        else
        {
            tgps.setText(R.string.check_gps);

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

    boolean cnt_con;           //можно ли увеличивать счетсчик подключения и писать в файл
    boolean sending_archive;   //идет отправка архива
    static public boolean stop_dyn;     //отключить динамическое чтение

    /* Поток чтения динамических параметров */
    Runnable runnableDynTask = new Runnable() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void run() {
            try {
                while (true) {

                    if (stop_dyn) return;
                    //Если не пришла команда паузы чтения
                    if (!Connect.read_pause) {
                        if (Connect.State_pack == Connect.RX_pack.COMPLETE )
                        {
                            connToDev = 0;
                            connect_device = true;
                        }
                        Log.d("ПГ-414","Делаю запрос");
                        //Чтение статуса
                        myPG.reqDyn();
                        Connect.State_pack = Connect.RX_pack.DYNPARAM;
                        Thread.sleep(2300);
                        if (stop_dyn) return;
                        myPG.startRead();
                        //noinspection StatementWithEmptyBody
                        while (Connect.State_pack != Connect.RX_pack.COMPLETE && abort_counter < 5)
                            ;                      //ждем пока не прочтется

                        Log.d("InfoPage", abort_counter + " " +  Connect.State_pack);

                        if (abort_counter >= 5 && Connect.State_pack != Connect.RX_pack.COMPLETE)
                            Log.d("ПГ-414","Не могу достучаться " + connToDev);
                        else {
                            Log.d("ПГ-414", "Прочитал");
                            connToDev = 0;
                        }

                        abort_counter = 0;
                        cnt_con = true;

                    }
                }

            } catch (InterruptedException e) {
                Log.e("InfoPage", "Runnable error");
            }
        }
    };
        byte abort_counter = 0;

        ///Таймер для обновления графического интерфейса в зависимости от показаний
        private final Handler handler = new Handler(Looper.getMainLooper());

    private void fonValRefreshStart() {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                UIUpdate();
                Construct_JobInfo();
                abort_counter++;

                handler.postDelayed(this, 1500); // повторяем каждые 1.5 сек
            }
        }, 2000); // первый запуск через 2 сек
    }

    boolean firstAlarmPackage = false;

    private final Handler serverHandler = new Handler(Looper.getMainLooper());


    private void startServerTimer() {

        InfoPage currentPage = this;

        serverHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("SERVER_TIMER", "Соединение с устройством " + connect_device + " Строк в архиве: " + lines_archive + " Соединение с сервером: " + connect_server);



                if (!connect_device) {
                    Log.d("SEND", "Устройство отключено — отправка отменена");
                    return;
                }


                new Thread(() -> {
                    try {
                            while (lines_archive > 0) {
                                sending_archive = true;
                                Thread.sleep(60);
                                Send_Message = Sending.eArchive;
                                String line = readLastLine(FILE_NAME);
                                if (line != null && !line.isEmpty()) {
                                    sendingInfoToServ(currentPage, line);
                                    Log.d("ARCHIVE_READ", line);
                                    Log.d("ARCHIVE_READ",  String.valueOf(lines_archive));
                                } else {
                                    Log.w("ARCHIVE", "readLastLine вернул null или пустую строку");
                                }
                                lines_archive--;

                            }
                            sending_archive = false;
                    } catch (Exception e) {
                        Log.e("serverHandler", "Ошибка отправки архива");
                    }

                }).start();

                int err = myPG.getErrorBits();
                boolean currentAlarm = isAlarm(err);
                Log.d("SERVER_TIMER", "Current state: " + myPG.status);
                Log.d("SERVER_TIMER", "Alarm now: " + currentAlarm + ", Alarm before: " + lastAlarmState);
                firstAlarmPackage = false;

                if (currentAlarm && !lastAlarmState && lines_archive == 0) {                  //переход в аварийный режим
                    Log.d("ALARM", "ВХОД В АВАРИЙНЫЙ РЕЖИМ");
                    alarmMode = true;
                    firstAlarmPackage = true;

                    // немедленная отправка
                    new Thread(() -> {
                        if(connect_device){
                            Log.d("SEND", "Немедленная отправка");
                            Send_Message = Sending.eEvent;
                            sendingInfoToServ(currentPage, "");

                        }

                    }).start();
                }

                if (!currentAlarm && lastAlarmState) {                  //переход в штатный режим
                    Log.d("ALARM", "ВОЗВРАТ В ШТАТНЫЙ РЕЖИМ");
                    alarmMode = false;
                }

                lastAlarmState = currentAlarm;

                if(!firstAlarmPackage && lines_archive == 0){

                    new Thread(() -> {
                        Log.d("Sending", "eEvent");
                        send_message_to_server(Sending.eEvent);         //Шлем данные о событиях


                        sendingInfoToServ(currentPage, "");

                    }).start();
                }

                long delay = alarmMode ? 3000 : 30000;


                serverHandler.postDelayed(this, delay);

            }
        }, 3000);
    }


        //Функция для отправки данных на сервер. Регистрационнная инфомрация отправыляется один раз, динаическая - периодически, сразу после опроса устрйоства
         public void sendingInfoToServ(InfoPage page, String send_arch)
         {
             if (!connect_device) {
                 Log.d("SEND", "Нет устройства — выход");
                 return;
             }
             Log.d("На сервер","отправляю" + connect_server);
                 String params = "";
                 if (Send_Message == Sending.eEvent) {
                     Log.d("SEND", "eEvent");
                     if (PG414.getInstance().status.length() == 21)  PG414.getInstance().status = "OK";
                    //Если статус пустой то шлем OK;
                     if (PG414.getInstance().status.length() < 5) {
                         PG414.getInstance().status = "OK";
                     }


                     try {
                         JSONObject json = new JSONObject();

                         // -------- fCount --------
                         json.put("fCnt", fCnt);

                         // -------- tags --------
                         JSONObject tags = new JSONObject();
                         tags.put("ERdeviceType", "2");
                         tags.put("ERcodec", "pg-bluetooth");

                         json.put("tags", tags);

                         // -------- object --------
                         JSONObject object = new JSONObject();

                         object.put("zav_number", PG414.getInstance().zavod_number);

                         object.put("state", prepareState(PG414.getInstance().status));

                         // концентрация
                         object.put("gaz_type1", PG414.getInstance().gazType[0]);
                         String text1 = tconc1.getText().toString();
                         text1 = text1.replace(',', '.');
                         object.put("conc1", Double.parseDouble(text1));
                         object.put("measure_unit1", PG414.getInstance().gazUnit[0]);

                         object.put("gaz_type2", PG414.getInstance().gazType[1]);
                         String text2 = tconc2.getText().toString();
                         text2 = text2.replace(',', '.');
                         object.put("conc2", Double.parseDouble(text2));
                         object.put("measure_unit2", PG414.getInstance().gazUnit[1]);

                         object.put("gaz_type3", PG414.getInstance().gazType[2]);
                         String text3 = tconc3.getText().toString();
                         text3 = text3.replace(',', '.');
                         object.put("conc3", Double.parseDouble(text3));
                         object.put("measure_unit3", PG414.getInstance().gazUnit[2]);

                         object.put("gaz_type4", PG414.getInstance().gazType[3]);
                         String text4 = tconc4.getText().toString();
                         text4 = text4.replace(',', '.');
                         object.put("conc4", Double.parseDouble(text4));
                         object.put("measure_unit4", PG414.getInstance().gazUnit[3]);

                         object.put("battery_percent", PG414.getInstance().percent_charge);

                         json.put("object", object);
                         params = json.toString();

                         Log.d("JSON_SEND", json.toString(2));
                     } catch (JSONException e) {
                         Log.e("JSON", "Ошибка формирования пакета");
                     }
                 } else if (Send_Message == Sending.eArchive) {             //если ведется архиваня отправка данных
                     params = send_arch;

                     try{
                         JSONObject originalJson = new JSONObject(params);

                         JSONObject newJson = new JSONObject();

                         newJson.put("fCnt", fCnt);

                         Iterator<String> keys = originalJson.keys();
                         while (keys.hasNext()) {
                             String key = keys.next();
                             newJson.put(key, originalJson.get(key));
                         }

                         params = newJson.toString();
                         Log.d("JSON_SEND_ARCHIVE", newJson.toString(2));
                     }catch (org.json.JSONException e){
                         Log.e("JSON", "Ошибка формирования пакета архива");
                     }



                 }
                 byte[] dataz;
                 InputStream is = null;

                 HttpURLConnection conn;

                 boolean needToSave = false;

                 try {
                     URL url;
                     url = new URL(Connect.Server);
                     conn = (HttpURLConnection) url.openConnection();
                     conn.setRequestMethod("POST");
                     conn.setDoOutput(true);
                     conn.setDoInput(true);


                     conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                     conn.setRequestProperty("ERkey", "c0b96c2589a63163b8bc2bf94adc7cd6");
                     OutputStream os = conn.getOutputStream();
                     if (!connect_device) {
                         Log.d("SEND", "Отмена перед отправкой");
                         return;
                     }
                     dataz = params.getBytes(StandardCharsets.UTF_8);

                     String requestData = new String(dataz, StandardCharsets.UTF_8);
                     Log.e("HTTP_SEND", "=== HTTP ЗАПРОС ===");
                     Log.e("HTTP_SEND", "Время: " + System.currentTimeMillis());
                     Log.e("HTTP_SEND", "Тело: " + requestData);

                     os.write(dataz);
                     Log.d("SEND", "пакет отправлен");

                     dataz = null;
                     os.flush();
                     os.close();

                     int responseCode = conn.getResponseCode();

                     if(responseCode == 200){
                         ByteArrayOutputStream baos = new ByteArrayOutputStream();
                         is = conn.getInputStream();

                         byte[] buffer = new byte[8192];
                         int bytesRead;
                         while ((bytesRead = is.read(buffer)) != -1) {
                             baos.write(buffer, 0, bytesRead);
                         }dataz = baos.toByteArray();

                         String responseStr = new String(dataz, StandardCharsets.UTF_8);
                         JSONObject respJson = new JSONObject(responseStr);
                         boolean success = respJson.getBoolean("success");

                         if(success){
                             connect_server = true;
                             Log.d("SEND", "Успешно отправлено");
                         }else{
                             connect_server = false;
                             Log.w("SEND", "Данные не приняты");
                             needToSave = true;
                         }

                         fCnt++;
                     }else{
                         connect_server = false;
                         Log.e("SEND", "HTTP ошибка: " + responseCode);
                         Log.w("SEND", "Данные не приняты");
                         needToSave = true;
                     }

                     Log.d("SERVER_URL", Connect.Server);
                     Log.d("HTTP_CODE", String.valueOf(responseCode));
                     Log.d("SERVER_RESPONSE", new String(dataz));

                 } catch
                         (Exception ex) {
                     connect_server = false;
                     needToSave = true;
                     Log.e("DEBUG", "Error sending data", ex);
                 } finally {
                     if (needToSave) {
                         String packetToArchive = removeFcntFromJson(params);
                         page.writeToFile(packetToArchive);                                   //пишем в файл и увеличиваем количество линий
                         Log.d("JSON_ARCHIVE_SAVE", packetToArchive);
                     }

                     try {

                         if (is != null)
                             is.close();
                     } catch (Exception ex) {
                         Log.e("DEBUG", "IS close error", ex);
                     }

                 }

         }

    private static String removeFcntFromJson(String jsonWithFcnt) {
        try {
            JSONObject obj = new JSONObject(jsonWithFcnt);
            obj.remove("fCnt");  // удаляем поле fCnt
            return obj.toString();
        } catch (JSONException e) {
            return jsonWithFcnt;  // если ошибка, возвращаем как есть
        }
    }

    private static String prepareState(String state) {

        String[] lines = state.split("\n");

        // уникальные строки
        LinkedHashSet<String> unique = new LinkedHashSet<>(Arrays.asList(lines));

        StringBuilder result = new StringBuilder();

        int MAX_LINES = 3;
        int count = 0;

        for (String line : unique) {

            line = line.trim();

            // пропускаем мусор
            if (line.isEmpty() || line.equals("НЕ ИСПОЛЬЗУЕТСЯ"))
                continue;

            result.append(line).append("\n");

            count++;
            if (count >= MAX_LINES) break;
        }

        return result.toString().trim();
    }


         public void send_message_to_server(Sending parametr)
         {
             Send_Message = parametr;
         }


         public void backToConnect(View v){
             onBackPressed();
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
            input.setText(Connect.Server);
            input.setTextColor(Color.rgb(232, 228, 211));
            alert.setView(input);


            alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                String resulty = input.getText().toString();
                //Заполняем поле
                Connect.Server = resulty;
                Connect.editor.putString(Connect.SERVER_SETTING, resulty);
                Connect.editor.commit();
            });

            alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                // Canceled.
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

            alert.setPositiveButton("Ok", (dialog, whichButton) -> clearFile());

            alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                // Canceled.
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

    @SuppressLint("SetTextI18n")
    public void UIUpdate()
    {
            if (!Connect.read_pause) {

                //Применяем дискретность газов
                NumberFormat[] nf;
                nf = new NumberFormat[4];
                for (byte j =0; j < 4; j++)
                {
                    nf[j] = NumberFormat.getInstance();

                    int digits = Math.abs(myPG.gazDiskret[j]);

                    nf[j].setMaximumFractionDigits(digits);
                    nf[j].setMinimumFractionDigits(digits);
                    nf[j].setGroupingUsed(false);
                }
                //В данном коде выводим форматированную     концентрацию                 при этом учитываем          дискретность
                tconc1.setText(nf[0].format(myPG.conc1/(float)(myPG.gazDelitel[abs(myPG.gazDiskret[0])])));
                tconc2.setText(nf[1].format(myPG.conc2/(float)(myPG.gazDelitel[abs(myPG.gazDiskret[1])])));
                tconc3.setText(nf[2].format(myPG.conc3/(float)(myPG.gazDelitel[abs(myPG.gazDiskret[2])])));
                tconc4.setText(nf[3].format(myPG.conc4/(float)(myPG.gazDelitel[abs(myPG.gazDiskret[3])])));
                //Процент заряда батареи
                charge.setText(getResources().getString(R.string.Charge) + myPG.percent_charge + "%");
                //Статус
                String tState = myPG.Make_State();
                if (Connect.mConnectionState != STATE_CONNECTED) {
                    tState = getString(R.string.con_lost);
                    myPG.status = tState;
                }
                tstatus.setText(tState);
                if (!connect_server) {                                 //Если не подключен к серверу
                    errcon.setText(R.string.Server_aerror);
                    errcon.setVisibility(View.VISIBLE);
                    disconnect.setVisibility(View.VISIBLE);
                }
                else {
                    errcon.setVisibility(View.INVISIBLE);
                    disconnect.setVisibility(View.INVISIBLE);
                }


                connToDev++;                                            //увеличиываем счетчик попыток подключения. он сбрасывается при успешном чтении
                if (connToDev > 222) connToDev = 11;
                Log.d("UIUpdate", String.valueOf(connToDev));
                if (connToDev > 10 && !sending_archive)                  //если попыток было 5 а информации от ПГ-414 не поступала выводим сообщение и запускаем переподключение
                {
                    connect_device = false;
                    tstatus.setText(R.string.err_con_dev);            //КОСТЫЛЬ. на планшетах почему то постоянно пишет хоть связь и есть
                    try{
                        myPG.mBluetoothGatt.connect();
                    } catch (SecurityException e) {
                        Log.e("InfoPage", "connect error");
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

            /*Обновляем информацию по GPS*/
            if (!"none".equals(param_lat_lon)) {
                tgps.setText(param_lat_lon);
                myPG.gps = param_lat_lon;
            }

            if (cnt_con)
            {
            //Работа с файлами архива. В него пишем если нет соединения с сервером, но есть связь с ПГ. Эти данные регистрируем в отдельный файл
             if (!connect_server && connect_device) {
                 try {
                     JSONObject json = new JSONObject();

                     // -------- tags --------
                     JSONObject tags = new JSONObject();
                     tags.put("ERdeviceType", "2");
                     tags.put("ERcodec", "pg-bluetooth");
                     json.put("tags", tags);

                     // -------- object --------
                     JSONObject object = new JSONObject();
                     object.put("zav_number", myPG.zavod_number);

                     JSONObject gps = new JSONObject();

                     object.put("state", "Archive: " + myPG.status);

                     object.put("gaz_type1", myPG.gazType[0]);
                     object.put("conc1", Double.parseDouble(tconc1.getText().toString().replace(',', '.')));
                     object.put("measure_unit1", myPG.gazUnit[0]);

                     object.put("gaz_type2", myPG.gazType[1]);
                     object.put("conc2", Double.parseDouble(tconc2.getText().toString().replace(',', '.')));
                     object.put("measure_unit2", myPG.gazUnit[1]);

                     object.put("gaz_type3", myPG.gazType[2]);
                     object.put("conc3", Double.parseDouble(tconc3.getText().toString().replace(',', '.')));
                     object.put("measure_unit3", myPG.gazUnit[2]);

                     object.put("gaz_type4", myPG.gazType[3]);
                     object.put("conc4", Double.parseDouble(tconc4.getText().toString().replace(',', '.')));
                     object.put("measure_unit4", myPG.gazUnit[3]);

                     object.put("battery_percent", myPG.percent_charge);

                     json.put("object", object);

                     // Сохраняем JSON архива без fCnt
                     writeToFile(json.toString());                                   //пишем в файл и увеличиваем количество линий
                     Log.d("JSON_ARCHIVE_SAVE", json.toString(2));
                 } catch (JSONException e) {
                     Log.e("JSON", "Ошибка формирования пакета архива");
                 }
                  }
                cnt_con = false;
            }

    }





    //Запись данных в файл.
    protected void writeToFile(String text)
    {
        text += '\n';
        try (FileOutputStream fos = openFileOutput(FILE_NAME, MODE_APPEND)){
            fos.write(text.getBytes(StandardCharsets.UTF_8));
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
            byte[] buffer;
            try (FileInputStream fin = openFileInput(FILE_NAME)) {
                buffer = new byte[fin.available()];
                // считаем файл в буфер
                fin.read(buffer, 0, fin.available());
            }
            int linesCount = 0;
            for (byte b : buffer) {
                if (b == '\n')
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
    @SuppressWarnings("SameParameterValue")
    private String readLastLine(String fileName) {
        List<String> lines = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(openFileInput(fileName), StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();

            if (lines.isEmpty()) return null;

            String lastLine = lines.remove(lines.size() - 1);

            // перезаписываем файл без последней строки
            FileOutputStream fos = openFileOutput(fileName, MODE_PRIVATE);
            for (String l : lines) {
                fos.write((l + "\n").getBytes(StandardCharsets.UTF_8));
            }
            fos.close();

            return lastLine;

        } catch (Exception e) {
            Log.e("readLastLine", "ошибка чтения файла");
        }

        return null;
    }


    //Очистка файла
    protected void clearFile()
    {
     try(FileOutputStream fos = openFileOutput(FILE_NAME, MODE_PRIVATE)) {
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
        if(!Connect.offline && !Connect.hideMode){
            Connect.read_pause = true;
            Connect.State_pack = Connect.RX_pack.PARAMS;
            if (!cbMoblity.isChecked())
            {
                myPG.setParamOnOff((short) 0, (short)(1 << 10),'+');
            }
            else
            {
                myPG.setParamOnOff((short)0, (short)(1 << 10),'-');
            }
        }


    }

//-----------------------------------------------------------------------------------------------
}



