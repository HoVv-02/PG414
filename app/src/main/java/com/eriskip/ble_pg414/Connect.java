package com.eriskip.ble_pg414;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.eriskip.ble_pg414.library.PG414;
import com.eriskip.ble_pg414.library.SampleGattAttributes;

import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

public class Connect extends AppCompatActivity {

    static public SharedPreferences mSettings;
    static public SharedPreferences.Editor editor;

    public static String ADRES_SETTINGS     =      "adres";   public static String  Adress      = "";
    public static String WC_SETTINGS   =        "bconnect";   public static boolean bConnect;
    public static String SERVER_SETTING      =    "server";   public static String  Server       = "";

    public static boolean hideMode = false;
    byte[] rx_buf;

    String State_of_connection;

    BluetoothManager btManager;                                                         // объект управления
    BluetoothAdapter btAdapter;                                                         // аппаратный адаптер
    BluetoothLeScanner btScanner;                                                       // часть адаптера для сканирования сети

    public static boolean offline;                                                      // оффлайн режим работы
    public static boolean read_pause = false;                                           // пауза в чтении

    private static Handler infoHandler;

    public static void setHandler(Handler handler) {
        infoHandler = handler;
    }

    public static PG414 myPG;                                                           // объект класса ПГ-414
    //>>>>>> UI  -------------------------------------------------------------------------------------------------------
    Button startScanningButton;                                                         // кнопка старт
    Button stopScanningButton;                                                          // кнопка стоп
    ProgressBar pgBar;                                                                  // прогрессбар
    TextView peripheralTextView;                                                        // заголовок
    TextView stateText;                                                                 // статус подключения
    ListView deviceList;                                                                // список найденных устройств
    //------------------------------------------------------------------------------------------------------------------
    private final static int REQUEST_ENABLE_BT = 1;                                     // статус открытия доступа к блютуз
    private final List<String> DeviceList = new ArrayList<>();                                // массив имен найденых устройств
    public static List<BluetoothDevice> BLElist = new ArrayList <>();                          // массив устройств

    public static RX_pack State_pack;                                                   // ожидаемый пакет

    public boolean isScanning = false;                                                  //идет сканирование


    private boolean be_connect = false;                                                 // есть ли подключение
    public boolean isConnecting = false;
    private boolean bluetooth_en  = false;                                                     // включен ли блютуз

    private boolean servicesDiscovered = false;

    static final private int CHOOSE_THIEF = 0;                                          // для определения статуса дочерней активити

    public byte numParams = 1;                                                          // номер считываемого параметра
    public volatile static boolean breaker = false;                                                     // при сработке обрывает фоновый поток

    public boolean trueRead = false;                                                    // успешное считывание параметров

    private int taps = 0;                                                               // колчество нажатий на экран
    private long lastTap = 0;
    private boolean disconnectFromUser = false;                                         //пользователь сам нажал Отключиться
    public static boolean isReconnecting = false;                                       //идет переподключение
    private boolean re_reconnection = false;                                            //повторное переподключение сосканированием

    private static InfoPage infoPageRef;

    // Регистрация InfoPage
    public static void setInfoPage(InfoPage page) {
        infoPageRef = page;
    }
    // Получение целевой Activity для запроса разрешений
    private Activity getTargetActivity() {
        if (isReconnecting && infoPageRef != null && !infoPageRef.isFinishing()) {
            return infoPageRef;  // используем InfoPage
        }
        return this;  // используем Connect
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
          super.onActivityResult(requestCode, resultCode, data);
        try {
            if (myPG != null) {
                Log.d(TAG, "Отключаемся");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                                3001
                        );
                        return;
                    }
                }

                if (!hideMode) {
                    close();
                }else{
                    hideMode = false;
                    Log.d("onActivityresult", "hidemode отключен");
                }
                if (btScanner != null)
                    btScanner.stopScan(leScanCallback);

                disconnectFromUser = true;
                peripheralTextView.setText(getString(R.string.Get_connect));
                stopScanningButton.setVisibility(View.INVISIBLE);

            }
        }catch (Exception ex)
        {
            Log.d(TAG, "Error close connection: " + ex);
        }

        if (requestCode == REQUEST_ENABLE_BT) {
            bluetooth_en = (resultCode == RESULT_OK);
        }


    }



    Handler handler;            //служит для вывода сообщений из потока
    @SuppressLint({"ClickableViewAccessibility", "HandlerLeak"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        findViewById(android.R.id.content).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long now = System.currentTimeMillis();
                if (now - lastTap > 3000) taps = 0;
                taps++;
                lastTap = now;
                if (taps >= 8) {
                    if(!hideMode){
                        hideMode = true;
                        Toast.makeText(this, "Hidemode активирован", Toast.LENGTH_SHORT).show();
                    }else{
                        hideMode = false;
                        Toast.makeText(this, "Hidemode отключен", Toast.LENGTH_SHORT).show();
                    }

                    taps = 0;
                }
                return true;
            }
            return false;
        });

        mSettings = getPreferences(Context.MODE_PRIVATE);

        // Получаем адрес модуля из настроек
        Adress = mSettings.getString(ADRES_SETTINGS,"");
        // Получаем флаг подключения к модулю
        bConnect = mSettings.getBoolean(WC_SETTINGS,false);
        // Получаем сервер подключения
        Server ="http://erc.eriskip.ru/api/listeners/packages.php";

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Set_Battery();  // запускаем диалог
        }

        editor = mSettings.edit();
        editor.putString(ADRES_SETTINGS, Adress);
        editor.putBoolean(WC_SETTINGS, bConnect);

        editor.commit();



        rx_buf = new byte[250];

        pgBar = findViewById(R.id.pgBar1);
        pgBar.setVisibility(View.INVISIBLE);
        //заголовок, статус
        peripheralTextView =  findViewById(R.id.title_label);
        peripheralTextView.setMovementMethod(new ScrollingMovementMethod());

        //статус подключения
        stateText = findViewById(R.id.state_of_con);
        stateText.setVisibility(View.INVISIBLE);

        //кнопка начать сканирование
        startScanningButton =  findViewById(R.id.ble_button);
        startScanningButton.setOnClickListener((View v) -> {
            startScanning();
        });

        //кнопка остановить сканирование
        stopScanningButton =  findViewById(R.id.ble_stop);
        stopScanningButton.setOnClickListener(v -> {
//            breaker = true;
            stopScanning();
        });
        stopScanningButton.setVisibility(View.INVISIBLE);

        //Запрос на доступ к месторасположению, начиная с 10 Андроид начали требовать при сканировани блютузных приложений
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);           //нужен для того чтобы рповерить включена ли геолокация

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {                                         //проверяем
            //Формируем диалоговое окно
            //формируем кнопку для активации ОК
            final android.app.AlertDialog aboutDialog = new AlertDialog.Builder(
                    Connect.this,  R.style.AlertDialogCustom).setMessage(getString(R.string.need_location))
                    .setPositiveButton("OK", (dialog, which) -> {                                    //а нее переписываем обработчик
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));   //открываем меню для включения геолокации
                    }).create();
            aboutDialog.show();
        }
        checkPermissions();          //запрос разрешений
        //Список найденных устройств
        deviceList =  findViewById(R.id.list_devices);
        deviceList.setOnItemClickListener((adapterView, view, i, l) -> {
            Log.d("connectToBle", "click on device");
            if(!hideMode){
                breaker = true;
                if (connectThread != null) {
                    connectThread.interrupt();
                    connectThread = null;
                }
                close();
                Connect_to_BLE(i);
            }

        });

        //Для вывода на экран статуса подключения
        handler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(@NonNull Message msg) {
                stateText.setText(State_of_connection);
                if (!be_connect && !isConnecting) {
                    pgBar.setVisibility(View.INVISIBLE);
                    stateText.setVisibility(View.INVISIBLE);
                    peripheralTextView.setText(getString(R.string.err_connect));
                    startScanningButton.setVisibility(View.VISIBLE);
                    Connect_to_BLE(index);
                }
                if (!offline)
                {
                    stopScanning();
                    peripheralTextView.setText(getString(R.string.info_read));
                    pgBar.setVisibility(View.INVISIBLE);
                    stateText.setVisibility(View.INVISIBLE);
                    offline = false;
                    startScanningButton.setVisibility(View.VISIBLE);
                    Log.d("HandlerConnect", "Открываю активити" + mBluetoothGatt);
                    Intent intent = new Intent(Connect.this, InfoPage.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivityForResult(intent, CHOOSE_THIEF);
                }
            }
        };
        createNotificationChannel();        //инициализируем канал уведомлений
        try {
            //Объекты для работы с BLE
            btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            btAdapter = btManager.getAdapter();
            Intent enableIntent;
            enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
            }

            if (!btAdapter.isEnabled()) {                                       //если не включен блютуз выводим сообщение
                if (!bluetooth_en)
                {
                    enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent,REQUEST_ENABLE_BT);

                }
            }
            btScanner = btAdapter.getBluetoothLeScanner();
        }
        catch (Exception ex) {peripheralTextView.setText(R.string.err_adapter);}


    }
    //-----Конец OnCreate

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        BLElist.clear();
        BLElist = null;
    }

    //Функция выводи сообщение о необходимости отключения энергосбережения
    private void Set_Battery()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this, R.style.AlertDialogCustom);

        alert.setTitle(getString(R.string.info));
        alert.setMessage(getString(R.string.Message_powermanage));
        alert.setPositiveButton("Ok", (dialog, whichButton) -> {
            Intent enableIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(enableIntent);
        });

        alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
            // Canceled.
        });

        alert.show();

    }

    String currentDevice = "";

    //Действие при нахождении устройства
    private final ScanCallback leScanCallback = new ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            BluetoothDevice device = result.getDevice();

            ScanRecord scanRecord = result.getScanRecord();

            if (result.getDevice().getName()!=null){
                if (result.getDevice().getName().contains("PG")) {
                    if (scanRecord != null){
                        byte[] bytes = scanRecord.getBytes();

                        StringBuilder sb = new StringBuilder();
                        for (byte b : bytes) {
                            sb.append(String.format("%02X ", b));
                        }
                        Log.d("BLE_ADVERTISING", "Device: " + device.getAddress());
                        Log.d("BLE_ADVERTISING", "Raw packet: " + sb.toString());
                        Log.d("BLE_ADVERTISING", "Device Name: " + scanRecord.getDeviceName());
                    }




                    currentDevice = getResources().getString(R.string.Device) + result.getDevice().getName();                         //текущее устройство
                    if (!DeviceList.contains(currentDevice)) {                                         //если тек. устройства нет в списке
                        DeviceList.add(currentDevice);                                                 //добавляем его
                        BLElist.add(result.getDevice());                                               //пишем в список наших устройств
                    }


                    //Обновляем listview
                    ArrayAdapter<String> AdapterTMP = new ArrayAdapter<>(Connect.this, android.R.layout.simple_list_item_1, DeviceList);
                    deviceList.setAdapter(AdapterTMP);


                }                                                //имя может быть null - поэтому contains вызывает исключение

            }
        }
    };

    public boolean isBtEnabled;

    @SuppressLint("StaticFieldLeak")
    public void startScanning() {
        breaker = true;
        Activity target = getTargetActivity();

        if (target == null || target.isFinishing() || target.isDestroyed()) {
            Log.e("Connect", "Нет активной Activity");
            return;
        }
        if (btAdapter == null || !btAdapter.isEnabled()){
            isBtEnabled = false;
            target.runOnUiThread(() -> {
                Toast.makeText(
                        target,
                        "Для работы приложения необходимо включить Bluetooth",
                        Toast.LENGTH_SHORT
                ).show();
            });
            if(isReconnecting){
                Log.d("startScanning", "блютуз не включен, отправляю ошибку");
                infoHandler.sendEmptyMessage(1);
            }
            return;
        }
        isBtEnabled = true;
        if (be_connect)                                                                             //если было подключение
            close();         //отключаемся
        be_connect = false;

        if (!isReconnecting) {
            deviceList.setAdapter(null);                                                                //убираем адаптер из listview, очищая его
            if(BLElist != null){
                BLElist.clear();                                                                            //чистим устройства
            }
            DeviceList.clear();                                                                         //чистим имена устройств
            currentDevice = "";
            peripheralTextView.setText(R.string.start_scan);
            startScanningButton.setVisibility(View.INVISIBLE);
            stopScanningButton.setVisibility(View.VISIBLE);
        }
        Thread ScanTask;
        ScanTask = new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    // Android 12+
                    if (ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.BLUETOOTH_SCAN)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                Connect.this,
                                new String[]{Manifest.permission.BLUETOOTH_SCAN},
                                1002
                        );
                        return;
                    }

                } else {

                    // Android 8–11
                    if (ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                Connect.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                1002
                        );
                        return;
                    }
                }
                if(btScanner != null){
                    isScanning = true;
                    btScanner.startScan(leScanCallback);            //запускаем сканирование
                    if(infoPageRef != null && !infoPageRef.isFinishing()){
                        infoPageRef.tstatus.setText("Сканирование...");         //создать ресурс в strings
                    }
                    Log.d("Scan", "Начал сканирование");
                }else{
                    btScanner = btAdapter.getBluetoothLeScanner();
                    startScanning();
                }


            }
            catch (Exception ex){
                isScanning = false;
                if(!isReconnecting){
                    peripheralTextView.setText(R.string.scan_error);
                }else {
                    Log.d("startScanning", "Ошибка при сканировании " + ex);
                    infoHandler.sendEmptyMessage(1);
                }
            }
        });
           ScanTask.start();                                                          //запускаем фоновый процесс
    }


    public void stopScanning() {
        peripheralTextView.setText(R.string.Scan_stopped);
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        //Останавливаем скан
        AsyncTask.execute(() -> {
            if (btScanner != null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    // Android 12+
                    if (ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.BLUETOOTH_SCAN)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                Connect.this,
                                new String[]{Manifest.permission.BLUETOOTH_SCAN},
                                1002
                        );
                        return;
                    }

                } else {

                    // Android 8–11
                    if (ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                Connect.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                1002
                        );
                        return;
                    }
                }

            if (btScanner != null) {
                btScanner.stopScan(leScanCallback);
            } else {
                Log.e("BLE", "btScanner is null, cannot stop scan");
            }
        }
        );
    }

    //текущий элемент к которому будет совершено подключение
    private int index;
    Thread connectThread;


    //Подключение к выбранному BLE устройству
    public void Connect_to_BLE(int ind)
    {
        if(!btAdapter.isEnabled()){
            Toast.makeText(this, "Для работы приложения необходимо включить Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (be_connect) {
            close();
            be_connect = false;
        }

        index = ind;

        /*Запускаем  поток*/
        connectThread = new Thread(runnable_connect);
        connectThread.start();
        pgBar.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.connect);
        peripheralTextView.setText(R.string.connect);
        startScanningButton.setVisibility(View.INVISIBLE);
    }

    public final static String ACTION_GATT_CONNECTED =
            "com.eriskip.dgsandroidcfg.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.eriskip.dgsandroidcfg.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.eriskip.dgsandroidcfg.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.eriskip.dgsandroidcfg.ACTION_DATA_AVAILABLE";

    //UUID для проверки связи
    public final static UUID UUID_DGS_STRING =
            UUID.fromString(SampleGattAttributes.UUID_DGS_STRING);

    private static final String TAG = "FormConnect";
    private static final int STATE_DISCONNECT = 0;
    private static final int STATE_CONNECTED  = 2;

    public static int mConnectionState = STATE_DISCONNECT;

    public static BluetoothGatt mBluetoothGatt;
    public BluetoothGattCharacteristic mCharacteristic;

    private boolean refreshGattCache(BluetoothGatt gatt) {
        try {
            Method refresh = gatt.getClass().getMethod("refresh");
            if (refresh != null) {
                return (boolean) refresh.invoke(gatt);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка refresh", e);
        }
        return false;
    }
    public static boolean isSetCheck = false;



    //Обратный вызов GATT сервиса устройства
    public BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        //При подключении
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange " + " newstate " + newState + " status " + status);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                read_pause = false;
                mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:");

//                checkBTPermissions();
//                refreshGattCache(mBluetoothGatt);
                mBluetoothGatt.discoverServices();

            } else if (newState == STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                isConnecting = false;
                if (!disconnectFromUser && !isReconnecting) {
                    showMessageForDisconnect();
                }
                close();
                read_pause = true;
            }


        }

        //При нахождении сервисов
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered " + status);
            servicesDiscovered = true;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

        }

        //При чтении характеристики
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                } catch (UnsupportedEncodingException e) {
                    Log.e("onCharacteristicRead", "broadcast error");
                }
            }
        }

        //При записи харакетристики
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("BluetoothGattCallback", "OnCharacteristicWrite");
            if(isSetCheck){
                read_pause = false;
                isSetCheck = false;
            }
        }

        //При выборе характеристики
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged");
            try {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } catch (UnsupportedEncodingException e) {
                Log.e("onCharacteristicChanged", "broadcast error");
            }
        }

        //Чтение дескриптора
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorRead " + status);
        }

        //Запись дескриптора
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite " + status);

        }

        //
        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.d(TAG, "onReliableWriteCompleted " + status);

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.d(TAG, "onReadRemoteRssi " + status);

        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(TAG, "onMtuChanged " + status);
        }
    };

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    //При заврешении чтения значения

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) throws UnsupportedEncodingException {
        byte[] rec_value = characteristic.getValue();
        if (UUID_DGS_STRING.equals(characteristic.getUuid())) {

            //--------------Обработка текущего пакета---------------------\\
            switch (State_pack)
            {
                /*Чтение динамических параметров*/
                case DYNPARAM:
                         myPG.parseDyn(rec_value);               //парсинг динамических параметров
                    if(!InfoPage.dynReading){
                        State_of_connection = getResources().getString(R.string.dyn_read_complete);
                    }
                    break;
                /*  Чтение структуры параметров */
                case READPARAM:
                        if (myPG.parseParam(rec_value, numParams))              //парсим структуру под номером numParams
                        {
                            trueRead = true;
                            State_of_connection = getResources().getString(R.string.read_params_compl);
                        }
                        else trueRead = false;
                    break;

                case PARAMS:
//                    read_pause = false;
                    if (myPG.parseParamOnOff(rec_value))                         //парсим структуру
                    {
                        Log.d("parse", "считаны переключаемые параметры");
                        trueRead = true;
                        State_of_connection = getResources().getString(R.string.read_params_compl);
                    }
                    else trueRead = false;
                    break;

                 default:
                     runOnUiThread(() -> peripheralTextView.setText(R.string.un_package));

            }
            State_pack = RX_pack.COMPLETE;

        }
    }

    //Закрыть соединение по GATT
    @SuppressLint("MissingPermission")
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }

        try {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            if(myPG.mBluetoothGatt != null){
                myPG.mBluetoothGatt.disconnect();
                myPG.mBluetoothGatt.close();
                myPG.mBluetoothGatt = null;
            }
        } catch (Exception e) {
            Log.d("BLE", "Error closing GATT: " + e);
        }
    }

    //Считать данные
    public void ListShow(View view){

        breaker = true;
        myPG = PG414.getInstance(mBluetoothGatt, mCharacteristic, this);
        myPG.set_locale(getResources().getConfiguration().locale.toString());
        if (!hideMode) offline = true;
        ///***HIDE MODE - режим работы без ПГ - заводской номер генерируется из мак адреса блютуза     =====================================
        String macAddress;
        long zavod_mulage;
        if (hideMode)
        {
            myPG.HIDEMODE = true;
            macAddress = android.provider.Settings.Secure.getString(getApplicationContext().getContentResolver(), "bluetooth_address");
            if (macAddress != null) {
                macAddress = macAddress.replaceAll(":", "");     //удаляем из него :
                macAddress = macAddress.substring(0, 6);
                zavod_mulage = Long.parseLong(macAddress, 16);              //получаем число с учетом системы счисления
                Connect.myPG.zavod_mulage = zavod_mulage;
                Connect.myPG.zavod_number = zavod_mulage;
            }
            else
            {
                @SuppressLint("HardwareIds") String androidID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);     //получаем псевдоуникальный идентификатор
                if (androidID.length() > 8)
                    Connect.myPG.zavod_number = Long.parseLong(androidID.substring(0, 8), 16);                //получаем число с учетом системы счисления
                else
                    Connect.myPG.zavod_number = 999000;
            }
            Connect.myPG.status = "ОК";

        }
        Intent intent = new Intent(this, InfoPage.class);
        startActivityForResult(intent, CHOOSE_THIEF);
    }

    //** Преречисление возможных пакетов для чтения **///
    public enum RX_pack
    {
        DYNPARAM,
        READPARAM,
        COMPLETE,
        PARAMS,
    }
    public static String deviceName = "";
    BluetoothDevice Current_Device;              //текущий девайс для подключения

    public Runnable runnable_connect = new Runnable() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void run() {
                    try {
                    isConnecting = true;
                    if(infoPageRef != null && !infoPageRef.isFinishing()){
                        infoPageRef.tstatus.setText("Подключение...");         //создать ресурс в strings
                    }

                    Activity target = getTargetActivity();

                    if (target == null || target.isFinishing() || target.isDestroyed()) {
                        Log.e("Connect", "Нет активной Activity");
                        return;
                    }

                    offline = true;
                    breaker = false;                             //не обрываем поток

                    Current_Device = BLElist.get(index);

//                    deviceName = Current_Device.getName();

//                    checkBTPermissions();
                    mBluetoothGatt = Current_Device.connectGatt(
                            target,
                            false,
                            bluetoothGattCallback
                    );

                    if(breaker) return;
                    //Ожидание после подключения
                    Thread.sleep(500);

                    List<BluetoothGattService> BLEList = new ArrayList<>();
                    if(!isReconnecting){
                        State_of_connection = getResources().getString(R.string.services_search);
                        handler.sendEmptyMessage(1);
                    }
                    //Переподключаемся пока не произойдет подключения( 5 попыток)
                    short z = 0;
                    do {
                        BLEList = getSupportedGattServices();
                        z++;

                        if (BLEList == null || BLEList.isEmpty()) {

                            if (mBluetoothGatt == null) {
                                mBluetoothGatt = Current_Device.connectGatt(target, false, bluetoothGattCallback);
                            }
                            if (breaker) return;
                            Thread.sleep(2000);
                        }
                        int size = (BLEList == null) ? 0 : BLEList.size();
                        Log.d(TAG, "BLEList ITEMS: " + size + " гад: " + mBluetoothGatt);
                    }
                    while ((z < 5) && ((BLEList == null || BLEList.isEmpty())));


                    if (BLEList == null || BLEList.isEmpty()) {
                        be_connect = false;
                        isConnecting = false;

                        Handler targetHandler = isReconnecting ? infoHandler : handler;
                        targetHandler.sendEmptyMessage(1);
                        Log.d("runnable connect", "ошибка: сервисы не нашлись");
                        return;
                    } else {
                        if(!isReconnecting){
                            State_of_connection = getResources().getString(R.string.params_reading);
                            handler.sendEmptyMessage(1);
                        }
                    }

                    try {
                        if (BLEList.size() > 3) {
                            mCharacteristic = BLEList.get(3).getCharacteristic(UUID_DGS_STRING);
                            Log.d(TAG, "BLEList 3 pos caption:" + BLEList.get(3).getUuid() + " " + mCharacteristic);

                        }
                        //Где 3 номер характеристики к которой хотим подключиться
                        if (mCharacteristic != null) {
                            be_connect = true;
                            isConnecting = false;
//                            if (getResources() == null) {
//                                Log.e(TAG, "Resources = null, Activity уничтожена");
//                                return;
//                            }
                            Log.d("runnable_connect", "context: " + target);

                            //Создаем объект класса ПГ
                            myPG = PG414.getInstance(mBluetoothGatt, mCharacteristic, target);
                            Log.d("runnable_connect", "объект пг " + myPG);
                            String locale;
                            locale = target.getResources().getConfiguration().locale.toString();
                            myPG.set_locale(locale);

                            if(isReconnecting){
                                offline = false;
                                isReconnecting = false;
                                read_pause = false;
                                infoHandler.sendEmptyMessage(2);
                                return;
                            }
                        } else {
                            Log.d("runnable connect", "Не нашел характеристику");

                            be_connect = false;
                            isConnecting = false;

                            if(isReconnecting){
                                infoHandler.sendEmptyMessage(1);
                            }else{
                                State_of_connection = getResources().getString(R.string.err_connect);
                                handler.sendEmptyMessage(1);
                            }
                        }
                    } catch (Exception e) {
                        return;
                    }
                    if (be_connect) {
                        Log.d("runnable_connect", "Запускаю первичное чтение");
                        trueRead = false;
                        //Читаем динамические параметры устройства
                        // задержка между отправкой команды и чтением ответа
                        int delay_module = 700;
                        int p = 0;
                        do {
                            State_pack = RX_pack.DYNPARAM;
                            myPG.reqDyn();
                            Thread.sleep(delay_module);
                            myPG.startRead();
                            Thread.sleep(500);
                            p++;
                            if (breaker) return;
                        } while (State_pack != RX_pack.COMPLETE && p < 10);   //Ждем пока не прочтется
                        if(p >= 10){
                            be_connect = false;
                            Log.d("runnable connect", "ошибка чтения динамики");
                            handler.sendEmptyMessage(1);
                            return;
                        }
                        handler.sendEmptyMessage(1);
                        do {
                            //Читаем параметры №1 устройства
                            do {
                                numParams = 1;
                                State_pack = RX_pack.READPARAM;
                                myPG.reqParam(numParams);
                                Thread.sleep(delay_module);
                                myPG.startRead();
                                Thread.sleep(500);
                                if (breaker) return;
                            } while (State_pack != RX_pack.COMPLETE);   //Ждем пока не прочтется
                            handler.sendEmptyMessage(1);
                            //Читаем параметры №2 устройства
                            do {
                                numParams = 2;
                                State_pack = RX_pack.READPARAM;
                                myPG.reqParam(numParams);
                                Thread.sleep(delay_module);
                                myPG.startRead();
                                Thread.sleep(500);
                                if (breaker) return;
                            } while (State_pack != RX_pack.COMPLETE);   //Ждем пока не прочтется
                            //Читаем переключаемые параметры
                            do {
                                State_pack = RX_pack.PARAMS;
                                myPG.setParamOnOff((short)0, (short)0,'+');
                                Thread.sleep(delay_module);
                                myPG.startRead();
                                Thread.sleep(500);
                                if (breaker) return;
                            } while (State_pack != RX_pack.COMPLETE);   //Ждем пока не прочтется
                        }
                        while (!trueRead);
                        offline = false;
                        handler.sendEmptyMessage(1);
                    }
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.e("TAG", "Ошибка при выполнении операции Runnable Connect", e);
                }
            }
        };


//Функция запроса разрешений на локацию
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public void checkPermissions() {

        List<String> permissionsNeeded = new ArrayList<>();

        //  Геолокация
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        //  Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        //  До Android 12
        else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH);
            }

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }



        //  Если есть что запрашивать
        if (!permissionsNeeded.isEmpty()) {
            boolean showRationale = false;
            for (String perm : permissionsNeeded) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                    showRationale = true;
                    break;
                }
            }

            if (showRationale) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogCustom)
                        .setTitle("Требуются разрешения")
                        .setMessage("Для работы Bluetooth и GPS необходимо предоставить разрешения:\n" +
                                TextUtils.join("\n", permissionsNeeded))
                        .setPositiveButton("OK", (dialog, which) -> {
                            Log.d("PERMISSION", "Пользователь согласился");
                            // Запрос разрешений
                            String[] permissionsArray = permissionsNeeded.toArray(new String[0]);
                            ActivityCompat.requestPermissions(
                                    Connect.this,
                                    permissionsArray,
                                    MY_PERMISSIONS_REQUEST_LOCATION
                            );
                        })
                        .setNegativeButton("Отмена", (dialog, which) -> {
                            Log.d("PERMISSION", "Пользователь отказался");
                            dialog.dismiss();
                        });

                AlertDialog dialog = builder.create();
                dialog.show();
                Log.d("PERMISSION", "Диалог показан");
            } else {
                Log.d("checkPermissions", "showRational = false");
                // Если rationale не нужен, сразу запрашиваем
                String[] permissionsArray = permissionsNeeded.toArray(new String[0]);
                ActivityCompat.requestPermissions(
                        this,
                        permissionsArray,
                        MY_PERMISSIONS_REQUEST_LOCATION
                );
            }
        }
    }

//    public boolean permissionGranted = false;

    public void checkBTPermissions() {
        Activity target = getTargetActivity();

        if (target == null || target.isFinishing() || target.isDestroyed()) {
            Log.e("Connect", "Нет активной Activity");
            return;
        }

        final Activity activity = target;

        // Выполняем в UI потоке
        activity.runOnUiThread(() -> {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.e("Connect", "Activity уничтожена во время выполнения");
                return;
            }

            boolean granted;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                granted = ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
                if (!granted) {
                    ActivityCompat.requestPermissions(
                            activity,
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                            2001
                    );
                }
            } else {
                granted = ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH)
                        == PackageManager.PERMISSION_GRANTED;
                if (!granted) {
                    ActivityCompat.requestPermissions(
                            activity,
                            new String[]{Manifest.permission.BLUETOOTH},
                            2001
                    );
                }
            }


        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION) {

            boolean allGranted = true;

            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d("PERMISSION", "Все разрешения выданы");
            } else {
                Log.d("PERMISSION", "Не все разрешения выданы");
            }
        }
    }
    //-----------------------------------------------------------------
    //                  Выводим уведомление об отключении
    //-----------------------------------------------------------------
    void showMessageForDisconnect()
    {
        Log.d("showMessage", "Показываю уведомление об отлючении");
        Activity target = getTargetActivity();

        if (target == null || target.isFinishing() || target.isDestroyed()) {
            Log.e("Connect", "Нет активной Activity");
            return;
        }
        Notification notification;
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        long[] pattern = {0, 100, 1000, 200, 2000};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder = new Notification.Builder(target, GPS_service.CHANNEL_ID)
                    .setSmallIcon(R.drawable.avrr)
                    .setContentTitle(getString(R.string.Alert_title))
                    .setContentText(getString(R.string.con_lost))
                    .setAutoCancel(true)
                    .setVisibility(VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_ALARM);
//                    .setFullScreenIntent(fullScreenPendingIntent, true);
                     notification = builder.build();
        }
        else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(target, GPS_service.CHANNEL_ID)
                    .setSmallIcon(R.drawable.avrr)
                    .setContentTitle(getString(R.string.Alert_title))
                    .setContentText(getString(R.string.con_lost))
                    .setAutoCancel(true)
                    .setVibrate(pattern)
                    .setSound(alarmSound)
                    .setPriority(Notification.PRIORITY_MAX);
//                    .setFullScreenIntent(fullScreenPendingIntent, true);

                    notification = builder.build();

        }
// Show Notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(197, notification);
    }


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
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
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
//--     end-file
}
