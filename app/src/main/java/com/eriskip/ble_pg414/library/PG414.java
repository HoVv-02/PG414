package com.eriskip.ble_pg414.library;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.AsyncTask;
import android.util.Log;

import java.io.UnsupportedEncodingException;

public class PG414 {

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
        public byte[]  state = new byte[8];       //Состояние устройства
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
        public String status;

        public boolean localeRus;                             //Используется ли русский язык как локализация

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
                        "Низкий заряд",
                        "Критический заряд",
                        "Порог 1 - Электрохим. 1",
                        "Порог 2 - Электрохим. 1",
                        "Порог 1 - Электрохим. 2",
                        "Порог 2 - Электрохим. 1",
                        "Порог 1 - Кислород",
                        "Порог 2 - Кислород",
                        "Порог 1 - Пеллистор/мипекс",
                        "Порог 2 - Пеллистор/мипекс",
                        "НЕ ИСПОЛЬЗУЕТСЯ",
                        "Ошибка при чтении параметров из флеша",
                        "Время не установлено",
                        "Битая конфиг. таблица сенсора",
                        "Битая конфиг. таблица сенсора",
                        "Битая конфиг. таблица сенсора",
                        "Битая конфиг. таблица сенсора",
                        "Ошибка АЦП элх. сенсора",
                        "Ошибка конфигурации LMP",
                        "Ошибка АЦП элх. сенсора",
                        "Ошибка конфигурации LMP",
                        "Ошибка АЦП элх. сенсора",
                        "Ошибка АЦП элх. сенсора",
                        "Ошибка АЦП элх. сенсора",
                        "I2C не работает",
                        "I2C не работает",
                        "НЕ ИСПОЛЬЗУЕТСЯ",
                        "Превышение диапазона",
                        "Превышение диапазона",
                        "Превышение диапазона",
                        "Превышение диапазона",
                        "Ошибка неисправности И2Ц",
                        "Ошибка неисправности И2Ц",
                        "Ошибка неисправности И2Ц",
                        "Ошибка неисправности И2Ц",
                        "Ошибка неисправности И2Ц",
                        "Ошибка неисправности И2Ц",
                        "Ошибка неисправности И2Ц",
                        "Ошибка при чтении из флеш",
                        "НЕ ИСПОЛЬЗУЕТСЯ",
                        "Датчик темп. неисправен",
                        "Датчик давления неисправен",
                        "Ошибка при считывании лога",
                        "Ошибка модуля BLE"

                };
    public String[] array_of_Errors_EN =
            {
                    "Low charge",
                    "Critical charge",
                    "Alarm 1 - EC 1",
                    "Alarm 2 - EC. 1",
                    "Alarm 1 - EC. 2",
                    "Alarm 2 - EC. 1",
                    "Alarm 1 - Oxygen",
                    "Alarm 2 - Oxygen",
                    "Alarm 1 - Pellistor/Mipex",
                    "Alarm 2 - Pellistor/Mipex",
                    "NOT USED",
                    "Error flash reading",
                    "Time not set",
                    "Error sensor table",
                    "Error sensor table",
                    "Error sensor table",
                    "Error sensor table",
                    "DAC error EC sensors",
                    "Error config LMP",
                    "DAC error EC sensors",
                    "Error config  LMP",
                    "Error DAC EC sensors",
                    "Error DAC EC sensors",
                    "Error DAC EC sensors",
                    "I2C not work",
                    "I2C not work",
                    "NOT USED",
                    "Over range",
                    "Over range",
                    "Over range",
                    "Over range",
                    "Error I2C",
                    "Error I2C",
                    "Error I2C",
                    "Error I2C",
                    "Error I2C",
                    "Error I2C",
                    "Error I2C",
                    "Error flash reading",
                    "NOT USED",
                    "Temp sensor defective",
                    "Pressure sensor defective",
                    "Error reading log",
                    "Error module BLE"
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
            conc1 = ((answer[5]  & 0xFF) << 8) + (answer[4]  & 0xFF);       //текущая концентрация по 1 каналу
            conc2 = ((answer[7]  & 0xFF) << 8) + (answer[6]  & 0xFF);       //текущая концентрация по 2 каналу
            conc3 = ((answer[9]  & 0xFF) << 8) +  (answer[8] & 0xFF);       //текущая концентрация по 3 каналу
            conc4 = ((answer[11] & 0xFF) << 8) +  (answer[10] & 0xFF);      //текущая концентрация по 4 каналу
            for(byte x = 0; x < 8; x++)
            {
                state[x] = answer[12 + x];
            }
            percent_charge = answer[20];
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

        //Парсим конкретную структуру с номером "num_struct"
        public boolean parseParam(byte[] answer, byte num_struct) throws UnsupportedEncodingException {
            byte i;
            byte gaz[] = new byte [7]; byte unit[] = new byte [7];
            if (num_struct != answer[2]) return false;
            Log.d("BLE", "num_struct = " + num_struct +
                    " answer[2] = " + answer[2]);
            switch (num_struct)
            {
                case 1:
                            i = 8;
                                 zavod_number = (((answer[7]  & 0xFF) << 24) + ((answer[6]  & 0xFF) << 16) + ((answer[5]  & 0xFF) << 8) + (answer[4]  & 0xFF)) & 0xFFFFFFFFl;
                            for (byte x = 0; x < 4; x++) gazDiskret[x] = answer[i++];
                            for (byte x = 0; x < 2; x++)
                            {
                                for (byte y = 0; y < 7; y++) {
                                    gaz[y] = answer[i++];
                                }
                                for (byte z = 0; z < 7; z++) {
                                    unit[z] = answer[i++];
                                }

                                gazType[x] = new String(gaz, "Windows-1251");
                                gazUnit[x] = new String(unit, "Windows-1251");
                            }
                    break;

                case 2:
                    i = 4;
                    for (byte x = 2; x < 4; x++)
                    {
                        for (byte y = 0; y < 7; y++) {
                            gaz[y] = answer[i++];
                        }
                        for (byte z = 0; z < 7; z++) {
                            unit[z] = answer[i++];
                        }

                        gazType[x] = new String(gaz, "Windows-1251");
                        gazUnit[x] = new String(unit, "Windows-1251");
                    }
                    break;
            }
            return true;
        }
    int p = 0;
        //Переводим текущий статус в текстовый описатель ошибок
        public String Make_State()
        {
            String result = "";
            byte indx_err = 0;
            for (byte x = 0; x < 8; x++)
            {
                for (byte y = 0; y < 8; y++)
                {
                    if (((state[x] >> y) & 0x01) != 0)
                    if (localeRus)
                        if (indx_err < array_of_Errors_RUS.length)
                        result += array_of_Errors_RUS[indx_err]+"\n";
                    else
                        if (indx_err < array_of_Errors_EN.length)
                        result += array_of_Errors_EN[indx_err]+"\n";
                    indx_err++;
                }
            }
            if (result.length() == 21) result = "OK";   //Убираем время

            if (result.length() < 5)
            {
                p--;
                if (p <= 0)
                    result = "OK";
                else
                    result = "Сработка порогов";
            }
            else
                p = 10;
            status = result;
            return result;
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


