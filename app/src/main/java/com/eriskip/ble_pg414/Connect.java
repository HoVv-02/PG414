package com.eriskip.ble_pg414;

import android.annotation.SuppressLint;
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
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.eriskip.ble_pg414.library.PG414;
import com.eriskip.ble_pg414.library.SampleGattAttributes;

import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

public class Connect extends AppCompatActivity {

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
    private List<String> DeviceList = new ArrayList<>();                                // массив имен найденых устройств
    public List<BluetoothDevice> BLElist = new ArrayList <>();                          // массив устройств

    public static RX_pack State_pack;                                                   // ожидаемый пакет
    private int delay_module = 500;                                                     // задержка между отправкой команды и чтением ответа

    private boolean be_connect = false;                                                 // есть ли подключение
    private byte bluetooth_en  = 0;                                                     // включен ли блютуз

    static final private int CHOOSE_THIEF = 0;                                          // для определения статуса дочерней активити

    public static boolean write_OK = false;                                             // успешное завершение записи

    public byte numParams = 1;                                                          // номер считываемого параметра
    public boolean breaker = false;                                                     // при сработке обрывает фоновый поток


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //  super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED)
        {
            read_pause = true;
            bluetooth_en = 1;
            close();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

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
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });
        //кнопка остановить сканирование
        stopScanningButton =  findViewById(R.id.ble_stop);
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                breaker = true;
                stopScanning();
            }
        });
        stopScanningButton.setVisibility(View.INVISIBLE);


        //Список найденных устройств
        deviceList =  findViewById(R.id.list_devices);
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Connect_to_BLE(i);

            }
        });

        int cnt = 100;
        try {
            //Объекты для работы с BLE
            btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            btAdapter = btManager.getAdapter();
            Intent enableIntent;
            enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
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
                    }catch (Exception e){}
            }
            btScanner = btAdapter.getBluetoothLeScanner();
        }
        catch (Exception ex) {peripheralTextView.setText("Ошибка адаптера");}



    }

    String currentDevice = "";

    //Действие при нахождении устройства
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            currentDevice = "Прибор: " + result.getDevice().getName();                         //текущее устройство
            if (!DeviceList.contains(currentDevice)) {                                         //если тек. устройства нет в списке
                DeviceList.add(currentDevice);                                                 //добавляем его
                BLElist.add(result.getDevice());                                               //пишем в список наших устройств
            }
            //Обновляем listview
            ArrayAdapter<String> AdapterTMP = new ArrayAdapter<String>(Connect.this, android.R.layout.simple_list_item_1, DeviceList);
            deviceList.setAdapter(AdapterTMP);
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
        peripheralTextView.setText("Сканирование началось...");
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        AsyncTask ScanTask = null;
        ScanTask = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    btScanner.startScan(leScanCallback);                                                //запускаем сканирование
                }
                catch (Exception ex) {peripheralTextView.setText("Ошибка сканирования");}
                return null;
            }
        };
        ScanTask.execute();                                                          //запускаем фоновый процесс
    }




    public void stopScanning() {
        peripheralTextView.setText("Сканирование остановлено");
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        //Останавливаем скан

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (btScanner != null)
                    btScanner.stopScan(leScanCallback);
            }
        });
    }

    //текущий элемент к которому будет совершено подключение
    private int index;

    MyTask readParam;
    //Подключение к выбранному BLE устройству
    public void Connect_to_BLE(int ind)
    {
        if (be_connect) {
            close();
            be_connect = false;
        }

        index = ind;
        readParam = new MyTask();
        readParam.execute();
        pgBar.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText("Чтение параметров");
        peripheralTextView.setText("Подключение...");
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
    public final static String EXTRA_DATA =
            "com.eriskip.dgsandroidcfg.EXTRA_DATA";

    //UUID для проверки связи
    public final static UUID UUID_DGS_STRING =
            UUID.fromString(SampleGattAttributes.UUID_DGS_STRING);

    private static final String TAG = "BluetoothLEService";
    private static final int STATE_DISCONNECT = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED  = 2;

    private int mConnectionState = STATE_DISCONNECT;
    private BluetoothAdapter mBluetoothAdapter;

    public BluetoothGatt mBluetoothGatt;
    public BluetoothGattCharacteristic mCharacteristic;
    private String bluetoothAddress;


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
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        //При нахождении сервисов
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered " + status);
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
            Log.d(TAG, "onCharacteristicRead " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }

        //При записи харакетристики
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite " + status);

        }

        //При выборе характеристики
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged");
            try {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
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
                         State_of_connection = "Динамические параметры считаны";
                    break;
                /*  Чтение структуры параметров */
                case READPARAM:
                        myPG.parseParam(rec_value, numParams);  //парсим структуру под номером numParams
                        State_of_connection = "Параметры успешно считаны";
                    break;

                default: peripheralTextView.setText("Неизвестный пакет");
            }
            State_pack = RX_pack.COMPLETE;

        }
        sendBroadcast(intent);
    }

    //Закрыть соединение по GATT
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    //Отключиться от блютуз
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(TAG, "Bluetooth adapter not initialize");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    //Читаем характеристику
    public void readCharacteristic(@NonNull BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        mBluetoothGatt.readCharacteristic(bluetoothGattCharacteristic);
    }

    //Установить уведомление харктеристики
    public void setCharacteristicNotification(@NonNull BluetoothGattCharacteristic characteristic, boolean enabled) {
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    //Считать данные
    public void ListShow(View view){
        offline = true;
        breaker = true;
        myPG = new PG414(mBluetoothGatt, mCharacteristic);
        Intent intent = new Intent(this, InfoPage.class);
        startActivity(intent);
    }

    //** Преречисление возможных пакетов для чтения **///
    public enum RX_pack
    {
        DYNPARAM,
        READPARAM,
        COMPLETE,
    }

    //Асинхроннный поток для выполнения подключения
    class MyTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            peripheralTextView.setText("Информация считана");
            pgBar.setVisibility(View.INVISIBLE);
            stateText.setVisibility(View.INVISIBLE);
            offline = false;
            startScanningButton.setVisibility(View.VISIBLE);
            Intent intent = new Intent(Connect.this, InfoPage.class);
            startActivityForResult(intent, CHOOSE_THIEF);
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            stateText.setText(State_of_connection);
            if (be_connect == false) {
                pgBar.setVisibility(View.INVISIBLE);
                stateText.setVisibility(View.INVISIBLE);
                peripheralTextView.setText("Ошибка подключения");
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {

                breaker = false;            //не обрываем поток
                BluetoothDevice Current_Device = BLElist.get(index);
                mBluetoothGatt = Current_Device.connectGatt(Connect.this, true, bluetoothGattCallback);
                //Ожидание после подключения
                try {
                    Thread.sleep( 2000);
                }catch (Exception e){}

                List<BluetoothGattService> BLEList = getSupportedGattServices();
                Log.d(TAG, "BLEList ITEMS:" + BLEList.size());
                if (BLEList.size() == 0)
                {
                    State_of_connection = "Ошибка подключения. Повторите попытку";
                    be_connect = false;
                    publishProgress();
                    this.cancel(false);
                }
                else    State_of_connection = "Считывание параметров";
                try {
                    if (BLEList!= null) {
                        Log.d(TAG, "BLEList 3 pos caption:" + BLEList.get(3).getUuid());
                        mCharacteristic = BLEList.get(3).getCharacteristic(UUID_DGS_STRING);
                    }
                    //Где 3 номер характеристики к которой хотим подключиться
                    if (mCharacteristic != null)
                    {
                        be_connect = true;
                        offline = false;
                        //Создаем объект класса ПГ
                        myPG = new PG414(mBluetoothGatt, mCharacteristic);

                        //запускаем фоновый поток
                    }
                    else
                    {
                        State_of_connection  = "Ошибка подключения";
                    }
                } catch (Exception e) { this.cancel(false); }
                if (be_connect) {
                    //Читаем динамические параметры устройства
                    do {
                        myPG.reqDyn();
                        State_pack = RX_pack.DYNPARAM;
                        Thread.sleep(delay_module);
                        myPG.startRead();
                        Thread.sleep(500);
                        if (breaker) return null;
                    } while (State_pack != RX_pack.COMPLETE);   //Ждем пока не прочтется
                    publishProgress();
                    //Читаем параметры №1 устройства
                    do {
                        numParams = 1;
                        myPG.reqParam(numParams);
                        State_pack = RX_pack.READPARAM;
                        Thread.sleep(delay_module);
                        myPG.startRead();
                        Thread.sleep(300);
                        if (breaker) return null;
                    } while (State_pack != RX_pack.COMPLETE);   //Ждем пока не прочтется
                    publishProgress();
                    //Читаем параметры №2 устройства
                    do {
                        numParams = 2;
                        myPG.reqParam(numParams);
                        State_pack = RX_pack.READPARAM;
                        Thread.sleep(delay_module);
                        myPG.startRead();
                        Thread.sleep(300);
                        if (breaker) return null;
                    } while (State_pack != RX_pack.COMPLETE);   //Ждем пока не прочтется


                    publishProgress();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }



    }
}
