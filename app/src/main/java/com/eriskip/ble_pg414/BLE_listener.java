package com.eriskip.ble_pg414;

public interface BLE_listener {
    void onConnectionStateChanged(BLE_manager.ConnectionState state, String msg);
}
