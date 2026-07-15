package com.eriskip.ble_pg414;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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

import java.util.ArrayList;
import java.util.List;

import com.eriskip.ble_pg414.library.PG414;

import static android.app.Notification.VISIBILITY_PUBLIC;
import static com.eriskip.ble_pg414.PermissionHelper.MY_PERMISSIONS_REQUEST_ALL;

public class Connect extends AppCompatActivity {

    static public SharedPreferences mSettings;
    static public SharedPreferences.Editor editor;

    public static String ADRES_SETTINGS     =      "adres";   public static String  Adress      = "";
    public static String WC_SETTINGS   =        "bconnect";   public static boolean bConnect;
    public static String SERVER_SETTING      =    "server";   public static String  Server       = "";

    public static boolean hideMode = false;
    byte[] rx_buf;

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
    public static List<BluetoothDevice> BLElist = new ArrayList <>();                          // массив устройств


    public boolean isScanning = false;                                                  //идет сканирование


    BLE_manager ble_manager;


    private boolean bluetooth_en  = false;                                                     // включен ли блютуз

    static final private int CHOOSE_THIEF = 0;                                          // для определения статуса дочерней активити


    private int taps = 0;                                                               // колчество нажатий на экран
    private long lastTap = 0;
    NotificationHelper notHelper;               //объект класса уведомлений
    PermissionHelper permHelper;                //объект класса разрешений




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
          super.onActivityResult(requestCode, resultCode, data);
        try {
            Log.d("onActivityResult", "Отключаемся");
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

            connectPressed = false;
            ble_manager.disconnectFromUser = true;
            if (!hideMode) {
                ble_manager.disconnect();
            }else{
                hideMode = false;
                Log.d("onActivityresult", "hidemode отключен");
            }
            if (btScanner != null) {
                btScanner.stopScan(leScanCallback);
            }

            peripheralTextView.setText(getString(R.string.Get_connect));
            stopScanningButton.setVisibility(View.INVISIBLE);


        }catch (Exception ex)
        {
            Log.d("onActivityResult", "Error close connection: " + ex);
        }

        if (requestCode == REQUEST_ENABLE_BT) {
            bluetooth_en = (resultCode == RESULT_OK);
        }


    }



    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint({"ClickableViewAccessibility", "HandlerLeak"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        ble_manager = BLE_manager.getInstance(this);
        notHelper = new NotificationHelper(this);
        permHelper = new PermissionHelper(this);

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
        startScanningButton.setOnClickListener((View v) -> startScanning());

        //кнопка остановить сканирование
        stopScanningButton =  findViewById(R.id.ble_stop);
        stopScanningButton.setOnClickListener(v -> stopScanning());
        stopScanningButton.setVisibility(View.INVISIBLE);

        //Запрос на доступ к месторасположению, начиная с 10 Андроид начали требовать при сканировани блютузных приложений
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);           //нужен для того чтобы рповерить включена ли геолокация

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {                                         //проверяем
            //Формируем диалоговое окно
            //формируем кнопку для активации ОК
            final AlertDialog aboutDialog = new AlertDialog.Builder(
                    Connect.this,  R.style.AlertDialogCustom).setMessage(getString(R.string.need_location))
                    .setPositiveButton("OK", (dialog, which) -> {                                    //а нее переписываем обработчик
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));   //открываем меню для включения геолокации
                    }).create();
            aboutDialog.show();
        }
        permHelper.checkPermissions(this);          //запрос разрешений
        //Список найденных устройств
        deviceList =  findViewById(R.id.list_devices);
        deviceList.setOnItemClickListener((adapterView, view, i, l) -> {
            Log.d("connectToBle", "click on device");
            if(!hideMode){
                Connect_to_BLE(i);
            }

        });


        notHelper.createNotificationChannel();        //инициализируем канал уведомлений
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

        setListener();


    }
    //-----Конец OnCreate

    private void setListener(){
        //Слушатель BLE событий
        ble_manager.setBLE_listener((state, message) -> {
            switch(state){
                case ERROR:
                    pgBar.setVisibility(View.INVISIBLE);
                    stateText.setVisibility(View.INVISIBLE);
                    peripheralTextView.setText(message);
                    startScanningButton.setVisibility(View.VISIBLE);
                    if(!ble_manager.disconnectFromUser) {
                        notHelper.showDisconnectNotification(this);
                    }
                    break;
                case READ_COMPLETE:
                    peripheralTextView.setText(message);
                    pgBar.setVisibility(View.INVISIBLE);
                    stateText.setVisibility(View.INVISIBLE);
                    startScanningButton.setVisibility(View.VISIBLE);
                    if(!ble_manager.newConnecting){
                        Log.d("BLE_listener", "Открываю активити");
                        Intent intent = new Intent(Connect.this, InfoPage.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivityForResult(intent, CHOOSE_THIEF);
                    }
                    break;
                default:
                    stateText.setText(message);

                    break;
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        BLE_manager.removeInstance();
        BLElist.clear();
        BLElist = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("onResume", "onResume");
        setListener();

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
                        Log.d("BLE_ADVERTISING", "Raw packet: " + sb);
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


    @SuppressLint("StaticFieldLeak")
    public void startScanning() {
        if (btAdapter == null || !btAdapter.isEnabled()){
            Toast.makeText(
                    this,
                    "Для работы приложения необходимо включить Bluetooth",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        deviceList.setAdapter(null);                                                                //убираем адаптер из listview, очищая его
        if(BLElist != null){
            BLElist.clear();                                                                            //чистим устройства
        }
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
                if(btScanner != null){
                    isScanning = true;
                    btScanner.startScan(leScanCallback);            //запускаем сканирование
                    Log.d("Scan", "Начал сканирование");
                }else{
                    btScanner = btAdapter.getBluetoothLeScanner();
                    startScanning();
                }


            }
            catch (Exception ex){
                isScanning = false;
                Log.d("startScanning", "Ошибка при сканировании " + ex);
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

    private boolean connectPressed = false;
    int index;

    //Подключение к выбранному BLE устройству
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void Connect_to_BLE(int ind)
    {
        stopScanning();
        if(!btAdapter.isEnabled()){
            Toast.makeText(this, "Для работы приложения необходимо включить Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        index = ind;

        if(ble_manager.disconnectFromUser){
            ble_manager.disconnectFromUser = false;
        }

        pgBar.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.connect);
        peripheralTextView.setText(R.string.connect);
        startScanningButton.setVisibility(View.INVISIBLE);

        if(connectPressed){
            ble_manager.changeDevice(ind);
            Log.d("Connect_to_BLE", "Пользователь нажална девайс второй раз, инициирую новое подключение");
        }else{
            Log.d("Connect_to_BLE", "Пользователь нажална девайс первый раз, инициирую подключение");
            ble_manager.connect(ind);
        }
        connectPressed = true;


    }



    public void ListShow(View view){

        myPG = PG414.getInstance(this);
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




    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MY_PERMISSIONS_REQUEST_ALL) {

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

//--     end-file
}
