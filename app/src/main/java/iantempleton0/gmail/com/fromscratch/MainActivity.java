package iantempleton0.gmail.com.fromscratch;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;

import java.text.MessageFormat;

import androidx.annotation.NonNull;

public class MainActivity extends AppCompatActivity {


    /**
     * TAG is used for informational messages
     */
    private final static String TAG = MainActivity.class.getSimpleName();

    /**
     * Variables to access objects from the layout such as buttons, switches, values
     */
    private static TextView mCapsenseValue;
    private TextView brightness;
    private TextView color;
    private ProgressBar progressBar;
    private ProgressBar progressBar1;
    private SeekBar seekBar;
    private SeekBar seekBar1;
    private static TextView textView;
    private static Button start_button;
    private static Button search_button;
    //private static Button connect_button;
    //private static Button discover_button;
    private static Button disconnect_button;
    private static Switch led_switch;
    private static Switch cap_switch;

    /**
     * Variables to manage BLE connection
     */
    private static boolean mConnectState;
    private static boolean mServiceConnected;
    private static PSoCCapSenseLEDService mPSoCCapSenseLedService;

    private static final int REQUEST_ENABLE_BLE = 1;

    /**
     * This is required for Android 6.0 (Marshmallow)
     */
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    /**
     * Keep track of whether CapSense Notifications are on or off
     */
    private static boolean CapSenseNotifyState = false;


   /** public void seekbar(){
        seekBar1 = (SeekBar)findViewById(R.id.seekBar1);
        textView = (TextView)findViewById(R.id.textView);
        // casts the seekbar and textview variables
        textView.setText("Covered: " + seekBar1.getProgress() + " / " +seekBar1.getMax());

        //seekbar listener
        seekBar1.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
        int progressValue;

        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            progressValue=progress;
            //mPSoCCapSenseLedService.writeDimmerCharacteristic(progress); crash
            //mPSoCCapSenseLedService.writeDimmerCharacteristic(progressValue); crash
            textView.setText("Covered: " + progress + " / " +seekBar1.getMax());
            Toast.makeText(MainActivity.this, "Seekbar in progress", Toast.LENGTH_LONG).show();
            //mPSoCCapSenseLedService.writeDimmerCharacteristic(progress); // causes crash



         //  public void onCheckedChanged(CompoundButton textView, int fingerPos) {
     //    we want to vary the LED illumination based of fingerPos
     //     *
     //      * problem with this code here !!!!!!
     //      *
     //      *
     //     mPSoCCapSenseLedService.writeDimmerCharacteristic(fingerPos);
     // }
     }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {
            Toast.makeText(MainActivity.this, "Seekbar in StartTracking", Toast.LENGTH_LONG).show();
            //mPSoCCapSenseLedService.writeDimmerCharacteristic(progressValue); crash
     }

        @Override public void onStopTrackingTouch(SeekBar seekBar) {
            textView.setText("Covered: " + progressValue + " / " +seekBar1.getMax());
            Toast.makeText(MainActivity.this, "Seekbar in StopTracking", Toast.LENGTH_LONG).show();
            //mPSoCCapSenseLedService.writeDimmerCharacteristic(progressValue); crash

     }
     }
     );

     } */


    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object and initialize the service.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        /**
         * This is called when the PSoCCapSenseLedService is connected
         *
         * @param componentName the component name of the service that has been connected
         * @param service service being bound
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mPSoCCapSenseLedService = ((PSoCCapSenseLEDService.LocalBinder) service).getService();
            mServiceConnected = true;
            mPSoCCapSenseLedService.initialize();

//            mPSoCCapSenseLedService.scan(); // no crash!!!
//            mPSoCCapSenseLedService.connect(); //  crash
//            mPSoCCapSenseLedService.discoverServices();
        }

        /**
         * This is called when the PSoCCapSenseService is disconnected.
         *
         * @param componentName the component name of the service that has been connected
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected");
            mPSoCCapSenseLedService = null;
        }
    };


    /**
     * This is called when the main activity is first created
     *
     * @param savedInstanceState is any state saved from prior creations of this activity
     */
    @TargetApi(Build.VERSION_CODES.M) /** This is required for Android 6.0 (Marshmallow) to work */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** connects variables to the xml or interface of the application */
        //mCapsenseValue = (TextView) findViewById(R.id.capsense_value);

        /** Set up variables for accessing buttons and slide switches */
        start_button = (Button) findViewById(R.id.start_button);
        search_button = (Button) findViewById(R.id.search_button);
        //connect_button = (Button) findViewById(R.id.connect_button);
        //discover_button = (Button) findViewById(R.id.discoverSvc_button);
        disconnect_button = (Button) findViewById(R.id.disconnect_button);
        led_switch = (Switch) findViewById(R.id.led_switch);
        cap_switch = (Switch) findViewById(R.id.capsense_switch);
        textView = findViewById(R.id.textView);
        progressBar1 = (ProgressBar)findViewById(R.id.progressBar1);
        seekBar1 = (SeekBar)findViewById(R.id.seekBar1);

        seekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar1, int progress, boolean fromUser) {
               progressBar1.setProgress(progress);
               textView.setText("Brightness: "+progress+"%");
               mPSoCCapSenseLedService.writeDimmerCharacteristic(progress);

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar1) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar1) {
//                int progress = seekBar1.getProgress();
//                progressBar1.setProgress(progress);
//                textView.setText("Brightness: "+progress+"%");
//                mPSoCCapSenseLedService.writeDimmerCharacteristic(progress);
            }
        });



         /** Initialize service and connection state variable */
        mServiceConnected = false;
        mConnectState = false;

        /**This section required for Android 6.0 (Marshmallow) */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** Android M Permission check  */
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access ");
                builder.setMessage("Please grant location access so this app can detect devices.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        } /** End of section for Android 6.0 (Marshmallow) */



        /** This will be called when the LED On/Off switch is touched
        *  Need to set up a call for a seekbar listener that can handle int
        * */
        led_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                /** Turn the LED on or OFF based on the state of the switch */
                mPSoCCapSenseLedService.writeLedCharacteristic(isChecked);
            }
        });





        /** This will be called when the CapSense Notify On/Off switch is touched */
        cap_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                /** Turn CapSense Notifications on/off based on the state of the switch */
                mPSoCCapSenseLedService.writeCapSenseNotification(isChecked);
                CapSenseNotifyState = isChecked;  /** Keep track of CapSense notification state */
                if (isChecked) { /** Notifications are now on so text has to say "No Touch" */
                    mCapsenseValue.setText(R.string.NoTouch);
                } else { /** Notifications are now off so text has to say "Notify Off" */
                    mCapsenseValue.setText(R.string.NotifyOff);
                }
            }
        });
        //seekbar(); //only used if the seekbar is outside of oncreate
    }

    /**This method required for Android 6.0 (Marshmallow) */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission for 6.0:", "Coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
            }
        }
    } /**End of section for Android 6.0 (Marshmallow) */

    @Override
    protected void onResume() {
        super.onResume();
        /** Register the broadcast receiver. This specified the messages the main activity looks for from the PSoCCapSenseLedService */
        final IntentFilter filter = new IntentFilter();
        filter.addAction(PSoCCapSenseLEDService.ACTION_BLESCAN_CALLBACK);
        filter.addAction(PSoCCapSenseLEDService.ACTION_CONNECTED);
        filter.addAction(PSoCCapSenseLEDService.ACTION_DISCONNECTED);
        filter.addAction(PSoCCapSenseLEDService.ACTION_SERVICES_DISCOVERED);
        filter.addAction(PSoCCapSenseLEDService.ACTION_DATA_RECEIVED);
        registerReceiver(mBleUpdateReceiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /** User chose not to enable Bluetooth. */
        if (requestCode == REQUEST_ENABLE_BLE && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBleUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /** Close and unbind the service when the activity goes away */
        mPSoCCapSenseLedService.close();
        unbindService(mServiceConnection);
        mPSoCCapSenseLedService = null;
        mServiceConnected = false;
    }

    /**
     * This method handles the start bluetooth button
     *
     * @param view the view object
     */
    public void startBluetooth(View view) {

        /** Find BLE service and adapter */
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        /** Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it. */
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLE);
        }

        /** Start the BLE Service */
        Log.d(TAG, "Starting BLE Service");
        Intent gattServiceIntent = new Intent(this, PSoCCapSenseLEDService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        /** Disable the start button and turn on the search  button */
        start_button.setEnabled(false);
        search_button.setEnabled(true);
        Log.d(TAG, "Bluetooth is Enabled");



        // search function does not crash app
        // cannot force step into if statement, is mServiceConnected true?
        // no mServiceConnected appears to false
        // okay let's force this if statement to be executed
        // but first setting code back to normal and seeing how mServiceConnected is set
      //  if (mServiceConnected) {
        //mPSoCCapSenseLedService = ((PSoCCapSenseLEDService.LocalBinder) service).getService(); // service not recognized
//        mServiceConnected = true;

//        new CountDownTimer(15, 1000) {
//            public void onFinish() {
//                // When timer is finished
//                // Execute your code here
//                mPSoCCapSenseLedService.scan();
//            }
//
//            public void onTick(long millisUntilFinished) {
//                // millisUntilFinished    The amount of time until finished.
//            }
//        }.start();
//        // roughly 65ms after scanning
//        new CountDownTimer(80, 1000) {
//            public void onFinish() {
//                // When timer is finished
//                // Execute your code here
//                mPSoCCapSenseLedService.connect();
//            }
//
//            public void onTick(long millisUntilFinished) {
//                // millisUntilFinished    The amount of time until finished.
//            }
//        }.start();
//            // needs to be roughly 260ms after connection
//        new CountDownTimer(340, 1000) {
//            public void onFinish() {
//                // When timer is finished
//                // Execute your code here
//                mPSoCCapSenseLedService.discoverServices();
//            }
//
//            public void onTick(long millisUntilFinished) {
//                // millisUntilFinished    The amount of time until finished.
//            }
//        }.start();


        // crash

    }

    /**
     *
     * This method will handle the power on power of while keeping the BLE
     * connection intact
     *
     * @param view
     */

    /**
    public void powerOn(View view) {

        }

    /**
     * This method handles the Search for Device button
     *
     * @param view the view object

*/

    public void searchBluetooth(View view) {
       if (mServiceConnected) {
            mPSoCCapSenseLedService.scan();

        }
//new CountDownTimer(15, 1000) {
//        public void onFinish() {
//            // When timer is finished
//            // Execute your code here
//            mPSoCCapSenseLedService.scan();
//        }
//
//        public void onTick(long millisUntilFinished) {
//            // millisUntilFinished    The amount of time until finished.
//        }
//    }.start();
    // roughly 65ms after scanning

        new CountDownTimer(60, 1000) {
        public void onFinish() {
            // When timer is finished
            // Execute your code here
            mPSoCCapSenseLedService.connect();
        }

        public void onTick(long millisUntilFinished) {
            // millisUntilFinished    The amount of time until finished.
        }
    }.start();
    // needs to be roughly 260ms after connection
        new CountDownTimer(340, 1000) {
        public void onFinish() {
            // When timer is finished
            // Execute your code here
            mPSoCCapSenseLedService.discoverServices();
        }

        public void onTick(long millisUntilFinished) {
            // millisUntilFinished    The amount of time until finished.
        }
    }.start();
//
   }




    /**
     * This method handles the Connect to Device button
     *
     * @param view the view object
     */


//    public void connectBluetooth(View view) {
//        mPSoCCapSenseLedService.connect();
//
//        /** After this we wait for the gatt callback to report the device is connected
//         That event broadcasts a message which is picked up by the mGattUpdateReceiver */
//
//    }




    /**
     * This method handles the Discover Services and Characteristics button
     *
     * @param view the view object
     */



//     public void discoverServices(View view) {
//         /** This will discover both services and characteristics*/
//         mPSoCCapSenseLedService.discoverServices();
//
//         /** After this we wait for the gatt callback to report the services and characteristics
//          That event broadcasts a message which is picked up by the mGattUpdateReceiver */
//
//     }




    /**
     * This method handles the Disconnect button
     *
     * @param view the view object
     */
    public void Disconnect(View view) {
        mPSoCCapSenseLedService.disconnect();

        /** After this we wait for the gatt callback to report the device is disconnected
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * Listener for BLE event broadcasts
     */
    private final BroadcastReceiver mBleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case PSoCCapSenseLEDService.ACTION_BLESCAN_CALLBACK:
                    /** Disable the search button and enable the connect button */
                    search_button.setEnabled(false);
                    //connect_button.setEnabled(true);
                    break;

                case PSoCCapSenseLEDService.ACTION_CONNECTED:
                    /** This if statement is needed because we sometimes get a GATT_CONNECTED
                    /* action when sending Capsense notifications */
                    if (!mConnectState) {
                        /** Disable the connect button, enable the discover services and disconnect buttons */
                        //connect_button.setEnabled(false);
                        //discover_button.setEnabled(true);
                        disconnect_button.setEnabled(true);
                        mConnectState = true;
                        Log.d(TAG, "Connected to Device");
                    }
                    break;
                case PSoCCapSenseLEDService.ACTION_DISCONNECTED:
                    /** Disable the disconnect, discover svc, discover char button, and enable the search button */
                    disconnect_button.setEnabled(false);
                   //discover_button.setEnabled(false);
                    search_button.setEnabled(true);
                    /** Turn off and disable the LED and CapSense switches */
                    led_switch.setChecked(false);
                    led_switch.setEnabled(false);
                    cap_switch.setChecked(false);
                    cap_switch.setEnabled(false);
                    mConnectState = false;
                    Log.d(TAG, "Disconnected");
                    break;
                case PSoCCapSenseLEDService.ACTION_SERVICES_DISCOVERED:
                    /** Disable the discover services button */
                    //discover_button.setEnabled(false);
                    /** Enable the LED and CapSense switches */
                    led_switch.setEnabled(true);
                    cap_switch.setEnabled(true);
                    Log.d(TAG, "Services Discovered");
                    break;
                case PSoCCapSenseLEDService.ACTION_DATA_RECEIVED:
                    /** This is called after a notify or a read completes
                    // Check LED switch Setting */
                    if (mPSoCCapSenseLedService.getLedSwitchState()) {
                        led_switch.setChecked(true);
                    } else {
                        led_switch.setChecked(false);
                    }
                    /** Get CapSense Slider Value */
                    String CapSensePos = mPSoCCapSenseLedService.getCapSenseValue();
                    if (CapSensePos.equals("-1")) {  /** No Touch returns 0xFFFF which is -1 */
                        if (!CapSenseNotifyState) { /** Notifications are off */
//                            mCapsenseValue.setText(R.string.NotifyOff);
                        } else { /** Notifications are on but there is no finger on the slider */
                            mCapsenseValue.setText(R.string.NoTouch);
                        }
                    } else { /** Valid CapSense value is returned */
                        mCapsenseValue.setText(CapSensePos);
                    }
                default:
                    break;
            }
        }
    };
}