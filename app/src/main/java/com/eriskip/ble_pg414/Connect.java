package com.eriskip.ble_pg414;

import android.Manifest;
import android.annotation.SuppressLint;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.eriskip.ble_pg414.library.PG414;
import com.eriskip.ble_pg414.library.SampleGattAttributes;

import static android.app.Notification.VISIBILITY_PUBLIC;
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
    public List<BluetoothDevice> BLElist = new ArrayList <>();                          // массив устройств

    public static RX_pack State_pack;                                                   // ожидаемый пакет

    private boolean be_connect = false;                                                 // есть ли подключение
    private boolean isConnecting = false;
    private byte bluetooth_en  = 0;                                                     // включен ли блютуз

    private boolean servicesDiscovered = false;

    static final private int CHOOSE_THIEF = 0;                                          // для определения статуса дочерней активити

    public byte numParams = 1;                                                          // номер считываемого параметра
    public boolean breaker = false;                                                     // при сработке обрывает фоновый поток

    public boolean trueRead = false;                                                    // успешное считывание параметров

    private int taps = 0;                                                               // колчество нажатий на экран
    private long lastTap = 0;
    private boolean disconnectFromUser = false;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
          super.onActivityResult(requestCode, resultCode, data);
        try {
            InfoPage.stop_dyn = true;
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

                myPG.mBluetoothGatt.disconnect();
                myPG.mBluetoothGatt.close();
                myPG.mBluetoothGatt = null;
                if (btScanner != null)
                    btScanner.stopScan(leScanCallback);

                disconnectFromUser = true;
                peripheralTextView.setText(getString(R.string.Get_connect));
            }
        }catch (Exception ex)
        {
            Log.d(TAG, "Error close connection: " + ex);
        }
        if (resultCode == RESULT_CANCELED)
        {
            read_pause = true;
            bluetooth_en = 1;
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
                    Toast.makeText(this, "Hidemode активирован", Toast.LENGTH_SHORT).show();
                    hideMode = true;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Set_Battery();  // запускаем диалог
            }
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
        startScanningButton.setOnClickListener((View v) -> startScanning());

        //кнопка остановить сканирование
        stopScanningButton =  findViewById(R.id.ble_stop);
        stopScanningButton.setOnClickListener(v -> {
            breaker = true;
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
        deviceList.setOnItemClickListener((adapterView, view, i, l) -> Connect_to_BLE(i));

        //Для вывода на экран статуса подключения
        handler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(@NonNull Message msg) {
                stateText.setText(State_of_connection);
                if (!be_connect && !isConnecting) {
                    pgBar.setVisibility(View.INVISIBLE);
                    stateText.setVisibility(View.INVISIBLE);
                    peripheralTextView.setText(getString(R.string.err_conncet));
                }
                if (!offline)
                {
                    peripheralTextView.setText(getString(R.string.info_read));
                    pgBar.setVisibility(View.INVISIBLE);
                    stateText.setVisibility(View.INVISIBLE);
                    offline = false;
                    startScanningButton.setVisibility(View.VISIBLE);
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

            while (!btAdapter.isEnabled()) {                                       //пока не включен блютуз выводим сообщение
                if (bluetooth_en == 1)
                {
                    enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
                    bluetooth_en = 0;

                }
                if (bluetooth_en == 2) break;
                else
                    try {
                        Thread.sleep(5000);
                    }catch (Exception e){
                        Log.e("BLE", "Sleep interrupted", e);
                    }
            }
            btScanner = btAdapter.getBluetoothLeScanner();
        }
        catch (Exception ex) {peripheralTextView.setText(R.string.err_adapter);}
    }
    //-----Конец OnCreate

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
            if (result.getDevice().getName()!=null)                                                //имя может быть null - поэтому contains вызывает исключение
                if (result.getDevice().getName().contains("PG")) {
                currentDevice = getResources().getString(R.string.Device) + result.getDevice().getName();                         //текущее устройство
                if (!DeviceList.contains(currentDevice)) {                                         //если тек. устройства нет в списке
                    DeviceList.add(currentDevice);                                                 //добавляем его
                    BLElist.add(result.getDevice());                                               //пишем в список наших устройств
                }
                //Обновляем listview
                ArrayAdapter<String> AdapterTMP = new ArrayAdapter<>(Connect.this, android.R.layout.simple_list_item_1, DeviceList);
                deviceList.setAdapter(AdapterTMP);
            }
        }
    };



    @SuppressLint("StaticFieldLeak")
    public void startScanning() {
        if (be_connect)                                                                             //если было подключение
            close();         //отключаемся
        be_connect = false;
        deviceList.setAdapter(null);                                                                //убираем адаптер из listview, очищая его
        BLElist.clear();                                                                            //чистим устройства
        DeviceList.clear();                                                                         //чистим имена устройств
        currentDevice = "";
        peripheralTextView.setText(R.string.start_scan);
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
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

                btScanner.startScan(leScanCallback);                                               //запускаем сканирование
                Log.d("Scan", "Начал сканирование");
            }
            catch (Exception ex)
            {peripheralTextView.setText(R.string.scan_error);}
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

    //Подключение к выбранному BLE устройству
    public void Connect_to_BLE(int ind)
    {
        if (be_connect) {
            close();
            be_connect = false;
        }

        index = ind;
        /*Запускаем  поток*/
        Thread thread = new Thread(runnable_connect);
        thread.start();
        isConnecting = true;
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


    //Обратный вызов GATT сервиса устройства
    public BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        //При подключении
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange " + newState);

            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    // Android 12+
                    if (ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                Connect.this,
                                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                                2001
                        );
                        return;
                    }

                } else {
                    // Android 8–11 (обычно уже достаточно Bluetooth permission, но можно добавить check)
                    if (ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.BLUETOOTH)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                Connect.this,
                                new String[]{Manifest.permission.BLUETOOTH},
                                2001
                        );
                        return;
                    }
                }
//                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
                mBluetoothGatt.discoverServices();

            } else if (newState == STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                if(disconnectFromUser){
                    showMessageForDisconnect();
                }
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
                close();
            }

        }

        //При нахождении сервисов
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered " + status);
            servicesDiscovered = true;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
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

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    //При заврешении чтения значения

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) throws UnsupportedEncodingException {
        final Intent intent = new Intent(action);
        byte[] rec_value = characteristic.getValue();
        if (UUID_DGS_STRING.equals(characteristic.getUuid())) {

            //--------------Обработка текущего пакета---------------------\\
            switch (State_pack)
            {
                /*Чтение динамических параметров*/
                case DYNPARAM:
                         myPG.parseDyn(rec_value);               //парсинг динамических параметров
                         State_of_connection = getResources().getString(R.string.dyn_read_complete);
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
                    read_pause = false;
                    if (myPG.parseParamOnOff(rec_value))                         //парсим структуру
                    {
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
        sendBroadcast(intent);
    }

    //Закрыть соединение по GATT
    @SuppressLint("MissingPermission")
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        try {
            mBluetoothGatt.close();
        } catch (Exception e) {
            Log.d("BLE", "Error closing GATT: " + e);
        }
        myPG.mBluetoothGatt = null;
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
        startActivity(intent);
    }

    //** Преречисление возможных пакетов для чтения **///
    public enum RX_pack
    {
        DYNPARAM,
        READPARAM,
        COMPLETE,
        PARAMS,
    }


    Runnable runnable_connect = new Runnable() {
        public void run() {
                    try {

                    offline = true;
                    BluetoothDevice Current_Device;              //текущий девайс для подключения
                    breaker = false;                             //не обрываем поток
                    Current_Device = BLElist.get(index);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(Connect.this, Manifest.permission.BLUETOOTH_CONNECT)
                                != PackageManager.PERMISSION_GRANTED) {

                            ActivityCompat.requestPermissions(
                                    Connect.this,
                                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                                    1006
                            );
                            return;
                        }


                    mBluetoothGatt = Current_Device.connectGatt(
                            Connect.this,
                            false,
                            bluetoothGattCallback
                    );
                    //Ожидание после подключения
                    Thread.sleep(500);

                    List<BluetoothGattService> BLEList = new ArrayList<>();
                    State_of_connection = getResources().getString(R.string.services_search);
                    handler.sendEmptyMessage(1);
                    //Переподключаемся пока не произойдет подключения( 5 попыток)
                    short z = 0;
                    do {
                        BLEList = getSupportedGattServices();
                        z++;

                        if (BLEList.isEmpty()) {

                            if (mBluetoothGatt == null) {
                                mBluetoothGatt = Current_Device.connectGatt(Connect.this, false, bluetoothGattCallback);
                            }
                            Thread.sleep(2000);
                        }
                        Log.d(TAG, "BLEList ITEMS:" + BLEList.size() + " гад: " + mBluetoothGatt);

                    }
                    while ((BLEList.isEmpty()) && (z < 5));


                    if (BLEList.isEmpty()) {
                        be_connect = false;
                        isConnecting = false;
                        handler.sendEmptyMessage(1);
                        Log.d("runnable connect", "ошибка: сервисы не нашлись");
                        return;
                    } else {
                        State_of_connection = getResources().getString(R.string.params_reading);
                        handler.sendEmptyMessage(1);
                    }

                    try {
                        if (BLEList.size() > 3) {
                            Log.d(TAG, "BLEList 3 pos caption:" + BLEList.get(3).getUuid());
                            mCharacteristic = BLEList.get(3).getCharacteristic(UUID_DGS_STRING);
                        }
                        //Где 3 номер характеристики к которой хотим подключиться
                        if (mCharacteristic != null) {
                            be_connect = true;
                            isConnecting = false;
                            //Создаем объект класса ПГ
                            myPG = PG414.getInstance(mBluetoothGatt, mCharacteristic, Connect.this);
                            myPG.set_locale(getResources().getConfiguration().locale.toString());
                            //запускаем фоновый поток
                        } else {
                            Log.d("runnable connect", "Не нашел характеристику");
                            State_of_connection = getResources().getString(R.string.err_conncet);
                            be_connect = false;
                            isConnecting = false;
                        }
                        handler.sendEmptyMessage(1);
                    } catch (Exception e) {
                        return;
                    }
                    if (be_connect) {
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
                        Log.e("TAG", "Ошибка при выполнении операции Runnable Connect", e);
                }
            }
        };
    //--
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
        Notification notification;
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        long[] pattern = {0, 100, 1000, 200, 2000};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder = new Notification.Builder(this, GPS_service.CHANNEL_ID)
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
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, GPS_service.CHANNEL_ID)
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
