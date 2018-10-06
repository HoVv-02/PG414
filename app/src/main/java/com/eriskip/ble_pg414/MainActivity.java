package com.eriskip.ble_pg414;




import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.content.SharedPreferences;
import android.content.Context;
import android.widget.TextView;

import 	java.io.InputStream;
import 	java.net.HttpURLConnection;
import 	java.net.URL;
import 	java.io.OutputStream;
import java.io.ByteArrayOutputStream;



public class MainActivity extends AppCompatActivity {


    static final private int CHOOSE_THIEF = 0;
    private static final String TAG = "Connection PG414";


    //>>>>> Настройки     -------------------------------------------------------------------
    private SharedPreferences mSettings;
    public String FILE_SETTINGS        =  "configs";
    public String LOGIN_SETTINGS       =    "login";   public static String  Login       = "";
    public String PASSWORD_SETTINGS     ="password";   public static String  Password    = "";
    public String ADRES_SETTINGS     =      "adres";   public static String  Adress      = "";
    public String WC_SETTINGS   =        "bconnect";   public static boolean bConnect;
    public String DESCRIPT_SETTINGS  =       "desc";   public static String  Description = "";
    //---------------------------------------------------------------------------------------

    EditText eLogin, ePassword, eDescript;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED)
        {
            finish();    //TMP
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = getSharedPreferences(FILE_SETTINGS, Context.MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        //Ассоциируем графические объекты
        eLogin    = findViewById(R.id.eLogin);
        ePassword = findViewById(R.id.ePassword);
        eDescript = findViewById(R.id.eDescript);
        eLogin.setText(Login);
        ePassword.setText(Password);
        eDescript.setText(Description);

////TMP   //Временная строка для того чтобы переключиться на сразу форму подключения
//        Intent intent = new Intent(this, Connect.class);
//        startActivityForResult(intent, CHOOSE_THIEF);
    }

    public void ConnectOpen(View view){
        Login = eLogin.getText().toString();
        Password = ePassword.getText().toString();
        Description = "ПГ-414." +  eDescript.getText().toString();
        Intent intent = new Intent(this, Connect.class);
        startActivityForResult(intent, CHOOSE_THIEF);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Запоминаем данные
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(LOGIN_SETTINGS, Login);
        editor.putString(PASSWORD_SETTINGS, Password);
        editor.putString(ADRES_SETTINGS, Adress);
        editor.putBoolean(WC_SETTINGS, bConnect);

        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
            // Получаем логин из настроек
            Login = mSettings.getString(LOGIN_SETTINGS,"nop");
            // Получаем пароль из настроек
            Password = mSettings.getString(PASSWORD_SETTINGS,"nop");
            // Получаем адрес модуля из настроек
            Adress = mSettings.getString(ADRES_SETTINGS,"nop");
            // Получаем флаг подключения к модулю
            bConnect = mSettings.getBoolean(WC_SETTINGS,false);
            // Получаем текстовый описатель
    }
}

