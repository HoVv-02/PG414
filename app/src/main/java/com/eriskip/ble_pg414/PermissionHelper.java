package com.eriskip.ble_pg414;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    Context context;

    public PermissionHelper (Context context){
        this.context = context.getApplicationContext();
    }

    //Функция запроса разрешений
    public static final int MY_PERMISSIONS_REQUEST_ALL = 99;

    public void checkPermissions(Activity activity) {

        List<String> permissionsNeeded = new ArrayList<>();

        //  Геолокация
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        //  Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        //  До Android 12
        else {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH);
            }

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }



        //  Если есть что запрашивать
        if (!permissionsNeeded.isEmpty()) {
            boolean showRationale = false;
            for (String perm : permissionsNeeded) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
                    showRationale = true;
                    break;
                }
            }

            if (showRationale) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogCustom)
                        .setTitle("Требуются разрешения")
                        .setMessage("Для работы Bluetooth и GPS необходимо предоставить разрешения:\n" +
                                TextUtils.join("\n", permissionsNeeded))
                        .setPositiveButton("OK", (dialog, which) -> {
                            Log.d("PERMISSION", "Пользователь согласился");
                            // Запрос разрешений
                            String[] permissionsArray = permissionsNeeded.toArray(new String[0]);
                            ActivityCompat.requestPermissions(
                                    activity,
                                    permissionsArray,
                                    MY_PERMISSIONS_REQUEST_ALL
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
                        activity,
                        permissionsArray,
                        MY_PERMISSIONS_REQUEST_ALL
                );
            }
        }
    }

    List<String> permissionsBTNeeded = new ArrayList<>();

    public boolean checkBTPermissions() {


        //  Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsBTNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsBTNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        //  До Android 12
        else {
            //  Геолокация
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsBTNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsBTNeeded.add(Manifest.permission.BLUETOOTH);
            }

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsBTNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsBTNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        return permissionsBTNeeded.isEmpty();
    }

    public void requestBTPermissions(Activity activity){
        boolean showRationale = false;
        for (String perm : permissionsBTNeeded) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
                showRationale = true;
                break;
            }
        }

        if (showRationale) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogCustom)
                    .setTitle("Требуются разрешения")
                    .setMessage("Для работы Bluetooth и GPS необходимо предоставить разрешения:\n" +
                            TextUtils.join("\n", permissionsBTNeeded))
                    .setPositiveButton("OK", (dialog, which) -> {
                        Log.d("PERMISSION", "Пользователь согласился");
                        // Запрос разрешений
                        String[] permissionsArray = permissionsBTNeeded.toArray(new String[0]);
                        ActivityCompat.requestPermissions(
                                activity,
                                permissionsArray,
                                MY_PERMISSIONS_REQUEST_ALL
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
            String[] permissionsArray = permissionsBTNeeded.toArray(new String[0]);
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsArray,
                    MY_PERMISSIONS_REQUEST_ALL
            );
        }
    }


}
