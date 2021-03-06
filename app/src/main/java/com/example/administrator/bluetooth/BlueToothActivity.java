package com.example.administrator.bluetooth;


        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.BluetoothServerSocket;
        import android.bluetooth.BluetoothSocket;
        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.os.Handler;
        import android.os.HandlerThread;
        import android.os.Looper;
        import android.os.Message;
        import android.support.v7.app.AppCompatActivity;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.View;
        import android.widget.AdapterView;
        import android.widget.ArrayAdapter;
        import android.widget.Button;
        import android.widget.EditText;
        import android.widget.ListView;
        import android.widget.Toast;


        import java.io.IOException;
        import java.io.OutputStream;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Objects;
        import java.util.UUID;


public class BlueToothActivity extends AppCompatActivity {
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket socket;
    private Thread AcceptPushThread;
    private List<String> devices;
    private List<BluetoothDevice> deviceList;
    private ArrayAdapter<String> mAdaptor;
    private ListView listview;
    //想调用蓝牙模块，就必须获得下面的adapter实例。
    private BluetoothAdapter bluetoothAdapter;
    private BlueToothReceiver blueToothReceiver;

    private  Object obj = new Object();
    private boolean search_mutex;
    private volatile boolean IsOpen;
    private Handler subhandler;

    //宠物
    private PetInfo pet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blue_tooth);

        devices = new ArrayList<String>();
        deviceList = new ArrayList<BluetoothDevice>();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent,1);
        }
        Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        enable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600); //3600为蓝牙设备可见时间
        startActivity(enable);

        //开启搜索蓝牙

        listview = (ListView) findViewById(R.id.listview);
        mAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1,devices);
        listview.setAdapter(mAdaptor);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                new ConnectThread(deviceList.get(position)).start();
            }
        });


        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        blueToothReceiver = new BlueToothReceiver();
        registerReceiver(blueToothReceiver, filter);


        //search
        search_mutex = false;
        Button btn = (Button)findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SearchDevices();
            }
        });


        //Accept
        AcceptPushThread = new AcceptThread();

        Button btn2 = (Button)findViewById(R.id.btn2);
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IsOpen = true;
                AcceptPushThread.start();

            }
        });

        Button closebtn = (Button)findViewById(R.id.closebtn);
        closebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IsOpen = false;
            }
        });
        //宠物
        pet = new PetInfo("halo");

        //子线程接受handler msg
        final EditText edittext = (EditText) findViewById(R.id.send_msg);
        Button sendbtn = (Button)findViewById(R.id.send_btn);
        sendbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Message newmsg = Message.obtain();
                newmsg.obj = edittext.getText() + "$";
                newmsg.what = 0;
                try {
                    subhandler.sendMessage(newmsg);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(blueToothReceiver);
        super.onDestroy();
    }

    //开始查找，发送广播
    private void SearchDevices() {
        if (!search_mutex)
        {
            search_mutex = true;
            bluetoothAdapter.startDiscovery();
        }
    }


    public class PetInfo {
        public PetInfo(){
            this.PetName = "Pat";
        }
        public PetInfo(String name){
            this.PetName = name;
        }
        public void SetName(String name) {
            this.PetName = name;
        }
        public String getPetName() {
            return PetName;
        }
        private String PetName;
    }


    public class BlueToothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (devices.indexOf(device.getName()) == -1) {
                    devices.add(device.getName());
                    deviceList.add(device);
                }
            }
            mAdaptor.notifyDataSetChanged();
            Toast.makeText(context,"search finished", Toast.LENGTH_SHORT).show();
            search_mutex = false;
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private String sendMessage = "";


        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            socket = tmp;
        }


        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                Log.i("TAG","thread start");
                socket.connect();
                IsOpen = true;

                //接受线程的开启
                new ManageSocket().start();

                Looper.prepare();
                subhandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        try {
                            switch (msg.what) {
                                //msg send
                                case 0:
                                    sendMessage = msg.obj.toString();
                                    socket.getOutputStream().write(sendMessage.getBytes());
                                    break;
                            }
                        } catch (Exception e) {
                            getLooper().quit();
                        }
                    }
                };
                Looper.loop();




            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    socket.close();
                } catch (IOException closeException) { }
                //return;
            }

        }


        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            subhandler.removeCallbacksAndMessages(null);
        }

    }


    private class ManageSocket extends Thread {

        public ManageSocket() {
            super();
        }

        @Override
        public void run() {
            while (IsOpen) {
                try {
                        byte[] buffer = new byte[1024];
                        if (socket.getInputStream().read(buffer) != -1) {
                            //在这里处理byte转string类型
                            String conv = "";
                            char c;
                            for (int i = 0; i < 1024; i++) {
                                if (buffer[i] == 36) {
                                    break;
                                }
                                c = (char) buffer[i];
                                conv = conv + c;
                            }
                            Log.i("MSGVG", conv);
                        }

                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private String sendMessage = "";

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("TestServer", MY_UUID);
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            //BluetoothSocket socket;
            // Keep listening until exception occurs or a socket is returned

            try {
                socket = mmServerSocket.accept();
            }
            catch (IOException e) {

            }
            //接受线程的开启
            new ManageSocket().start();

            Looper.prepare();
            subhandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    try {
                        switch (msg.what) {
                            //msg send
                            case 0:
                                sendMessage = msg.obj.toString();
                                socket.getOutputStream().write(sendMessage.getBytes());
                                break;
                        }
                    } catch (Exception e) {
                        getLooper().quit();
                    }
                }
            };
            Looper.loop();
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            subhandler.removeCallbacksAndMessages(null);
        }
    }
}
