package com.eriskip.ble_pg414.library;
import java.util.HashMap;

/**
 * Created by brijesh on 15/4/17.
 */

public class SampleGattAttributes {
    public static final String UUID_BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb";
    public static final String UUID_BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb";
    public static final String UUID_DGS_STRING = "F0001131-0451-4000-B000-000000000000";

    private static HashMap<String, String> attributes = new HashMap();

    static {
        attributes.put(UUID_BATTERY_LEVEL_UUID, "Battery Level");
        attributes.put(UUID_BATTERY_SERVICE, "Battery Service");
        attributes.put(UUID_DGS_STRING, "Sending string");
    }

    public static String lookup(String uuid) {
        String name = attributes.get(uuid);
        return name;
    }
}
