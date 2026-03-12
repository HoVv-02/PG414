package com.eriskip.ble_pg414;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.content.SharedPreferences;
import android.content.Context;


public class MainActivity extends AppCompatActivity {
    static final private int CHOOSE_THIEF = 0;
    private static final String TAG = "Connection PG414";
    //>>>>> Настройки     -------------------------------------------------------------------
    static public SharedPreferences mSettings;
    static public SharedPreferences.Editor editor;
    public static String FILE_SETTINGS        =  "configs";
    public static String LOGIN_SETTINGS       =    "login";   public static String  Login       = "";
    public static String PASSWORD_SETTINGS     ="password";   public static String  Password    = "";
    public static String ADRES_SETTINGS     =      "adres";   public static String  Adress      = "";
    public static String WC_SETTINGS   =        "bconnect";   public static boolean bConnect;
    public static String DESCRIPT_SETTINGS  =       "desc";   public static String  Description = "";
    public static String SERVER_SETTING      =    "server";   public static String  Server       = "";
    //---------------------------------------------------------------------------------------

    EditText eLogin, ePassword, eDescript;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED)
        {
            finish();    // TMP
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = getPreferences(Context.MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        // Ассоциируем графические объекты
        eLogin    = findViewById(R.id.eLogin);
        ePassword = findViewById(R.id.ePassword);
        eDescript = findViewById(R.id.eDescript);

        // Получаем логин из настроек
        Login = mSettings.getString(LOGIN_SETTINGS,"");
        // Получаем пароль из настроек
        Password = mSettings.getString(PASSWORD_SETTINGS,"");
        // Получаем адрес модуля из настроек
        Adress = mSettings.getString(ADRES_SETTINGS,"");
        // Получаем флаг подключения к модулю
        bConnect = mSettings.getBoolean(WC_SETTINGS,false);
        // Получаем текстовый описатель
        Description = mSettings.getString(DESCRIPT_SETTINGS,"");
        // Получаем сервер подключения
        Server ="http://erc.eriskip.ru:8008/api/listeners/packages.php";

        eLogin.setText(Login);
        ePassword.setText(Password);
        eDescript.setText(Description);

        String packageName = getPackageName();
        final PowerManager pm =  (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(packageName))
        {
            Set_Battery();  //запускаем диалог необходимости выключения режима энергосбережения
        }

////TMP   //Временная строка для того чтобы переключиться на сразу форму подключения
//        Intent intent = new Intent(this, Connect.class);
//        startActivityForResult(intent, CHOOSE_THIEF);
    }

    public void ConnectOpen(View view){
        Login = eLogin.getText().toString();
        Password = ePassword.getText().toString();
        String Opisatel = eDescript.getText().toString();
        if (Opisatel.contains("ПГ"))
            Description = Opisatel;
        else
            Description = "ПГ-414." + Opisatel;
        Intent intent = new Intent(this, Connect.class);
        startActivityForResult(intent, CHOOSE_THIEF);

        // Запоминаем данные
        editor = mSettings.edit();
        editor.putString(LOGIN_SETTINGS, Login);
        editor.putString(PASSWORD_SETTINGS, Password);
        editor.putString(DESCRIPT_SETTINGS, Description);
        editor.putString(ADRES_SETTINGS, Adress);
        editor.putBoolean(WC_SETTINGS, bConnect);

        editor.commit();
    }

    //Функция выводи сообщение о необходимости отключения энергосбережения
    private void Set_Battery()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this, R.style.AlertDialogCustom);

        alert.setTitle(getString(R.string.info));
        alert.setMessage(getString(R.string.Message_powermanage));
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Intent enableIntent;
                enableIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(enableIntent);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }



    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}

