package com.eriskip.ble_pg414;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.eriskip.ble_pg414.library.PG414;
import com.eriskip.ble_pg414.library.SampleGattAttributes;

import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLE_manager {

    private BLE_listener ble_listener;

    public void setBLE_listener(BLE_listener listener) {
        this.ble_listener = listener;
    }

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private void notifyState(ConnectionState state, String message) {
        if (ble_listener != null) {
            uiHandler.post(() ->
                    ble_listener.onConnectionStateChanged(state, message)
            );
        }
    }

    public enum ConnectionState {
        CONNECTING,
        DISCOVERING_SERVICES,
        READING_DYN_PARAMS,
        READING_STATIC_PARAMS,
        READING_ONOFF_PARAMS,
        READ_COMPLETE,
        PACKET_PARSED,
        CON_RESTORED,
        RECONNECTING,
        DISCONNECTING,
        ERROR,
        CHANGE_DEVICE,
        READ_RSSI
    }

    private final Context context;
    private static BLE_manager instance;

    public BLE_manager(Context context) {
        this.context = context.getApplicationContext();
        IntentFilter filter =
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

        context.registerReceiver(bluetoothStateReceiver, filter);
    }

    public static synchronized BLE_manager getInstance(Context context) {
        if (instance == null) {
            instance = new BLE_manager(context);
        }
        return instance;
    }

    public static synchronized void removeInstance(){
        instance = null;
        Log.d("BLE_manager", "Instance removed");
    }



    public boolean btEnabled = true;
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onReceive(Context context, Intent intent) {

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {

                int state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);

                switch (state) {

                    case BluetoothAdapter.STATE_OFF:
                        Log.d("BLE", "Bluetooth OFF");
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d("BLE", "Bluetooth TURNING_OFF");

                        State_of_connection = "Включите Bluetooth";
                        notifyState(ConnectionState.ERROR, State_of_connection);
                        removeCallbacks();
                        btEnabled = false;
                        break;

                    case BluetoothAdapter.STATE_ON:
                        Log.d("BLE", "Bluetooth ON");
                        btEnabled = true;
                        reconnect();
                        break;

                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d("BLE", "Bluetooth TURNING_ON");
                        break;
                }
            }
        }
    };

    //UUID для чтения парамаетров
    public final static UUID UUID_DGS_STRING =
            UUID.fromString(SampleGattAttributes.UUID_DGS_STRING);

    private static final int STATE_DISCONNECT = 0;
    private static final int STATE_CONNECTED  = 2;
    private static final int GATT_NO_RESOURCES = 128;

    public static int mConnectionState = STATE_DISCONNECT;

    public static BluetoothGatt mBluetoothGatt;
    public BluetoothGattCharacteristic mCharacteristic;

    public static PG414 myPG;                                                           // объект класса ПГ-414
    public byte numParams = 1;                                                          // номер считываемого параметра

    public RX_pack State_pack;                                                          // ожидаемый пакет

    //** Преречисление возможных пакетов для чтения **///
    public enum RX_pack
    {
        DYN_PARAM,
        STATIC_PARAM,
        ONOFF_PARAM,

    }

    private boolean be_connect = false;                                                 // есть ли подключение
    int index;
    String State_of_connection;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connect(int ind){
        index = ind;
        BluetoothDevice Current_Device;
        Current_Device = Connect.BLElist.get(index);
        startConnectTimeout();

        mBluetoothGatt = Current_Device.connectGatt(
                context,
                false,
                bluetoothGattCallback
        );
        Log.d("connect", "Запускаю подключение");

        State_of_connection = context.getResources().getString(R.string.connect);
        notifyState(ConnectionState.CONNECTING, State_of_connection);
    }

    public boolean connectPressed = false;

    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    private Runnable reconnectTimeoutRunnable;

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (mBluetoothGatt == null) {
            return;
        }
        State_of_connection = context.getResources().getString(R.string.disconnecting);
        notifyState(ConnectionState.DISCONNECTING, State_of_connection);

        try {
            Log.d("disconnect", "disconnect");
            mBluetoothGatt.disconnect();
        } catch (Exception e) {
            Log.d("BLE", "Error disconnect GATT: " + e);
        }

        reconnectTimeoutRunnable = () -> {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            if (btEnabled) {
                connToDev++;
                reconnect();
            }

        };
        reconnectHandler.postDelayed(reconnectTimeoutRunnable, 1500);
    }
    private boolean isReconnecting = false;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void reconnect(){
        Log.d("reconnect", "reconnect");
        isReconnecting = true;
        readCnt = 0;
        if(connToDev > 3){
            Log.d("reconnect", "Попытки покдлючения закончились");
            State_of_connection = context.getResources().getString(R.string.err_con_message);
            notifyState(ConnectionState.ERROR, State_of_connection);
            connToDev = 0;
            return;
        }
        State_of_connection = context.getResources().getString(R.string.connect);
        notifyState(ConnectionState.RECONNECTING, State_of_connection);
        new Handler(Looper.getMainLooper()).postDelayed(() ->{
            Log.d("reconnect", "Запускаю реконект");
            connect(index);
        }, 200);
    }

    private final Handler changeDeviceHandler = new Handler(Looper.getMainLooper());

    private Runnable changeDeviceTimeoutRunnable;
    public boolean newConnecting = false;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void changeDevice(int ind){
        removeCallbacks();
        be_connect = false;
        newConnecting = true;

        index = ind;

        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }

        changeDeviceTimeoutRunnable = () -> {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
            connect(ind);
        };
        changeDeviceHandler.postDelayed(changeDeviceTimeoutRunnable, 1500);
    }

    public boolean openPage = false;
    private int readCnt = 0;
    public boolean showMsgForUpdate = false;


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void parseParams(final BluetoothGattCharacteristic characteristic) throws UnsupportedEncodingException {
        byte[] rec_value = characteristic.getValue();
        //--------------Обработка текущего пакета---------------------\\
        switch (State_pack)
        {
            /*Чтение динамических параметров*/
            case DYN_PARAM:
                if(myPG.parseDyn(rec_value)){       //парсинг динамических параметров
                    //уведомляем активити
                    readCnt = 0;
                    if (!openPage) {
                        State_pack = RX_pack.STATIC_PARAM;
                    }else{
                        Log.d("parseParams", "динамика считана");
                        notifyState(ConnectionState.PACKET_PARSED, "Динамика считана");
                    }
                }else{
                    Log.d("parseParams", "не получилось прочитать пакет");
                    readCnt++;
                }
                break;

            /*  Чтение структуры параметров */
            case STATIC_PARAM:
                if (myPG.parseParam(rec_value, numParams))              //парсим структуру под номером numParams
                {
                    readCnt = 0;
                    //уведомляем активити
                    if(numParams == 1){
                        numParams = 2;
                    }else{
                        numParams = 1;
                        if(isRefreshPressed){
                            Log.d("parseParams", "статические параметры считаны");
                            notifyState(ConnectionState.PACKET_PARSED, "Данные об устройстве обновлены");
                            isRefreshPressed = false;
                            showMsgForUpdate = true;
                            State_pack = RX_pack.DYN_PARAM;
                            return;
                        }
                        State_pack = RX_pack.ONOFF_PARAM;
                    }
                }else{
                    Log.d("parseParams", "не получилось прочитать пакет");
                    readCnt++;
                }
                break;

            case ONOFF_PARAM:
                if (myPG.parseParamOnOff(rec_value))                         //парсим структуру
                {
                    if(isSetCheckPressed){
                        isSetCheckPressed = false;
                        Log.d("parseParams", "переключаемые параметры считаны");
                        notifyState(ConnectionState.PACKET_PARSED, "Детекция неподвижности записана");
                    }else{
                        openPage = true;
                        Log.d("parseParams", "Чтение завершено, уведомляю Connect");
                        State_of_connection = context.getResources().getString(R.string.info_read);
                        notifyState(ConnectionState.READ_COMPLETE, State_of_connection);
                    }
                    State_pack = RX_pack.DYN_PARAM;

                }
                break;

            default:
                //неизвестный пакет
        }
    }

    public boolean isSetCheckPressed = false;
    public boolean setCheck;
    public  boolean isRefreshPressed;
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void reqParams(){
        switch (State_pack){
            case DYN_PARAM:
                myPG.reqDyn();
                State_of_connection = context.getResources().getString(R.string.dyn_reading);
                notifyState(ConnectionState.READING_DYN_PARAMS, State_of_connection);
                break;
            case STATIC_PARAM:
                myPG.reqParam(numParams);
                State_of_connection = context.getResources().getString(R.string.static_reading);
                notifyState(ConnectionState.READING_STATIC_PARAMS, State_of_connection);
                break;
            case ONOFF_PARAM:
                if(isSetCheckPressed){
                    if(setCheck){
                        myPG.setParamOnOff((short)0, (short)(1 << 10),'-');
                    }else{
                        myPG.setParamOnOff((short)0, (short)0,'+');
                    }
                }else{
                    myPG.setParamOnOff((short)0, (short)0,'+');
                    State_of_connection = context.getResources().getString(R.string.switch_reading);
                    notifyState(ConnectionState.READING_ONOFF_PARAMS, State_of_connection);
                }
                break;
            default:
                break;
        }
    }

    private int connToDev = 0;          //счетчик попыток подключения
    public boolean disconnectFromUser = false;

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    private final Handler dynReadHandler = new Handler(Looper.getMainLooper());
    private Runnable dynReadRunnable;

    private void removeCallbacks(){
        dynReadHandler.removeCallbacks(dynReadRunnable);
        readHandler.removeCallbacks(readTimeoutRunnable);
        writeHandler.removeCallbacks(writeTimeoutRunnable);
        serviceHandler.removeCallbacks(serviceTimeoutRunnable);
        connectHandler.removeCallbacks(connectTimeoutRunnable);
        reconnectHandler.removeCallbacks(reconnectTimeoutRunnable);
        changeDeviceHandler.removeCallbacks(changeDeviceTimeoutRunnable);
        rssiReadHandler.removeCallbacks(rssiReadRunnable);
    }

    private final Handler rssiReadHandler = new Handler(Looper.getMainLooper());

    private final Runnable rssiReadRunnable = new Runnable() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void run() {

            if (mBluetoothGatt != null) {
                mBluetoothGatt.readRemoteRssi();
            }

            rssiReadHandler.postDelayed(this, 2000);
        }
    };

    private void startReadRssi() {
        rssiReadHandler.post(rssiReadRunnable);
    }


    public BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback(){

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("onConnectionStateChange", "onConnectionStateChange " + " newstate " + newState + " status " + status);
            connectHandler.removeCallbacks(connectTimeoutRunnable);
            reconnectHandler.removeCallbacks(reconnectTimeoutRunnable);
            if (newState == BluetoothProfile.STATE_CONNECTED){
                connToDev = 0;
                mConnectionState = STATE_CONNECTED;
                be_connect = true;

                startReadRssi();


                if(newConnecting){
                    Log.d("onConnectionStateChange", "Соединение с новым устройством установлено");
                    newConnecting = false;
                }
                if(isReconnecting) {
                    State_of_connection = context.getResources().getString(R.string.con_restored);
                    notifyState(ConnectionState.CON_RESTORED, State_of_connection);
                    isReconnecting = false;
                }
                Log.i("onConnectionStateChange", "Connected to GATT server.");
                Log.i("onConnectionStateChange", "Attempting to start service discovery:");

                State_of_connection = context.getResources().getString(R.string.services_search);
                notifyState(ConnectionState.DISCOVERING_SERVICES, State_of_connection);
                startServiceTimeout();
                mBluetoothGatt.discoverServices();

            }else if (newState == STATE_DISCONNECTED){
                Log.i("onConnectionStateChange", "Disconnected from GATT server.");
                mConnectionState = STATE_DISCONNECTED;
                be_connect = false;
                State_of_connection = context.getResources().getString(R.string.disconnecting);
                notifyState(ConnectionState.DISCONNECTING, State_of_connection);

                removeCallbacks();

                gatt.close();

                if (gatt == mBluetoothGatt) {
                    mBluetoothGatt = null;
                }

                if(newConnecting){
                    connect(index);
                    return;
                }

                Log.d("onConnectionStateChange",  "disconnectFromUser " + disconnectFromUser + " btEnabled " + btEnabled);


                if(!disconnectFromUser && btEnabled){
                    connToDev++;
                    reconnect();
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d("onServicesDiscovered", "onServicesDiscovered status: " + status);
            serviceHandler.removeCallbacks(serviceTimeoutRunnable);
            if (status != GATT_SUCCESS) {
                handleServiceError("status != GATT_SUCCESS");
                return;
            }

            List<BluetoothGattService> BLEList = getSupportedGattServices();

            if (BLEList == null || BLEList.size() <= 3) {
                handleServiceError("invalid service list");
                return;
            }

            BluetoothGattService service = BLEList.get(3);
            mCharacteristic = service.getCharacteristic(UUID_DGS_STRING);

            if (mCharacteristic == null) {
                handleServiceError("characteristic null");
                return;
            }

            Log.d("onServicesDiscovered",
                    "service: " + service.getUuid());

            //Создаем объект класса ПГ
            myPG = PG414.getInstance(mBluetoothGatt, mCharacteristic, context);
            Log.d("runnable_connect", "объект пг " + myPG);
            String locale;
            locale = context.getResources().getConfiguration().locale.toString();
            myPG.set_locale(locale);

            State_pack = RX_pack.DYN_PARAM;
            startWriteTimeout();
            Log.d("onServicesDiscovered", "Отправляю запрос" + State_pack);
            reqParams();

        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void handleServiceError(String msg) {
            Log.d("BLE", "Service error: " + msg);
            disconnect();

        }

        private int writeCnt = 0;
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("onCharacteristicWrite", "onCharacteristicWrite status: " + status);
            writeHandler.removeCallbacks(writeTimeoutRunnable);
            if (status == GATT_SUCCESS) {
                writeCnt = 0;
                int delayMs = openPage ? 2000 : 200;

                dynReadRunnable = () -> {
                    startReadTimeout();
                    Log.d("onCharacteristicWrite", "Запускаю чтение");
                    myPG.startRead();
                };
                dynReadHandler.postDelayed(dynReadRunnable, delayMs);


                return;
            }

            if (status == GATT_NO_RESOURCES) {
                if (writeCnt++ >= 3) {
                    disconnectWithLog("Write error 128, reconnect");
                    return;
                }

                new Handler(Looper.getMainLooper()).postDelayed(() ->{
                    startWriteTimeout();
                    Log.d("onCharacteristicWrite", "Повторный запрос");
                    reqParams();
                },150);
                return;
            }

            disconnectWithLog("Ошибка записи характеристики: " + status);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onCharacteristicRead(@NonNull @NotNull BluetoothGatt gatt, @NonNull @NotNull BluetoothGattCharacteristic characteristic, @NonNull @NotNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            Log.d("onCharacteristicRead", "onCharacteristicRead status: " + status);
            readHandler.removeCallbacks(readTimeoutRunnable);
            if (status == GATT_SUCCESS) {

                try {
                    parseParams(characteristic);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                if(readCnt > 3) {
                    disconnectWithLog("Ошибка чтения: Неправильный формат пакета");
                    return;
                }
                if(isSetCheckPressed){
                    State_pack = RX_pack.ONOFF_PARAM;
                } else if (isRefreshPressed) {
                    State_pack = RX_pack.STATIC_PARAM;
                }
                startWriteTimeout();
                Log.d("onCharacteristicRead", "Отправляю запрос" + State_pack);
                reqParams();
            }else {
                disconnectWithLog("Ошибка чтения: " + status);
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status){
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d("onCharacteristicRead", "onCharacteristicRead (старый) status: " + status);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // На Android 13+ игнорируем старый callback
                return;
            }
            readHandler.removeCallbacks(readTimeoutRunnable);
            if (status == GATT_SUCCESS) {

                try {
                    parseParams(characteristic);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

                if(readCnt > 3) {
                    disconnectWithLog("Ошибка чтения: Неправильный формат пакета");
                    return;
                }

                if(isSetCheckPressed){
                    State_pack = RX_pack.ONOFF_PARAM;
                }
                else if (isRefreshPressed) {
                    State_pack = RX_pack.STATIC_PARAM;
                }
                startWriteTimeout();
                Log.d("onCharacteristicRead", "Отправляю запрос" + State_pack);
                reqParams();
            }else{
                disconnectWithLog("Ошибка чтения: " + status);
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void disconnectWithLog(String msg) {
            Log.d("disconnectWithLog", msg);
            disconnect();

        }

        @Override
        public void onCharacteristicChanged(@NonNull @NotNull BluetoothGatt gatt, @NonNull @NotNull BluetoothGattCharacteristic characteristic, @NonNull @NotNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);
            Log.d("onCharacteristicChanged", "" + characteristic.getUuid());
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "RSSI = " + rssi + " dBm");
                String RSSI = String.valueOf(rssi);
                notifyState(ConnectionState.READ_RSSI, RSSI);
            }
        }
    };

//    -----------------------------------------------------------------------
//                                  Таймауты
//    -----------------------------------------------------------------------
    private final Handler serviceHandler = new Handler(Looper.getMainLooper());

    private Runnable serviceTimeoutRunnable;
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void startServiceTimeout(){
        serviceTimeoutRunnable = () -> {
            Log.e("BLE", "SERVICES TIMEOUT");
            disconnect();

        };

        serviceHandler.postDelayed(serviceTimeoutRunnable, 7000);
    }

    private final Handler connectHandler = new Handler(Looper.getMainLooper());

    private Runnable connectTimeoutRunnable;
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void startConnectTimeout(){
        connectTimeoutRunnable = () -> {
            Log.e("BLE", "CONNECT TIMEOUT");
            disconnect();

        };

        connectHandler.postDelayed(connectTimeoutRunnable, 8000);
    }

    private final Handler writeHandler = new Handler(Looper.getMainLooper());

    private Runnable writeTimeoutRunnable;
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void startWriteTimeout(){
        writeTimeoutRunnable = () -> {
            Log.e("BLE", "WRITE TIMEOUT");
            disconnect();

        };

        writeHandler.postDelayed(writeTimeoutRunnable, 2000);
    }

    private final Handler readHandler = new Handler(Looper.getMainLooper());

    private Runnable readTimeoutRunnable;
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void startReadTimeout(){
        readTimeoutRunnable = () -> {
            Log.e("BLE", "READ TIMEOUT");
            disconnect();

        };

        readHandler.postDelayed(readTimeoutRunnable, 2000);
    }

}
