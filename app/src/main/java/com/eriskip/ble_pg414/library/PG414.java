package com.eriskip.ble_pg414.library;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class PG414 {

        private Context context;

        public boolean HIDEMODE = false;        //режим работы без ПГ
        public long    zavod_mulage = 0;        //заводской номер из мак адреса

        //Перечисления параметров
        public enum BaudRate {b1200, b2400, b4800, b9600, b19200, b38400, b57600, b115200}
        public enum Parity   {none, even, odd}
        public enum StopBit  {b1, b2}

        /*Указатели на внешние классы*/
        public BluetoothGatt mBluetoothGatt;
        public BluetoothGattCharacteristic mCharacteristic;


        /*Версия прошивки*/
        public String hard_version;

        /*Сетевые параметры*/
        public byte mbAdr;          //Сетевой адрес

        /*Параметры*/
        public short[] limit1       = new short[4];        //порог 1
        public short[] limit2       = new short[4];        //порог 2
        public short[] hist1        = new short[4];        //гистерезис 1
        public short[] hist2        = new short[4];        //гистерезис 2
        public short[] abort_time   = new short[4];        //время сброса
        public short[] death_zone   = new short[4];        //мертвая зона

        /*Единицы измерения*/
        public byte main_unit;          //Основаная единица измерения     //0 - ед. измерения сенсора. 1 - мг/м3
        public byte mode_unit;          //Режим отображения единиц        //0 - одинарный редим отображения. 1 - двойной режим отображения

        /*Динамические данные*/
        public byte[]  state = new byte[4];       //Состояние устройства
        public int conc1;                         //Текущая концентрация с сенсора 1
        public int conc2;                         //Текущая концентрация с сенсора 2
        public int conc3;                         //Текущая концентрация с сенсора 3
        public int conc4;                         //Текущая концентрация с сенсора 4
        /*Переключаемые параметры*/
        public int onoff1;
        public int onoff2;

        public long zavod_number;       //заводской номер
        public byte percent_charge;     //процент зарядки батареи

        public String[]     gazUnit = new String[4];    //единицы измерения
        public String[]     gazType = new String[4];    //тип газа
        public byte[]       gazDiskret = new byte[4];   //дискертность единиц измерения газа
        public short[]      gazDelitel;                 //делитель концентрации газа

        public String descriptor;
        public String login;
        public String password;
        public String gps;
        public String lng;
        public String lat;
        public String status;

        public boolean localeRus;                             //Используется ли русский язык как локализация

        private int cachedErrorBits = 0;
        private long[] lastErrorTime = new long[32];
        private static final long ERROR_HOLD_TIME = 800; // 800 миллисекунд

        //Конструктор
        public PG414(BluetoothGatt mBGT,  BluetoothGattCharacteristic Character)
        {
            mBluetoothGatt = mBGT;                                                                  //получаем BLE GATT
            mCharacteristic = Character;                                                            //и характеристику
            zavod_number = 0;
            gazDelitel = new short[] {1,10,100};
            if (mBGT == null)
            {
                zavod_number = 0; conc4 =0; conc3 = 0; conc2 = 0; conc1 = 0;
            }
            if (HIDEMODE) zavod_number = zavod_mulage;
        }

        //Проверка, является ли русской текущая локаль
        public void set_locale(String text_locale)
        {
            char[] ch_locale = text_locale.toCharArray();
            if (ch_locale[0] == 'r' && ch_locale[1] == 'u')
                localeRus = true;
            else
                localeRus = false;
        }

        public String[] array_of_Errors_RUS =
                {
                        "Порог 1 - CH4",                                                       //0
                        "Порог 2 - CH4",                                                       //1
                        "Превышение диапазона - CH4",                                          //2
                        "Порог 1 - O2",                                                       //3
                        "Порог 2 - O2",                                                       //4
                        "Превышение диапазона - O2",                                          //5
                        "Порог 1 - H2S",                                                       //6
                        "Порог 2 - H2S",                                                       //7
                        "Превышение диапазона - H2S",                                          //8
                        "Порог 1 - CO",                                                       //9
                        "Порог 2 - CO",                                                       //10
                        "Превышение диапазона - CO",                                          //11
                        "Порог 1 - Сенсор 5",                                                       //12
                        "Порог 2 - Сенсор 5",                                                       //13
                        "Превышение диапазона - Сенсор 5",                                          //14
                        "Человек без движения",                                                     //15
                        "Низкий заряд батареи",                                                     //16
                        "Время не установлено",                                                     //17
                        "Ошибка связи с АЦП",                                                       //18
                        "Ошибка связи с АЦП2",                                                      //19
                        "Ошибка связи с ЛМП1",                                                      //20
                        "Ошибка связи с ЛМП2",                                                      //21
                        "Ошибка связи с ЛМП3",                                                      //22
                        "Температура вышла за диапазон. Используется нормальная температура 32 °C", //23
                        "Давление вышло за диапазон. Используется нормальное давление - 101,3 кПа", //24
                        "Чистая флешка архива или CRC flash",                                       //25
                        "Ошибка радиомодуля",                                                       //26
                        "Падение человека",                                                         //27
                        "Ошибка платы питания, пин PWGD",                                           //28
                        "Ошибка акселер",                                                           //29
                        "Неисправность сенсора",                                                    //30
                        "Ошибка расширителя"                                                        //31


                };
    public String[] array_of_Errors_EN =
            {
                    "Alarm 1 - CH4",
                    "Alarm 2 - CH4",
                    "Over range - CH4",
                    "Alarm 1 - O2",
                    "Alarm 2 - O2",
                    "Over range - O2",
                    "Alarm 1 - H2S",
                    "Alarm 2 - H2S",
                    "Over range - H2S",
                    "Alarm 1 - C0",
                    "Alarm 2 - C0",
                    "Over range - C0",
                    "Alarm 1 - Sensor 5",
                    "Alarm 2 - Sensor 5",
                    "Over range - Sensor 5",
                    "Person without movement",
                    "Low charge",
                    "Time not set",
                    "DAC communication error",
                    "DAC2 communication error",
                    "LMP1 communication error",
                    "LMP2 communication error",
                    "LMP3 communication error",
                    "The temperature is out of range. The normal temperature is 32°C.",
                    "The pressure is out of range. The normal pressure is 101.3 kPa.",
                    "A clean flash drive or CRC flash drive",
                    "Radio module error",
                    "Person fell",
                    "Power board error, pin PWGD",
                    "Accelerometer error",
                    "Sensor malfunction",
                    "Extender error"
            };

        /**********ФУНКЦИИ**********/

        //Запрос на чтение динмических параметров
        public void setParamOnOff(short s1,short s2, char set)
        {
            byte[] request = new  byte[10];
            //формируем запрос
            request[0] =(byte) 10;             //первым байтом указываем длинну ответа 4 структура 6 обертка комнады
            request[1] =(byte) 0xFE;
            request[2] =(byte) 'S';             //Команда
            request[3] =(byte) set;             //Установить или снять
            request[4] =(byte) s1;              //Установить или снять
            request[5] =(byte) (s1 >> 8);       //Установить или снять
            request[6] =(byte) s2;              //Установить или снять
            request[7] =(byte) (s2 >> 8);       //Установить или снять
            request[8] =(byte) '#';              //-------
            request[9] =(byte) 0x0D;
            mCharacteristic.setValue(request);                          //заносим в характеристику

            mBluetoothGatt.writeCharacteristic(mCharacteristic);
        }

        public boolean parseParamOnOff(byte[] answer)
        {
            onoff1 = ((answer[5]  & 0xFF) << 8) + (answer[4]  & 0xFF);
            onoff2 = ((answer[7]  & 0xFF) << 8) + (answer[6]  & 0xFF);
            return true;
        }

        //Запрос на чтение динмических параметров
        public void reqDyn()
        {
            byte[] request = new  byte[10];
            //формируем запрос
            request[0] =(byte) 23;             //первым байтом указываем длинну ответа 17 структура 6 обертка комнады
            request[1] =(byte) 0xFE;
            request[2] =(byte) 'D';              //Команда
            request[3] =(byte) 'P';
            request[4] =(byte) '#';              //-------
            request[5] =(byte) 0x0D;
            mCharacteristic.setValue(request);                          //заносим в характеристику
            mBluetoothGatt.writeCharacteristic(mCharacteristic);
        }

        //Парсим прочитанные динамические параметры
        public void parseDyn(byte[] answer)
        {
            if (answer.length < 21) return;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < answer.length; i++) {
                sb.append(String.format("%02X ", answer[i]));
            }
            Log.d("PG414", "Raw answer: " + sb.toString());

            conc1 = ((answer[5]  & 0xFF) << 8) + (answer[4]  & 0xFF);       //текущая концентрация по 1 каналу
            conc2 = ((answer[7]  & 0xFF) << 8) + (answer[6]  & 0xFF);       //текущая концентрация по 2 каналу
            conc3 = ((answer[9]  & 0xFF) << 8) +  (answer[8] & 0xFF);       //текущая концентрация по 3 каналу
            conc4 = ((answer[11] & 0xFF) << 8) +  (answer[10] & 0xFF);      //текущая концентрация по 4 каналу
            for(byte x = 0; x < 4; x++)
            {
                state[x] = answer[12 + x];
            }
            percent_charge = answer[20];

            updateErrorCache();
        }

        //Запрос на чтение параметров
        public void reqParam(byte Number)
        {
            byte[] request = new  byte[10];
            //формируем запрос
            request[0] =(byte) 46;               //первым байтом указываем длинну ответа 40 структура 6 обертка комнады
            request[1] =(byte) 0xFE;
            request[2] =(byte) 'R';              //Команда
            request[3] =(byte) ('0' + Number);
            request[4] =(byte) '#';              //-------
            request[5] =(byte) 0x0D;
            mCharacteristic.setValue(request);                          //заносим в характеристику
            mBluetoothGatt.writeCharacteristic(mCharacteristic);
        }

//        определяем формат пакета
    private int detectPacketOffset(byte[] answer) {

        // Вариант 1 — старый формат (структура сразу в [2])
        if (answer.length > 3 && (answer[2] == 1 || answer[2] == 2)) {
            return 0;
        }

        // Вариант 2 — ищем struct внутри пакета
        for (int i = 0; i < answer.length; i++) {
            if (answer[i] == 1 || answer[i] == 2) {
                return i - 2; // приводим к "старой" структуре
            }
        }

        // если вообще не нашли
        return -1;
    }

        //Парсим конкретную структуру с номером "num_struct"
        public boolean parseParam(byte[] answer, byte num_struct) throws UnsupportedEncodingException {
            Log.d("RAW_BYTES", Arrays.toString(answer));
            Log.d("RAW_TEXT", new String(answer));
            int shift = detectPacketOffset(answer);

            if (shift < 0) {
                Log.e("PARSE", "Не удалось определить формат пакета");
                return false;
            }

            int structIndex = shift + 2;

            if (answer.length <= structIndex || answer[structIndex] != num_struct) {
                Log.e("PARSE", "Структура не совпадает");
                return false;
            }

            byte i;

            byte[] gaz = new byte[7];
            byte[] unit = new byte[7];

            switch (num_struct) {

                case 1:
                    i = (byte)(shift + 8);

                    // универсальный парсинг номера
                    long parsed =
                            ((answer[shift + 7] & 0xFF) << 24) |
                                    ((answer[shift + 6] & 0xFF) << 16) |
                                    ((answer[shift + 5] & 0xFF) << 8) |
                                    (answer[shift + 4] & 0xFF);

                    if (parsed != 0) {
                        zavod_number = parsed;
                    }

                    // газы
                    for (byte x = 0; x < 4; x++) gazDiskret[x] = answer[i++];

                    for (byte x = 0; x < 2; x++) {
                        for (byte y = 0; y < 7; y++) gaz[y] = answer[i++];
                        for (byte z = 0; z < 7; z++) unit[z] = answer[i++];

                        gazType[x] = new String(gaz, "Windows-1251").trim();
                        gazUnit[x] = new String(unit, "Windows-1251").trim();
                    }
                    break;

                case 2:
                    i = (byte)(shift + 4);

                    for (byte x = 2; x < 4; x++) {
                        for (byte y = 0; y < 7; y++) gaz[y] = answer[i++];
                        for (byte z = 0; z < 7; z++) unit[z] = answer[i++];

                        gazType[x] = new String(gaz, "Windows-1251").trim();
                        gazUnit[x] = new String(unit, "Windows-1251").trim();
                    }
                    break;
            }

            return true;
        }
    int p = 0;
        //Переводим текущий статус в текстовый описатель ошибок
        public String Make_State()
        {
            Set<String> errors = new LinkedHashSet<>();

            int err = getCashedErrorBits();


            for (int i = 0; i < 32; i++)
            {
                if ((err & (1 << i)) != 0)
                {
                    if(localeRus){
                        if (i < array_of_Errors_RUS.length)
                        {
                            errors.add(array_of_Errors_RUS[i]);
                        }
                    }else{
                        if(i < array_of_Errors_EN.length)
                        {
                            errors.add(array_of_Errors_EN[i]);
                        }
                    }

                }
            }

            Set<String> prioritizedErrors = applyThresholdPriority(errors);

            if (prioritizedErrors.isEmpty())
            {
                status = "OK";
                return status;
            }

            String result = String.join("\n", prioritizedErrors);
            status = result;
            return result;
        }

    private Set<String> applyThresholdPriority(Set<String> errors)
    {
        Set<String> result = new LinkedHashSet<>();

        // Обрабатываем каждый сенсор
        for (int sensor = 0; sensor < 5; sensor++)
        {
            String firstThreshold = localeRus ?
                    array_of_Errors_RUS[sensor * 3] :
                    array_of_Errors_EN[sensor * 3];

            String secondThreshold = localeRus ?
                    array_of_Errors_RUS[sensor * 3 + 1] :
                    array_of_Errors_EN[sensor * 3 + 1];

            String rangeExceed = localeRus ?
                    array_of_Errors_RUS[sensor * 3 + 2] :
                    array_of_Errors_EN[sensor * 3 + 2];

            // Приоритет: Превышение диапазона > Второй порог > Первый порог
            if (errors.contains(rangeExceed))
            {
                result.add(rangeExceed);  // Показываем только превышение диапазона
            }
            else if (errors.contains(secondThreshold))
            {
                result.add(secondThreshold);  // Показываем только второй порог
            }
            else if (errors.contains(firstThreshold))
            {
                result.add(firstThreshold);  // Показываем только первый порог
            }
        }

        // Добавляем системные ошибки (не связанные с порогами)
        for (String error : errors)
        {
            if (!error.contains("Порог") && !error.contains("Превышение диапазона"))
            {
                result.add(error);
            }
        }

        return result;
    }

    private void updateErrorCache()
    {
        int currentBits = (state[0] & 0xFF) |
                ((state[1] & 0xFF) << 8) |
                ((state[2] & 0xFF) << 16) |
                ((state[3] & 0xFF) << 24);
        long now = System.currentTimeMillis();

        for (int i = 0; i < 32; i++)
        {
            if ((currentBits & (1 << i)) != 0)
            {
                cachedErrorBits |= (1 << i);
                lastErrorTime[i] = now;
            }
            else if ((cachedErrorBits & (1 << i)) != 0)
            {
                if (now - lastErrorTime[i] > ERROR_HOLD_TIME)
                {
                    cachedErrorBits &= ~(1 << i);
                }
            }
        }
    }

    public int getCashedErrorBits() {
        return cachedErrorBits;
    }

    public int getErrorBits() {
        return (state[0] & 0xFF) |
                ((state[1] & 0xFF) << 8) |
                ((state[2] & 0xFF) << 16) |
                ((state[3] & 0xFF) << 24);
    }

        //Чистим текст от постороних символов
        public void clean_text()
        {
            try {
                for (byte y = 0; y < 4; y++) {
                    gazType[y] = gazType[y].replaceAll("[^A-Za-zА-Яа-я0-9%.]", "");
                    gazUnit[y] = gazUnit[y].replaceAll("[^A-Za-zА-Яа-я0-9%.]", "");
                }
            } catch (Exception ex)
            {

            }
        }


    //Чтение характеристики
        public void startRead()
        {
            mBluetoothGatt.readCharacteristic(mCharacteristic);
        }
}


