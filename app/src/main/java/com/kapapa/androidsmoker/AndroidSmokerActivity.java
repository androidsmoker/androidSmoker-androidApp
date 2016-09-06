package com.kapapa.androidsmoker;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.app.Activity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Main Activity of the Android Smoker
 *
 * Connects to the Arduino via the Android Accessory Kit (USB)
 * Acts as a bridge by exposing a websocket for the web clients to connect to
 *
 * Displays a basic UI of the temperature, fan speed and PID control
 *
 */
public class AndroidSmokerActivity extends Activity implements Runnable {
    private static final String TAG = "AndroidSmoker";

    private static final String ACTION_USB_PERMISSION = "com.kapapa.androidsmoker.action.USB_PERMISSION";


    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;
    BufferedReader br;

    StringBuffer buf = new StringBuffer();
    SmokerWebSocket webSocketServer = null;
    Thread thread = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "in onCreate...");
        updateText("onCreate");
        webSocketServer = new SmokerWebSocket(this);
        webSocketServer.start();

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Log.i(TAG, "mUsbManager: " + mUsbManager);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

        updateText("registerREceiver");
        Log.i(TAG, "calling regiserReceiver...");
        registerReceiver(mUsbReceiver, filter);

        if (getLastNonConfigurationInstance() != null) {
            mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
            openAccessory(mAccessory);
        }

        setContentView(R.layout.main);


        setupUI();


    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "in onResume...");
        updateText("onResume");

        Intent intent = getIntent();
        if (mFileDescriptor != null) {
            updateText("bailing, mFileDescriptor already setup.");
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        updateText("no accessories");
        if (accessories != null) {
            Log.i(TAG, "accessories: " + accessories.length);
            updateText("accessories: " + accessories.length);
        }

        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }


    }

    @Override
    public void onPause() {
        super.onPause();
        updateText("onPause");
        closeAccessory();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        // socketServer.close();

        try {
            webSocketServer.stop();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        super.onDestroy();
    }


    /**
     * Handle Permission granted to USB
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "in onReceive...");
            String action = intent.getAction();
            Log.i(TAG, "acition is: " + action);
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    UsbAccessory accessory = (UsbAccessory) intent
                            .getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "
                                + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                UsbAccessory accessory = (UsbAccessory) intent
                        .getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    updateText("usb detached.");
                    closeAccessory();
                }
            }
        }
    };

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();

                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mAccessory != null) {
            return mAccessory;
        } else {
            return super.onRetainNonConfigurationInstance();
        }
    }

    private void setupUI() {
        TextView textView = (TextView) findViewById(R.id.info);
        textView.setMovementMethod(new ScrollingMovementMethod());

        TextView addressView = (TextView) findViewById(R.id.address);
        addressView.setText("ws://" + getLocalIpAddress() + ":9999");

        setupSetpointPicker();
        setupFoodPicker((TextView) findViewById(R.id.labelFood1), (TextView) findViewById(R.id.probe1Temp), 1);
        setupFoodPicker((TextView) findViewById(R.id.labelFood2), (TextView) findViewById(R.id.probe2Temp), 2);
    }

    private void updateText(String msg) {
        buf.insert(0, msg + "\n");
        buf.setLength(1000);
        final TextView textView = (TextView) findViewById(R.id.info);
        if(textView != null) {
            textView.post(new Runnable() {
                public void run() {
                    textView.setText(buf.toString());
                }
            });
        }
    }

    private void updateSensor(Sensor sensor, String value) throws Exception {
        int id = 0;
        if (sensor == Sensor.PIT) {
            id = R.id.pitTemp;
            value += "°";
        } else if (sensor == Sensor.PROBE1) {
            id = R.id.probe1Temp;
            value += "°";
        } else if (sensor == Sensor.PROBE2) {
            id = R.id.probe2Temp;
            value += "°";
        } else if(sensor == Sensor.FANSPEED) {
            id = R.id.fanspeed;
            int fs = Integer.parseInt(value);

            int percent = (int)(fs/255.0*100);
            ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar1);
            pb.setProgress(fs);

            value = percent +"%";
        }
        else if (sensor == Sensor.CONTROL) {
            id = R.id.control;
        }
        else if (sensor == Sensor.SETPOINT) {
            value = "Set Point: "+value;
            id = R.id.setpoint;
        }
        else {
            throw new Exception("No UI for that Sensor.");
        }

        final String val = value;
        final TextView textView = (TextView) findViewById(id);
        //update is coming from non UI thread
        textView.post(new Runnable() {
            public void run() {
                textView.setText(val);
            }
        });
    }

    private void openAccessory(UsbAccessory accessory) {
        updateText("openAccessory");
        ;
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        updateText("file" + mFileDescriptor);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);

            updateText("inputStream: " + mInputStream);
            try {
                br = new BufferedReader(new InputStreamReader(mInputStream,
                        "US-ASCII"), 20);
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            mOutputStream = new FileOutputStream(fd);

            if (thread != null) {
                updateText("killing old thread..");
                thread.interrupt();
            }

            updateText("startThread...");
            thread = new Thread(null, this, "AndroidSmoker");
            thread.start();
            Log.d(TAG, "accessory opened");
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    private void closeAccessory() {
        updateText("closing accessory...");
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    protected void setupSetpointPicker() {
        TextView labelPit = (TextView) findViewById(R.id.pitTemp);
        labelPit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final EditText input = new EditText(AndroidSmokerActivity.this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setSelectAllOnFocus(true);

                final AlertDialog dialog = new AlertDialog.Builder(AndroidSmokerActivity.this).setTitle("Set Point").setMessage("How hot do you like it?").setView(input)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            sendCommand("setpoint=" + input.getText().toString());

                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing.
                        }
                    }).create();

                input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                        }
                    }
                });

                dialog.show();
            }

        });
    }

    protected void setupFoodPicker(final TextView label, final TextView temperature, final int probe) {

        View.OnClickListener listener = new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                final EditText input = new EditText(AndroidSmokerActivity.this);
                input.setText(label.getText());
                input.setSelectAllOnFocus(true);

                final AlertDialog dialog = new AlertDialog.Builder(AndroidSmokerActivity.this).setTitle("Probe " + probe).setMessage("What are ya sticking it to?").setView(input)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String newval = input.getText().toString();
                            label.setText(newval);
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing.
                        }
                    }).create();

                input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                        }
                    }
                });

                dialog.show();
            }

        };

        label.setOnClickListener(listener);
        temperature.setOnClickListener(listener);
    }

    /**
     * Runnable for Thread to read from USB Stream
     */
    public void run() {
        updateText("in Run...");
        Log.i(TAG, "in run...");

        try {


            String ln;
            Log.i(TAG, "do readLine: ");
            while ((ln = br.readLine()) != null) {
                Log.i(TAG, "got: " + ln);
//                updateText(ln);

                String[] vals = ln.split(",");

                String msgType = vals[0];
                if (msgType.equals("SENSOR")) {
                    if (vals.length != 7)
                        continue;

                    ArduinoData td = new ArduinoData();
                    td.raw = ln;
                    td.pit = vals[1];
                    td.food1 = vals[2];
                    td.food2 = vals[3];
                    td.fanSpeed = vals[4];
                    td.setpoint = vals[5];
                    td.control = vals[6];

                    updateSensor(Sensor.PIT, td.pit);
                    updateSensor(Sensor.PROBE1, td.food1);
                    updateSensor(Sensor.PROBE2, td.food2);
                    updateSensor(Sensor.FANSPEED, td.fanSpeed);
                    updateSensor(Sensor.CONTROL, td.control);
                    updateSensor(Sensor.SETPOINT, td.setpoint);

                    // td.ambient = vals[3];
                    // td.lidCountdown = vals[5];
                    // td.windSpeed = vals[6];

                    webSocketServer.write(td);
                } else if (msgType.equals("AUTOTUNE")) {
                    String kp = vals[1];
                    String ki = vals[2];
                    String kd = vals[3];

                    webSocketServer.write(kp, ki, kd);
                } else if (msgType.equals("PID")) {
                    if (vals.length != 7)
                        continue;

                    PIDData pd = new PIDData();
                    pd.input = vals[1];
                    pd.output = vals[2];
                    pd.setpoint = vals[3];
                    pd.error = vals[4];
                    pd.iterm = vals[5];
                    pd.dinput = vals[6];

                    webSocketServer.write(pd);

                }
//                updateText("wait for line...");
            }

            Log.i(TAG, "done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Write command to the USB for Arduino to handle
     * @param cmd string command to send
     */
    public void sendCommand(String cmd) {
        cmd += '\0';

        byte[] buffer = (cmd).getBytes();

        if (mOutputStream != null) {
            try {
                Log.i(TAG, "writing command...:");
                Log.i(TAG, "len: " + buffer.length);
                Log.i(TAG, "as string: '" + new String(buffer) + "'");

                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }

}
