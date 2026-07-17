package com.eriskip.ble_pg414;

import android.Manifest;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.lang.Math.abs;

import com.eriskip.ble_pg414.library.PG414;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;


public class InfoPage extends AppCompatActivity {
    private static final String FILE_NAME = "archive.dat";              //имя файла архива

    public final static String BROADCAST_ACTION = "com.eriskip.ble_pg414";
    BroadcastReceiver br;   //слушатель параметров геолокациии от нашего сервиса

    public enum Sending {eEvent, eArchive, eNone}

    public static Sending Send_Message = Sending.eEvent;                                               //переменная, которая отвечает за то какой сейчас пакет отправляется на сервер

    //>>>>>  UI ------------------------------------------------------------------------------------
    //>>>>>  UI ------------------------------------------------------------------------------------
    public TextView tconc1, tconc2, tconc3, tconc4, tzavod, gaz1, gaz2, gaz3, gaz4, tgps, tstatus, //текстовые поля
            charge, temp, errcon, arch_cnt;
    ImageView disconnect, arch_alert;
    LinearLayout gazLayout1;
    LinearLayout gazLayout2;
    LinearLayout gazLayout3;
    LinearLayout gazLayout4;

    Button btnReconnect;
    ImageView btnRefresh;
    FrameLayout frameRefresh;
    //----------------------------------------------------------------------------------------------

    CheckBox cbMoblity;             //детекция неподвижности
    CheckBox cbServer;              //отправка на сервер
    private SharedPreferences prefs;

    private View statusDot;


    public volatile boolean connect_device = true;                //соединение с устройством
    public volatile boolean connect_server = false;                             //соединение с сервером
    private boolean alarmMode = false;                              // аварийный режим, сработка порога
    private boolean lastAlarmState = false;                         // штатный режим
    private boolean firstAlarmPackage = false;                     //отправка первого аварийного пакета

    private boolean isAlarm(int err) {
        if (err == 0) return false;

        // проверяем только нужные биты
        return checkBit(err, 0) || // Порог 1 - Сенсор 1
                checkBit(err, 1) || checkBit(err, 2) || // Превышение диапазона
                checkBit(err, 3) || checkBit(err, 4) || checkBit(err, 5) || checkBit(err, 6) || checkBit(err, 7) || checkBit(err, 8) || checkBit(err, 9) || checkBit(err, 10) || checkBit(err, 11) || checkBit(err, 12) || checkBit(err, 13) || checkBit(err, 14) ||

                checkBit(err, 23) || // Температура
                checkBit(err, 24) || // Давление

                checkBit(err, 27) || // Падение человека

                checkBit(err, 30);    // Неисправность сенсора
    }

    private boolean isSensorAlarm(int err){
        if (err == 0) return false;

        return checkBit(err, 0) ||
                checkBit(err, 1) || checkBit(err, 2) ||
                checkBit(err, 3) || checkBit(err, 4) ||
                checkBit(err, 5) || checkBit(err, 6) ||
                checkBit(err, 7) || checkBit(err, 8) ||
                checkBit(err, 9) || checkBit(err, 10) ||
                checkBit(err, 11);
    }

    private boolean isError(int err) {
        if (err == 0) return false;

        return checkBit(err, 18) ||      //Ошибка связи с АЦП
                checkBit(err, 19) ||     //Ошибка связи с АЦП2
                checkBit(err, 20) ||     //Ошибка связи с ЛМП1
                checkBit(err, 21) ||     //Ошибка связи с ЛМП2
                checkBit(err, 22) ||    //Ошибка связи с ЛМП3
                checkBit(err, 25) ||    //Чистая флешка архива или CRC flash
                checkBit(err, 28) ||    //Ошибка платы питания, пин PWGD
                checkBit(err, 29) ||    //Ошибка акселерометра
                checkBit(err, 30) ||    //Неисправность сенсора
                checkBit(err, 31);      //Ошибка расширителя

    }

    public String[] errorsToColorRUS = {
            "Ошибка связи с АЦП",
            "Ошибка связи с АЦП2",
            "Ошибка связи с ЛМП1",
            "Ошибка связи с ЛМП2",
            "Ошибка связи с ЛМП3",
            "Ошибка радиомодуля",
            "Ошибка платы питания, пин PWGD",
            "Ошибка акселерометра",
            "Неисправность сенсора",
            "Ошибка расширителя"
    };

    public String[] errorsToColorEN = {
            "DAC communication error",
            "DAC2 communication error",
            "LMP1 communication error",
            "LMP2 communication error",
            "LMP3 communication error",
            "Radio module error",
            "Power board error, pin PWGD",
            "Accelerometer error",
            "Sensor malfunction",
            "Extender error"
    };


    private boolean checkBit(int err, int bit) {
        return (err & (1 << bit)) != 0;
    }

    public int lines_archive = 0;                          //строк в архиве
    public final static String PARAM_TASK = "task";
    public static String param_lat_lon = "none";                  //координаты полученные от сервиса - Широта

    public String rssi;

    public int fCnt = 0;                                    //номер пакета

    public File arch_file;                         // полный путь файла архива
    String params = "";                            // данные для отправки

    private PG414 myPG;

    BLE_manager ble_manager;

    @Override
    protected void onDestroy() {
        Log.d("InfoPage", "onDestroy");
        stopService(new Intent(this, GPS_service.class));
        ble_manager.needToConnect = false;
        ble_manager.disconnect();
        handler.removeCallbacksAndMessages(null);
        if (serverHandler != null) {
            serverHandler.removeCallbacksAndMessages(null);
        }
        archiveHandler.removeCallbacksAndMessages(null);
        btnReconnect.setVisibility(INVISIBLE);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig){

        super.onConfigurationChanged(newConfig);

        Log.d("onConfigurationChanged", " onConfigurationChanged");
    }
    LocationManager mLocationManager;

    private Location getLastKnownLocation() {
        mLocationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            try {
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

    NotificationHelper notHelper;                //объект класса уведомлений
    PermissionHelper permHelper;                //объект класса разрешений

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ble_manager = BLE_manager.getInstance(this);
        notHelper = new NotificationHelper(this);
        permHelper = new PermissionHelper(this);

        fCnt = 0;                                           //счетчик пакетов

        myPG = PG414.getInstance();

        setContentView(R.layout.activity_info_page);
        if (!Connect.offline && !Connect.hideMode) {
            myPG.clean_text();                      //Чистим текстовые переменные класса ПГ-414
        }

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
        temp = findViewById(R.id.temp);
        arch_cnt = findViewById(R.id.arch_cnt);

        arch_alert = findViewById(R.id.arch_alert);         //иконка алерт у архива
        disconnect = findViewById(R.id.disconnect);         //картинка дисконнекта

        gazLayout1 = findViewById(R.id.gazLayout1);
        gazLayout2 = findViewById(R.id.gazLayout2);
        gazLayout3 = findViewById(R.id.gazLayout3);
        gazLayout4 = findViewById(R.id.gazLayout4);

        btnReconnect = findViewById(R.id.btnReconnect);            //кнопка переподключения
        btnRefresh = findViewById(R.id.imgRefresh);                 //кнопка "Обновить данные об устройстве"
        frameRefresh = findViewById(R.id.frameRefresh);

        cbMoblity = findViewById(R.id.cbMoblity);
        if ((myPG.onoff2 & (1 << 10)) == 0) cbMoblity.setChecked(true);

        cbServer = findViewById(R.id.serverCbx);
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean isChecked = prefs.getBoolean("moblity_enabled", false);     //получаем состояние чекбокса отправки на сервер
        cbServer.setChecked(isChecked);

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
        gaz1.setText(isNoneInGaz(0) ? "Отключено" : myPG.gazType[0] + ", " + myPG.gazUnit[0]);
        gaz2.setText(isNoneInGaz(1) ? "Отключено" : myPG.gazType[1] + ", " + myPG.gazUnit[1]);
        gaz3.setText(isNoneInGaz(2) ? "Отключено" : myPG.gazType[2] + ", " + myPG.gazUnit[2]);
        gaz4.setText(isNoneInGaz(3) ? "Отключено" : myPG.gazType[3] + ", " + myPG.gazUnit[3]);


        //Статус
        tstatus = findViewById(R.id.tstate);
        arch_file = context.getFileStreamPath(FILE_NAME);

        //Проверяем число неотправленных пакетов
        readCntLines();
        if (lines_archive > 0) {
            //Делаем видимыми картинку
            arch_cnt.setVisibility(VISIBLE);
            arch_alert.setVisibility(VISIBLE);
            arch_cnt.setText("" + lines_archive);
        } else {
            arch_cnt.setVisibility(INVISIBLE);
            arch_alert.setVisibility(INVISIBLE);
        }

        //Делаем так что сервис будет запускаться в любом случае, а не только при сне
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, GPS_service.class));
        } else {
            startService(new Intent(this, GPS_service.class));
        }
        //Обновляем GPS
//        Location lastKnownLocation = getLastKnownLocation();
//        UpdateLocation(lastKnownLocation);
        //----------------------------------
        //потоки


        if (!Connect.offline) {
            fonValRefreshStart();
            if (cbServer.isChecked()) {
                startServerTimer();
            }
            saveArchiveIfNeeded();
        }


        cbMoblity.setOnClickListener(view -> {
            if (!Connect.offline) setCheck();
        });
        cbServer.setOnCheckedChangeListener((buttonView, isChecked1) -> {
            // Сохраняем состояние при изменении
            prefs.edit().putBoolean("moblity_enabled", isChecked1).apply();
            Log.d("Settings", "Отправка на сервер: " + isChecked1);
            enableSendingToServ();
        });

        frameRefresh.setOnClickListener(view -> {
            refreshData();
        });

        setLongClickDescription(frameRefresh);
        printToast(arch_alert);

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        backToConnect(null);
                    }
                });

        ble_manager.setBLE_listener((state, msg) -> {
            switch (state) {
                case DISCONNECTING:
                    connect_device = false;
                    Log.d("InfoPage", "Показваю уведомление об отключении");
                    if(ble_manager.needToConnect){
                        notHelper.showDisconnectNotification(this);
                    }
                    tstatus.setText(msg);
                    Log.d("BLE_listener", "DISCONNECTING нет соединения с устройством");
                    handler.removeCallbacksAndMessages(null);
                    if (serverHandler != null) {
                        serverHandler.removeCallbacksAndMessages(null);
                    }
                    archiveHandler.removeCallbacksAndMessages(null);
                    btnReconnect.setVisibility(INVISIBLE);
                    break;
                case RECONNECTING:
                    tstatus.setText(msg);
                    myPG.status = tState;
                    Log.d("BLE_listener", "RECONNECTING запускаю переподключение");

                    break;
                case ERROR:
                    Log.d("BLE_listener", "ERROR поакзываю сообщение об ошибке");
                    tstatus.setText(msg);
                    if (ble_manager.btEnabled) {
                        btnReconnect.setVisibility(VISIBLE);
                    }
                    handler.removeCallbacksAndMessages(null);
                    if (serverHandler != null) {
                        serverHandler.removeCallbacksAndMessages(null);
                    }
                    archiveHandler.removeCallbacksAndMessages(null);
                    break;
                case CON_RESTORED:
                    Log.d("BLE_listener", "CON_RESTORED соединение восстановлено");
                    tstatus.setText(msg);
                    connect_device = true;
                    //запускаем потоки
                    fonValRefreshStart();
                    if (cbServer.isChecked()) {
                        startServerTimer();
                    }
                    saveArchiveIfNeeded();
                    break;
                case PACKET_PARSED:
                    Log.d("BLE_listener", "PACKET_PARSED динамика считана");
                    if (ble_manager.showMsgForUpdate) {
                        ble_manager.showMsgForUpdate = false;
                        gaz1.setText(isNoneInGaz(0) ? "Отключено" : myPG.gazType[0] + ", " + myPG.gazUnit[0]);
                        gaz2.setText(isNoneInGaz(1) ? "Отключено" : myPG.gazType[1] + ", " + myPG.gazUnit[1]);
                        gaz3.setText(isNoneInGaz(2) ? "Отключено" : myPG.gazType[2] + ", " + myPG.gazUnit[2]);
                        gaz4.setText(isNoneInGaz(3) ? "Отключено" : myPG.gazType[3] + ", " + myPG.gazUnit[3]);
                        stopRefreshAnimation();
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    }
                    blinkDot();
                    break;
                case READ_RSSI:
                    Log.d("BLE_listener", "READ_RSSI сигнал считан");
                    rssi = msg;
            }
        });

    }

    private void setLongClickDescription(View view) {
        view.setOnLongClickListener(v -> {
            CharSequence description = v.getContentDescription();

            if (description != null && description.length() > 0) {
                Toast.makeText(
                        v.getContext(),
                        description,
                        Toast.LENGTH_SHORT
                ).show();
            }

            return true;
        });
    }

    private boolean isNoneInGaz(int x) {
        return (myPG.gazType[x] + ", " + myPG.gazUnit[x]).contains("none");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem item = menu.findItem(R.id.menu_status);

        if (item != null) {
            View actionView = item.getActionView();

            if (actionView != null) {
                statusDot = actionView.findViewById(R.id.statusDot);
            }
        }

        return true;
    }

    private AnimatorSet blinkAnimator;

    private void blinkDot() {
        if (statusDot == null) return;

        if (blinkAnimator != null) {
            blinkAnimator.cancel();
        }

        statusDot.setAlpha(0f);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(450);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(450);

        blinkAnimator = new AnimatorSet();
        blinkAnimator.playSequentially(fadeIn, fadeOut);
        blinkAnimator.start();
    }

    //Обновление параметров GPS
    @SuppressLint("SetTextI18n")
    private void UpdateLocation(Location location) {
        if (location != null) {
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
        } else {
            tgps.setText(R.string.check_gps);

        }
    }

    @Override
    protected void onResume() {
        //При восстановлении работы вновь запускаем GPS & NEY провайдеров для определения координат
        super.onResume();
        /*При выходе из сна выставляем режим лучшего доступного провайдора */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, GPS_service.class).putExtra("mode", 3));
        }
        Log.d("ON RESUME RUN", "Я проснулся");
    }

    @Override
    protected void onPause() {
        Log.d("ON PAUSE RUN", "Я пошел спать");
        /*При переходе в сон выставляем режим провайдора location NETWORK*/
        if (ble_manager.openPage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, GPS_service.class).putExtra("mode", 2));
            }
        }
        //При остановке переводим GPS позиционирование на другой сервис
        super.onPause();
    }

    boolean sending_archive;   //идет отправка архива


    byte abort_counter = 0;

    /// Таймер для обновления графического интерфейса в зависимости от показаний
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
        }, 500); // первый запуск через 2 сек
    }

    private Handler serverHandler;


    private void startServerTimer() {

        if (serverHandler != null) {
            serverHandler.removeCallbacksAndMessages(null);
        }

        serverHandler = new Handler(Looper.getMainLooper());

        InfoPage currentPage = this;

        serverHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("SERVER_TIMER", "Соединение с устройством " + connect_device + " Строк в архиве: " + lines_archive + " Соединение с сервером: " + connect_server);


                if (!connect_device) {
                    Log.d("SEND", "Устройство отключено — отправка отменена");
                    return;
                }

                if (connect_server) {
                    if (lines_archive == 0) {
                        sending_archive = false;
                    }

                    if (lines_archive > 0) {
                        sending_archive = true;

                        new Thread(() -> {
                            try {
                                Send_Message = Sending.eArchive;
                                String line = readFirstLine(FILE_NAME);

                                if (line != null && !line.isEmpty()) {
                                    sendingInfoToServ(currentPage, line);
                                    Log.d("ARCHIVE_READ", "Отправлено: " + line);
                                    Log.d("ARCHIVE_READ", "Осталось в архиве: " + (lines_archive - 1));
                                    lines_archive--;
                                } else {
                                    Log.w("ARCHIVE", "readLastLine вернул null или пустую строку");
                                    lines_archive = 0; // сбрасываем, чтобы избежать бесконечного цикла
                                }

                            } catch (Exception e) {
                                Log.e("serverHandler", "Ошибка отправки архива", e);
                            }
                        }).start();
                    }

                    int err = myPG.getErrorBits();
                    boolean currentAlarm = isAlarm(err);
                    firstAlarmPackage = false;
                    Log.d("SERVER_TIMER", "Current state: " + myPG.status);
                    Log.d("SERVER_TIMER", "Alarm now: " + currentAlarm + ", Alarm before: " + lastAlarmState);

                    if (currentAlarm && !lastAlarmState && lines_archive == 0) {                  //переход в аварийный режим
                        Log.d("ALARM", "ВХОД В АВАРИЙНЫЙ РЕЖИМ");
                        alarmMode = true;
                        firstAlarmPackage = true;
                        // немедленная отправка
                        new Thread(() -> {
                            if (connect_device) {
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

                    if (!firstAlarmPackage && !sending_archive) {

                        new Thread(() -> {
                            Log.d("Sending", "eEvent");
                            Send_Message = Sending.eEvent;         //Шлем данные о событиях


                            sendingInfoToServ(currentPage, "");

                        }).start();
                    }
                } else {
                    new Thread(() -> {
                        Log.d("Sending", "eNone");
                        Send_Message = Sending.eNone;         //Отправляем тестовый пакет для установки соединения


                        sendingInfoToServ(currentPage, "");
                    }).start();
                }

                long delay;
                if (connect_server) {
                    if (lines_archive > 0) {
                        delay = 1000;
                    } else if (alarmMode) {
                        delay = 3000;
                    } else {
                        delay = 30000;
                    }

                } else {
                    delay = 5000;
                }
                Log.d("serverHandler", String.valueOf(delay));


                serverHandler.postDelayed(this, delay);

            }
        }, 3000);
    }


    //Функция для отправки данных на сервер. Регистрационнная инфомрация отправыляется один раз, динаическая - периодически, сразу после опроса устрйоства
    public void sendingInfoToServ(InfoPage page, String send_arch) {
        if (!connect_device) {
            Log.d("SEND", "Нет устройства — выход");
            return;
        }
        Log.d("На сервер", "отправляю" + connect_server);

        if (Send_Message == Sending.eEvent) {
            Log.d("SEND", "eEvent");
            if (PG414.getInstance().status.length() == 21) PG414.getInstance().status = "OK";
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

                JSONObject coord = new JSONObject();
                coord.put("lat", lat);
                coord.put("lng", lng);
                object.put("coord", coord);

                object.put("rssi", rssi);

                object.put("battery_percent", PG414.getInstance().percent_charge);

                json.put("object", object);
                params = json.toString();

                Log.d("JSON_SEND", json.toString(2));
            } catch (JSONException e) {
                Log.e("JSON", "Ошибка формирования пакета");
            }
        } else if (Send_Message == Sending.eArchive) {             //если ведется архиваня отправка данных
            params = send_arch;

            try {
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
            } catch (org.json.JSONException e) {
                Log.e("JSON", "Ошибка формирования пакета архива с номером");
                return;
            }


        } else {
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

                json.put("object", object);
                params = json.toString();

                Log.d("JSON_SEND", json.toString(2));

            } catch (org.json.JSONException e) {
                Log.e("JSON", "Ошибка формирования тестового пакета");
                return;
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

            if (responseCode == 200) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                is = conn.getInputStream();

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                dataz = baos.toByteArray();

                String responseStr = new String(dataz, StandardCharsets.UTF_8);
                JSONObject respJson = new JSONObject(responseStr);
                boolean success = respJson.getBoolean("success");

                if (success) {
                    connect_server = true;
                    Log.d("SEND", "Успешно отправлено");
                } else {
                    connect_server = false;
                    Log.w("SEND", "Данные не приняты");
                    needToSave = true;
                }

                fCnt++;
            } else {
                connect_server = false;
                Log.e("SEND", "HTTP ошибка: " + responseCode);
                Log.w("SEND", "Данные не приняты");
                needToSave = true;
            }

            Log.d("SERVER_URL", Connect.Server);
            Log.d("HTTP_CODE", String.valueOf(responseCode));
            Log.d("SERVER_RESPONSE", new String(dataz));

        } catch (Exception ex) {
            connect_server = false;
            needToSave = true;
            Log.e("DEBUG", "Error sending data", ex);
        } finally {
            if (Send_Message != Sending.eNone && needToSave) {
                String packetToArchive = removeFcntFromJson(params);
                page.writeToFile(packetToArchive);                                   //пишем в файл и увеличиваем количество линий
                Log.d("JSON_ARCHIVE_SAVE", packetToArchive);
            }

            try {

                if (is != null) is.close();
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
            if (line.isEmpty() || line.equals("НЕ ИСПОЛЬЗУЕТСЯ")) continue;

            result.append(line).append("\n");

            count++;
            if (count >= MAX_LINES) break;
        }

        return result.toString().trim();
    }

    public void backToConnect(View v) {
        Log.d("InfoPage", "Меня сломали. Гасим сервисы");
        stopService(new Intent(this, GPS_service.class));
        ble_manager.openPage = false;

        handler.removeCallbacksAndMessages(null);
        if (serverHandler != null) {
            serverHandler.removeCallbacksAndMessages(null);
        }
        archiveHandler.removeCallbacksAndMessages(null);
        PG414.removeInstance();
        finish();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void reconnect(View v) {
        Log.d("InfoPage", "пользователь нажал Подключиться");
        if (!permHelper.checkBTPermissions()) {
            permHelper.requestBTPermissions(this);
            return;
        }
        ble_manager.reconnect();
    }

    public void refreshData() {
        Log.d("InfoPage", "пользователь нажал Обновить данные о приборе");
        if (!Connect.offline && !Connect.hideMode) {
            startRefreshAnimation();
            ble_manager.isRefreshPressed = true;
        }
    }

    private ObjectAnimator refreshAnimator;

    private void startRefreshAnimation() {

        if (refreshAnimator != null && refreshAnimator.isRunning()) {
            return;
        }

        refreshAnimator = ObjectAnimator.ofFloat(
                btnRefresh,
                View.ROTATION,
                0f,
                360f
        );

        refreshAnimator.setDuration(800); // один оборот за 0.8 сек
        refreshAnimator.setInterpolator(new LinearInterpolator());
        refreshAnimator.setRepeatCount(ValueAnimator.INFINITE);
        refreshAnimator.setRepeatMode(ValueAnimator.RESTART);

        refreshAnimator.start();
    }

    private void stopRefreshAnimation() {

        if (refreshAnimator != null) {
            refreshAnimator.cancel();
            btnRefresh.setRotation(0f);
            refreshAnimator = null;
        }
    }



    //Ввод адреса сервера
    public void enterServer(View v) {
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

        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //*******************TEST ZONE***************************
    //Вывод окна для запроса очистки файла
    public void clearBtn() {
        try {
            AlertDialog.Builder alert = new AlertDialog.Builder(InfoPage.this, R.style.AlertDialogCustom);

            alert.setTitle(getString(R.string.Alert_title));
            alert.setMessage(getString(R.string.Clear_file));

            alert.setPositiveButton("Ok", (dialog, whichButton) -> clearFile());

            alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                // Canceled.
            });
            alert.show();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //*******************************************************
    public void Construct_JobInfo() {

    }

    String tState;
    String stateSensor;

    String lat;
    String lng;

    @SuppressLint("SetTextI18n")
    public void UIUpdate() {
        Log.d("UIUpdate", "UIUpdate");
        //Применяем дискретность газов
        NumberFormat[] nf;
        nf = new NumberFormat[4];
        for (byte j = 0; j < 4; j++) {
            nf[j] = NumberFormat.getInstance();

            int digits = Math.abs(myPG.gazDiskret[j]);

            nf[j].setMaximumFractionDigits(digits);
            nf[j].setMinimumFractionDigits(digits);
            nf[j].setGroupingUsed(false);
        }
        //В данном коде выводим форматированную     концентрацию                 при этом учитываем          дискретность
        tconc1.setText(nf[0].format(myPG.conc1 / (float) (myPG.gazDelitel[abs(myPG.gazDiskret[0])])));
        tconc2.setText(nf[1].format(myPG.conc2 / (float) (myPG.gazDelitel[abs(myPG.gazDiskret[1])])));
        tconc3.setText(nf[2].format(myPG.conc3 / (float) (myPG.gazDelitel[abs(myPG.gazDiskret[2])])));
        tconc4.setText(nf[3].format(myPG.conc4 / (float) (myPG.gazDelitel[abs(myPG.gazDiskret[3])])));
        //Процент заряда батареи
        charge.setText(getResources().getString(R.string.Charge) + " " + myPG.percent_charge + "%");
        //Температура
        temp.setText(getResources().getString(R.string.Temperature) + " " + myPG.temp + "°C");

        if (!connect_server && cbServer.isChecked()) {                                 //Если не подключен к серверу
            errcon.setText(R.string.Server_aerror);
            errcon.setVisibility(VISIBLE);
            disconnect.setVisibility(VISIBLE);
        } else {
            errcon.setVisibility(INVISIBLE);
            disconnect.setVisibility(GONE);
        }


        /*Обновляем информацию по GPS*/
        if (!"none".equals(param_lat_lon)) {
            tgps.setText(param_lat_lon);
            myPG.gps = param_lat_lon;
            String[] parts = param_lat_lon.split(", ");
            lat = parts[0];
            lng = parts[1];
        }

        //Статус

        tState = myPG.Make_State();
        Log.d("UIUpdate", "tState = '" + tState + "'");

        int err = myPG.getErrorBits();
        String[] lines = tState.split(",\n");

        if(isError(err)){
            Log.d("UIUpdate", "Errors in state");
            Set<String> errorSet = new HashSet<>(Arrays.asList(myPG.localeRus ? errorsToColorRUS : errorsToColorEN));

            SpannableStringBuilder builder = new SpannableStringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                int start = builder.length();
                builder.append(line);
                int end = builder.length();

                // Если это ошибка - красим в красный
                if (errorSet.contains(line)) {
                    builder.setSpan(
                            new ForegroundColorSpan(Color.RED),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }

                // Добавляем перенос строки между элементами
                if (i < lines.length - 1) {
                    builder.append("\n");
                }
            }

            tstatus.setText(builder);
        }else{
            tstatus.setText(tState);
        }

        if(isSensorAlarm(err)){
            Log.d("UIUpdate", "Sensor Alarm");
            for(String line : lines){
                for(int i = 0; i < 12; i++){
                    if (myPG.localeRus) {
                        if(line.equals(myPG.array_of_Errors_RUS[i])){
                            String[] parts = line.split(" - ");
                            stateSensor = parts[1];
                        }
                    }else{
                        if(line.equals(myPG.array_of_Errors_EN[i])){
                            String[] parts = line.split(" - ");
                            stateSensor = parts[1];
                        }
                    }
                }
            }
        }else{
            stateSensor = "";
        }
        colorSensorIfAlarm();               //подсвечиваем сенсор, если сработал порог или превышение

        if (lines_archive > 0)                                   //если есть записи в архиве, то выводим их количество на экран
        {
            arch_cnt.setVisibility(VISIBLE);
            arch_alert.setVisibility(VISIBLE);
            arch_cnt.setText(lines_archive + "");
            if (sending_archive) tstatus.setText(R.string.Sendnig_Arch);
        } else {
            arch_cnt.setVisibility(INVISIBLE);
            arch_alert.setVisibility(INVISIBLE);
        }

    }

    private final ObjectAnimator[] animators = new ObjectAnimator[4];

    private void colorSensorIfAlarm() {
        LinearLayout[] gazLayouts = {gazLayout1, gazLayout2, gazLayout3, gazLayout4};

        for (int sensor = 0; sensor < 4; sensor++) {
            if (stateSensor != null && stateSensor.equals(myPG.gazType[sensor])) {

                // Анимация уже запущена
                if (animators[sensor] != null && animators[sensor].isRunning()) {
                    continue;
                }

                PropertyValuesHolder pvh = PropertyValuesHolder.ofKeyframe(
                        "backgroundColor",

                        Keyframe.ofObject(0.0f, Color.TRANSPARENT),

                        Keyframe.ofObject(0.2f, 0x80FF0000),

                        Keyframe.ofObject(0.5f, 0x80FF0000),

                        Keyframe.ofObject(0.8f, Color.TRANSPARENT),

                        // удерживаем прозрачный цвет до конца цикла
                        Keyframe.ofObject(1.0f, Color.TRANSPARENT)
                );

                animators[sensor] = ObjectAnimator.ofPropertyValuesHolder(
                        gazLayouts[sensor],
                        pvh
                );

                animators[sensor].setEvaluator(new ArgbEvaluator());
                animators[sensor].setDuration(2000);          // полный цикл = 2 секунды
                animators[sensor].setRepeatCount(ValueAnimator.INFINITE);
                animators[sensor].setRepeatMode(ValueAnimator.RESTART);

                animators[sensor].start();
            } else {
                if (animators[sensor] != null) {
                    Log.d("colorSensor", "Останавливаю анимацию");
                    animators[sensor].cancel();
                    animators[sensor] = null;
                }

                gazLayouts[sensor].setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private final Handler archiveHandler = new Handler(Looper.getMainLooper());

    public void saveArchiveIfNeeded() {
        archiveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (!connect_server && cbServer.isChecked()) {
                    new Thread(() -> {

                        try {
                            JSONObject json = new JSONObject();

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

                            JSONObject coord = new JSONObject();
                            coord.put("lat", lat);
                            coord.put("lng", lng);
                            object.put("coord", coord);

                            object.put("rssi", rssi);

                            object.put("battery_percent", PG414.getInstance().percent_charge);

                            json.put("object", object);
                            params = json.toString();

                            Log.d("JSON_ARCHIVE_SAVE", json.toString(2));
                        } catch (JSONException e) {
                            Log.e("ARCHIVE", "Ошибка формирования пакета архива");
                        }

                        writeToFile(params);            //пишем в файл архива

                    }).start();
                }
                archiveHandler.postDelayed(this, 15000);
            }
        }, 3500);


    }


    //Запись данных в файл.
    protected void writeToFile(String text) {
        text += '\n';
        try (FileOutputStream fos = openFileOutput(FILE_NAME, MODE_APPEND)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
            lines_archive++;
        } catch (IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    //Открываем файл и смотрим сколько там неотрпавленных пакетов
    protected void readCntLines() {
        try {
            byte[] buffer;
            try (FileInputStream fin = openFileInput(FILE_NAME)) {
                buffer = new byte[fin.available()];
                // считаем файл в буфер
                fin.read(buffer, 0, fin.available());
            }
            int linesCount = 0;
            for (byte b : buffer) {
                if (b == '\n') linesCount++;
            }
            lines_archive = linesCount;         //Число не отправленных пакетов после включения программы
        } catch (IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            lines_archive = 0;
        }
    }

    //Чтение первой строки файла. А затем ее удаление
    @SuppressWarnings("SameParameterValue")
    private String readFirstLine(String fileName) {
        List<String> lines = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(openFileInput(fileName), StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();

            if (lines.isEmpty()) return null;

            String firstLine = lines.remove(0);

            // перезаписываем файл без первой строки
            FileOutputStream fos = openFileOutput(fileName, MODE_PRIVATE);
            for (String l : lines) {
                fos.write((l + "\n").getBytes(StandardCharsets.UTF_8));
            }
            fos.close();

            return firstLine;

        } catch (Exception e) {
            Log.e("readFirstLine", "ошибка чтения файла");
        }

        return null;
    }


    //Очистка файла
    protected void clearFile() {
        try (FileOutputStream fos = openFileOutput(FILE_NAME, MODE_PRIVATE)) {
            fos.write(0);
        } catch (IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            lines_archive = 0;
        }
    }


    //Печать текстового дескриптора катринки
    public void printToast(View view) {

        view.setOnLongClickListener(v -> {
            clearBtn();
            lines_archive = 0;

            return true;
        });

    }

    public void showDescription(View v){
        CharSequence description = v.getContentDescription();

        if (description != null && description.length() > 0) {
            Toast.makeText(
                    v.getContext(),
                    description,
                    Toast.LENGTH_SHORT
            ).show();
        }

    }

    private void enableSendingToServ() {
        if (cbServer.isChecked()) {
            Log.d("enableSendingToServ", "Включена отправка на сервер, запускаю поток");
            startServerTimer();
        } else {
            Log.d("enableSendingToServ", "Выключена отправка на сервер, останавливаю поток");
            serverHandler.removeCallbacksAndMessages(null);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void setCheck() {
        if (!permHelper.checkBTPermissions()) {
            permHelper.requestBTPermissions(this);
            return;
        }
        if (!Connect.offline && !Connect.hideMode) {
            ble_manager.isSetCheckPressed = true;
            ble_manager.setCheck = cbMoblity.isChecked();
        }

    }

//-----------------------------------------------------------------------------------------------
}



