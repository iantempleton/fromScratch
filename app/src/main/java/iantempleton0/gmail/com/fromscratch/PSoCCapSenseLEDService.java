package iantempleton0.gmail.com.fromscratch;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Service for managing the BLE data connection with the GATT database.
 *
 *
 *  Deprecated methods as of API level 21
 *      - startLeScan ====>  now use BluetoothLeScanner#startScan(List, ScanSettings, ScanCallback)
 *      - stopLeScan  ====>  now use BluetoothLeScanner#stopScan(ScanCallback)
 *
 *
 *
 *
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP) /** This is required to allow us to use the lollipop and later scan APIs */
public class PSoCCapSenseLEDService extends Service {
    private final static String TAG = PSoCCapSenseLEDService.class.getSimpleName();

    /** first create variables for the BLE objects
    // Bluetooth objects that we need to interact with */
    private static BluetoothManager mBluetoothManager;
    private static BluetoothAdapter mBluetoothAdapter;
    private static BluetoothLeScanner mLEScanner;
    private static BluetoothDevice mLeDevice;
    private static BluetoothGatt mBluetoothGatt;

    /** then create variable for the UUID for the services and characteristics
    // Bluetooth characteristics that we need to read/write*/
    private static BluetoothGattCharacteristic mDimmerCharacteristic; /** for dimmer characteristic */
    private static BluetoothGattCharacteristic mLedCharacteristic;

    /** UUIDs for the service and characteristics that the custom CapSenseLED service uses */
    private final static String baseUUID = "00000000-0000-1000-8000-00805f9b34f";
    private final static String capsenseLedServiceUUID = baseUUID + "0";
    public final static String ledCharacteristicUUID = baseUUID + "1";
    public final static String DimmerCharacteristicUUID = baseUUID + "3";

    /** Variables to keep track of the LED switch state and CapSense Value */
    private static boolean mLedSwitchState = false; // attempting to change type to int

    /** Actions used during broadcasts to the main activity */
    public final static String ACTION_BLESCAN_CALLBACK =
            "com.cypress.academy.ble101.ACTION_BLESCAN_CALLBACK";
    public final static String ACTION_CONNECTED =
            "com.cypress.academy.ble101.ACTION_CONNECTED";
    public final static String ACTION_DISCONNECTED =
            "com.cypress.academy.ble101.ACTION_DISCONNECTED";
    public final static String ACTION_SERVICES_DISCOVERED =
            "com.cypress.academy.ble101.ACTION_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_RECEIVED =
            "com.cypress.academy.ble101.ACTION_DATA_RECEIVED";

    public PSoCCapSenseLEDService() {
    }

    /**
     * This is a binder for the PSoCCapSenseLedService
     */
    public class LocalBinder extends Binder {
        PSoCCapSenseLEDService getService() {
            return PSoCCapSenseLEDService.this;
        }
    }



/**    Binder that binds the mainactivity to the service */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }



    @Override
    public boolean onUnbind(Intent intent) {
        /** The BLE close method is called when we unbind the service to free up the resources. */
        close();
        return super.onUnbind(intent);
    }



    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        /** For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager. */
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;


    }

    /**
     * Scans for BLE devices that support the service we are looking for
     *
     * the first if statement was changed from < to ></>= for functionality
     */
    public void scan() {
        /** Scan for devices and look for the one with the service that we want
         *   UUID ending in F0 i.e. the ledcapsense service*/
        UUID capsenseLedService = UUID.fromString(capsenseLedServiceUUID);
        UUID[] capsenseLedServiceArray = {capsenseLedService};

        /** Use old scan method for versions for different platform versions */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            /** noinspection deprecation */
            mBluetoothAdapter.startLeScan(capsenseLedServiceArray, mLeScanCallback);
        } else { /** New BLE scanning introduced in LOLLIPOP */
            ScanSettings settings;
            List<ScanFilter> filters;
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<>();
            /** We will scan just for the CAR's UUID */
            ParcelUuid PUuid = new ParcelUuid(capsenseLedService);
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(PUuid).build();
            filters.add(filter);
            mLEScanner.startScan(filters, settings, mScanCallback);
        }
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect() {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        /** Previously connected device.  Try to reconnect. */
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        /** We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false. */
        mBluetoothGatt = mLeDevice.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }

    /**
     * Runs service discovery on the connected device.
     */
    public void discoverServices() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.discoverServices();
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * This method is used to read the state of the LED from the device
     */
    public void readLedCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(mLedCharacteristic);
    }

    /**
     * This method is used to turn the LED on or off
     *
     * @param value Turns the LED on (1) or off (0)
     */
    public void writeLedCharacteristic(boolean value) {
        byte[] byteVal = new byte[1];
        if (value) {
            byteVal[0] = (byte) (1);
        } else {
            byteVal[0] = (byte) (0);
        }
        Log.i(TAG, "LED " + value);
        mLedSwitchState = value;
        mLedCharacteristic.setValue(byteVal);
        mBluetoothGatt.writeCharacteristic(mLedCharacteristic);
    }

    public void readDimmerCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(mLedCharacteristic);
    }

    /**
     * This function was setup to write the dimmer value based off the seekbar
     * @param value
     */
    public void writeDimmerCharacteristic(int value) {
        byte[] byteVal = new byte[1];
        switch(value){
            case 0:
                byteVal[0] = (byte) (0);
                break;
            case 1:
                byteVal[0] = (byte) (1);
                break;
            case 2:
                byteVal[0] = (byte) (2);
                break;
            case 3:
                byteVal[0] = (byte) (3);
                break;
            case 4:
                byteVal[0] = (byte) (4);
                break;
            case 5:
                byteVal[0] = (byte) (5);
                break;
            case 6:
                byteVal[0] = (byte) (6);
                break;
            case 7:
                byteVal[0] = (byte) (7);
                break;
            case 8:
                byteVal[0] = (byte) (8);
                break;
            case 9:
                byteVal[0] = (byte) (9);
                break;
            case 10:
                byteVal[0] = (byte) (10);
                break;
            case 11:
                byteVal[0] = (byte) (11);
                break;
            case 12:
                byteVal[0] = (byte) (12);
                break;
            case 13:
                byteVal[0] = (byte) (13);
                break;
            case 14:
                byteVal[0] = (byte) (14);
                break;
            case 15:
                byteVal[0] = (byte) (15);
                break;
            case 16:
                byteVal[0] = (byte) (16);
                break;
            case 17:
                byteVal[0] = (byte) (17);
                break;
            case 18:
                byteVal[0] = (byte) (18);
                break;
            case 19:
                byteVal[0] = (byte) (19);
                break;
            case 20:
                byteVal[0] = (byte) (20);
                break;
            case 21:
                byteVal[0] = (byte) (21);
                break;
            case 22:
                byteVal[0] = (byte) (22);
                break;
            case 23:
                byteVal[0] = (byte) (23);
                break;
            case 24:
                byteVal[0] = (byte) (24);
                break;
            case 25:
                byteVal[0] = (byte) (25);
                break;
            case 26:
                byteVal[0] = (byte) (26);
                break;
            case 27:
                byteVal[0] = (byte) (27);
                break;
            case 28:
                byteVal[0] = (byte) (28);
                break;
            case 29:
                byteVal[0] = (byte) (29);
                break;
            case 30:
                byteVal[0] = (byte) (30);
                break;
            case 31:
                byteVal[0] = (byte) (31);
                break;
            case 32:
                byteVal[0] = (byte) (32);
                break;
            case 33:
                byteVal[0] = (byte) (33);
                break;
            case 34:
                byteVal[0] = (byte) (34);
                break;
            case 35:
                byteVal[0] = (byte) (35);
                break;
            case 36:
                byteVal[0] = (byte) (36);
                break;
            case 37:
                byteVal[0] = (byte) (37);
                break;
            case 38:
                byteVal[0] = (byte) (38);
                break;
            case 39:
                byteVal[0] = (byte) (39);
                break;
            case 40:
                byteVal[0] = (byte) (40);
                break;
            case 41:
                byteVal[0] = (byte) (41);
                break;
            case 42:
                byteVal[0] = (byte) (42);
                break;
            case 43:
                byteVal[0] = (byte) (43);
                break;
            case 44:
                byteVal[0] = (byte) (44);
                break;
            case 45:
                byteVal[0] = (byte) (45);
                break;
            case 46:
                byteVal[0] = (byte) (46);
                break;
            case 47:
                byteVal[0] = (byte) (47);
                break;
            case 48:
                byteVal[0] = (byte) (48);
                break;
            case 49:
                byteVal[0] = (byte) (49);
                break;
            case 50:
                byteVal[0] = (byte) (50);
                break;
            case 51:
                byteVal[0] = (byte) (51);
                break;
            case 52:
                byteVal[0] = (byte) (52);
                break;
            case 53:
                byteVal[0] = (byte) (53);
                break;
            case 54:
                byteVal[0] = (byte) (54);
                break;
            case 55:
                byteVal[0] = (byte) (55);
                break;
            case 56:
                byteVal[0] = (byte) (56);
                break;
            case 57:
                byteVal[0] = (byte) (57);
                break;
            case 58:
                byteVal[0] = (byte) (58);
                break;
            case 59:
                byteVal[0] = (byte) (59);
                break;
            case 60:
                byteVal[0] = (byte) (60);
                break;
            case 61:
                byteVal[0] = (byte) (61);
                break;
            case 62:
                byteVal[0] = (byte) (62);
                break;
            case 63:
                byteVal[0] = (byte) (63);
                break;
            case 64:
                byteVal[0] = (byte) (64);
                break;
            case 65:
                byteVal[0] = (byte) (65);
                break;
            case 66:
                byteVal[0] = (byte) (66);
                break;
            case 67:
                byteVal[0] = (byte) (67);
                break;
            case 68:
                byteVal[0] = (byte) (68);
                break;
            case 69:
                byteVal[0] = (byte) (69);
                break;
            case 70:
                byteVal[0] = (byte) (70);
                break;
            case 71:
                byteVal[0] = (byte) (71);
                break;
            case 72:
                byteVal[0] = (byte) (72);
                break;
            case 73:
                byteVal[0] = (byte) (73);
                break;
            case 74:
                byteVal[0] = (byte) (74);
                break;
            case 75:
                byteVal[0] = (byte) (75);
                break;
            case 76:
                byteVal[0] = (byte) (76);
                break;
            case 77:
                byteVal[0] = (byte) (77);
                break;
            case 78:
                byteVal[0] = (byte) (78);
                break;
            case 79:
                byteVal[0] = (byte) (79);
                break;
            case 80:
                byteVal[0] = (byte) (80);
                break;
            case 81:
                byteVal[0] = (byte) (81);
                break;
            case 82:
                byteVal[0] = (byte) (82);
                break;
            case 83:
                byteVal[0] = (byte) (83);
                break;
            case 84:
                byteVal[0] = (byte) (84);
                break;
            case 85:
                byteVal[0] = (byte) (85);
                break;
            case 86:
                byteVal[0] = (byte) (86);
                break;
            case 87:
                byteVal[0] = (byte) (87);
                break;
            case 88:
                byteVal[0] = (byte) (88);
                break;
            case 89:
                byteVal[0] = (byte) (89);
                break;
            case 90:
                byteVal[0] = (byte) (90);
                break;
            case 91:
                byteVal[0] = (byte) (91);
                break;
            case 92:
                byteVal[0] = (byte) (92);
                break;
            case 93:
                byteVal[0] = (byte) (93);
                break;
            case 94:
                byteVal[0] = (byte) (94);
                break;
            case 95:
                byteVal[0] = (byte) (95);
                break;
            case 96:
                byteVal[0] = (byte) (96);
                break;
            case 97:
                byteVal[0] = (byte) (97);
                break;
            case 98:
                byteVal[0] = (byte) (98);
                break;
            case 99:
                byteVal[0] = (byte) (99);
                break;
            case 100:
                byteVal[0] = (byte) (100);
                break;
        }
        Log.i(TAG, "DIMMER " + value);
        mLedCharacteristic.setValue(byteVal);
        mBluetoothGatt.writeCharacteristic(mLedCharacteristic);
    }

    public void readColorCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(mDimmerCharacteristic);
    }
    public void writeColorCharacteristic(int value) {
        byte[] byteVal = new byte[1];

        switch(value){

            case 0:
                byteVal[0] = (byte) (0);
                break;
            case 1:
                byteVal[0] = (byte) (1);
                break;
            case 2:
                byteVal[0] = (byte) (2);
                break;
            case 3:
                byteVal[0] = (byte) (3);
                break;
            case 4:
                byteVal[0] = (byte) (4);
                break;
            case 5:
                byteVal[0] = (byte) (5);
                break;
            case 6:
                byteVal[0] = (byte) (6);
                break;
            case 7:
                byteVal[0] = (byte) (7);
                break;
            case 8:
                byteVal[0] = (byte) (8);
                break;
            case 9:
                byteVal[0] = (byte) (9);
                break;
            case 10:
                byteVal[0] = (byte) (10);
                break;
            case 11:
                byteVal[0] = (byte) (11);
                break;
            case 12:
                byteVal[0] = (byte) (12);
                break;
            case 13:
                byteVal[0] = (byte) (13);
                break;
            case 14:
                byteVal[0] = (byte) (14);
                break;
            case 15:
                byteVal[0] = (byte) (15);
                break;
            case 16:
                byteVal[0] = (byte) (16);
                break;
            case 17:
                byteVal[0] = (byte) (17);
                break;
            case 18:
                byteVal[0] = (byte) (18);
                break;
            case 19:
                byteVal[0] = (byte) (19);
                break;
            case 20:
                byteVal[0] = (byte) (20);
                break;
            case 21:
                byteVal[0] = (byte) (21);
                break;
            case 22:
                byteVal[0] = (byte) (22);
                break;
            case 23:
                byteVal[0] = (byte) (23);
                break;
            case 24:
                byteVal[0] = (byte) (24);
                break;
            case 25:
                byteVal[0] = (byte) (25);
                break;
            case 26:
                byteVal[0] = (byte) (26);
                break;
            case 27:
                byteVal[0] = (byte) (27);
                break;
            case 28:
                byteVal[0] = (byte) (28);
                break;
            case 29:
                byteVal[0] = (byte) (29);
                break;
            case 30:
                byteVal[0] = (byte) (30);
                break;
            case 31:
                byteVal[0] = (byte) (31);
                break;
            case 32:
                byteVal[0] = (byte) (32);
                break;
            case 33:
                byteVal[0] = (byte) (33);
                break;
            case 34:
                byteVal[0] = (byte) (34);
                break;
            case 35:
                byteVal[0] = (byte) (35);
                break;
            case 36:
                byteVal[0] = (byte) (36);
                break;
            case 37:
                byteVal[0] = (byte) (37);
                break;
            case 38:
                byteVal[0] = (byte) (38);
                break;
            case 39:
                byteVal[0] = (byte) (39);
                break;
            case 40:
                byteVal[0] = (byte) (40);
                break;
            case 41:
                byteVal[0] = (byte) (41);
                break;
            case 42:
                byteVal[0] = (byte) (42);
                break;
            case 43:
                byteVal[0] = (byte) (43);
                break;
            case 44:
                byteVal[0] = (byte) (44);
                break;
            case 45:
                byteVal[0] = (byte) (45);
                break;
            case 46:
                byteVal[0] = (byte) (46);
                break;
            case 47:
                byteVal[0] = (byte) (47);
                break;
            case 48:
                byteVal[0] = (byte) (48);
                break;
            case 49:
                byteVal[0] = (byte) (49);
                break;
            case 50:
                byteVal[0] = (byte) (50);
                break;
            case 51:
                byteVal[0] = (byte) (51);
                break;
            case 52:
                byteVal[0] = (byte) (52);
                break;
            case 53:
                byteVal[0] = (byte) (53);
                break;
            case 54:
                byteVal[0] = (byte) (54);
                break;
            case 55:
                byteVal[0] = (byte) (55);
                break;
            case 56:
                byteVal[0] = (byte) (56);
                break;
            case 57:
                byteVal[0] = (byte) (57);
                break;
            case 58:
                byteVal[0] = (byte) (58);
                break;
            case 59:
                byteVal[0] = (byte) (59);
                break;
            case 60:
                byteVal[0] = (byte) (60);
                break;
            case 61:
                byteVal[0] = (byte) (61);
                break;
            case 62:
                byteVal[0] = (byte) (62);
                break;
            case 63:
                byteVal[0] = (byte) (63);
                break;
            case 64:
                byteVal[0] = (byte) (64);
                break;
            case 65:
                byteVal[0] = (byte) (65);
                break;
            case 66:
                byteVal[0] = (byte) (66);
                break;
            case 67:
                byteVal[0] = (byte) (67);
                break;
            case 68:
                byteVal[0] = (byte) (68);
                break;
            case 69:
                byteVal[0] = (byte) (69);
                break;
            case 70:
                byteVal[0] = (byte) (70);
                break;
            case 71:
                byteVal[0] = (byte) (71);
                break;
            case 72:
                byteVal[0] = (byte) (72);
                break;
            case 73:
                byteVal[0] = (byte) (73);
                break;
            case 74:
                byteVal[0] = (byte) (74);
                break;
            case 75:
                byteVal[0] = (byte) (75);
                break;
            case 76:
                byteVal[0] = (byte) (76);
                break;
            case 77:
                byteVal[0] = (byte) (77);
                break;
            case 78:
                byteVal[0] = (byte) (78);
                break;
            case 79:
                byteVal[0] = (byte) (79);
                break;
            case 80:
                byteVal[0] = (byte) (80);
                break;
            case 81:
                byteVal[0] = (byte) (81);
                break;
            case 82:
                byteVal[0] = (byte) (82);
                break;
            case 83:
                byteVal[0] = (byte) (83);
                break;
            case 84:
                byteVal[0] = (byte) (84);
                break;
            case 85:
                byteVal[0] = (byte) (85);
                break;
            case 86:
                byteVal[0] = (byte) (86);
                break;
            case 87:
                byteVal[0] = (byte) (87);
                break;
            case 88:
                byteVal[0] = (byte) (88);
                break;
            case 89:
                byteVal[0] = (byte) (89);
                break;
            case 90:
                byteVal[0] = (byte) (90);
                break;
            case 91:
                byteVal[0] = (byte) (91);
                break;
            case 92:
                byteVal[0] = (byte) (92);
                break;
            case 93:
                byteVal[0] = (byte) (93);
                break;
            case 94:
                byteVal[0] = (byte) (94);
                break;
            case 95:
                byteVal[0] = (byte) (95);
                break;
            case 96:
                byteVal[0] = (byte) (96);
                break;
            case 97:
                byteVal[0] = (byte) (97);
                break;
            case 98:
                byteVal[0] = (byte) (98);
                break;
            case 99:
                byteVal[0] = (byte) (99);
                break;
            case 100:
                byteVal[0] = (byte) (100);
                break;

        }
        Log.i(TAG, "COLOR " + value);
        mDimmerCharacteristic.setValue(byteVal);
        mBluetoothGatt.writeCharacteristic(mDimmerCharacteristic);
    }

    /**
     * This method returns the state of the LED switch
     *
     * @return the value of the LED swtich state
     */
    public boolean getLedSwitchState() {
        return mLedSwitchState;
    }
    /**
     * Implements the callback for when scanning for devices has found a device with
     * the service we are looking for.
     * <p>
     * This is the callback for BLE scanning on versions prior to Lollipop
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    mLeDevice = device;
                    //noinspection deprecation
                    mBluetoothAdapter.stopLeScan(mLeScanCallback); // Stop scanning after the first device is found
                    broadcastUpdate(ACTION_BLESCAN_CALLBACK); // Tell the main activity that a device has been found
                }
            };

    /**
     * Implements the callback for when scanning for devices has faound a device with
     * the service we are looking for.
     * <p>
     * This is the callback for BLE scanning for LOLLIPOP and later
     */


    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mLeDevice = result.getDevice();
            mLEScanner.stopScan(mScanCallback); /** Stop scanning after the first device is found */
            broadcastUpdate(ACTION_BLESCAN_CALLBACK); /** Tell the main activity that a device has been found */
        }
    };


    /**
     * Implements callback methods for GATT events that the app cares about.  For example,
     * connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_DISCONNECTED);
            }
        }

        /**
         * This is called when a service discovery has completed.
         *
         * It gets the characteristics we are interested in and then
         * broadcasts an update to the main activity.
         *
         * @param gatt The GATT database object
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            /** Get just the service that we are looking for */
            BluetoothGattService mService = gatt.getService(UUID.fromString(capsenseLedServiceUUID));
            /** Get characteristics from our desired service */
            mDimmerCharacteristic = mService.getCharacteristic(UUID.fromString(DimmerCharacteristicUUID));
            mLedCharacteristic = mService.getCharacteristic(UUID.fromString(ledCharacteristicUUID));

            /** Read the current state of the LED from the device */
            readLedCharacteristic();
            readDimmerCharacteristic();
            readColorCharacteristic();

            /** Broadcast that service/characteristic/descriptor discovery is done */
            broadcastUpdate(ACTION_SERVICES_DISCOVERED);
        }

        /**
         * This is called when a read completes
         *
         * @param gatt the GATT database object
         * @param characteristic the GATT characteristic that was read
         * @param status the status of the transaction
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                /** Verify that the read was the LED state */
                String uuid = characteristic.getUuid().toString();
                /** In this case, the only read the app does is the LED state.
                // If the application had additional characteristics to read we could
                // use a switch statement here to operate on each one separately. */
                if (uuid.equalsIgnoreCase(ledCharacteristicUUID)) {
                    final byte[] data = characteristic.getValue();
                    /** Set the LED switch state variable based on the characteristic value that was read */
                    mLedSwitchState = ((data[0] & 0xff) != 0x00);
                }
                /** Notify the main activity that new data is available */
                broadcastUpdate(ACTION_DATA_RECEIVED);
            }
        }

        /**
         * This is called when a characteristic with notify set changes.
         * It broadcasts an update to the main activity with the changed data.
         *
         * @param gatt The GATT database object
         * @param characteristic The characteristic that was changed
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            /** Notify the main activity that new data is available */
            broadcastUpdate(ACTION_DATA_RECEIVED);
        }
    }; /** End of GATT event callback methods */

    /**
     * Sends a broadcast to the listener in the main activity.
     *
     * @param action The type of action that occurred.
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

}