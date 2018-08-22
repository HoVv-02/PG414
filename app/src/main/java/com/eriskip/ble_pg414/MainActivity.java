package com.eriskip.ble_pg414;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.util.Map;
import java.util.HashMap;
import 	java.io.InputStream;
import 	java.net.HttpURLConnection;
import 	java.net.URL;
import 	java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    public String URLka = "http://89.250.220.50:8001/dev_add.php";
    private static final String TAG = "Connection PG414";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    class MyTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            sending();
            return  null;
        }
    }
    public byte[] sending()
    {
        EditText eZnumber = findViewById(R.id.eZnumber);
        EditText eDescriptor = findViewById(R.id.eDescriptor);

        String params = "id_type=1&znumber="+eZnumber.getText().toString()+"&description="+eDescriptor.getText().toString()+"&key=1562";
        byte[] dataz = null;
        InputStream is = null;

        try {
            URL url = new URL(URLka);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));
            OutputStream os = conn.getOutputStream();
            dataz = params.getBytes("UTF-8");
            os.write(dataz);
            dataz = null;

            conn.connect();
            int responseCode= conn.getResponseCode();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            is = conn.getInputStream();

            byte[] buffer = new byte[8192]; // Такого вот размера буфер
            // Далее, например, вот так читаем ответ
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            dataz = baos.toByteArray();
        } catch (Exception e) {  Log.d(TAG, e.toString());
        } finally {
            try {

                if (is != null)
                    is.close();
            } catch (Exception ex) {}
        }
         return dataz;
    }

    public void Send(View view)
    {
        MyTask send_asynk = new MyTask();
        send_asynk.execute();
    }

}

