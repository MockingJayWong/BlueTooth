package com.example.administrator.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BlueToothActivity extends AppCompatActivity {

    private List<String> devices;
    private List<BluetoothDevice> deviceList;
    private ArrayAdapter<String> mAdaptor;
    private ListView listview;
    //想调用蓝牙模块，就必须获得下面的adapter实例。
    private BluetoothAdapter bluetoothAdapter;
    private BlueToothReceiver blueToothReceiver;
    private boolean search_mutex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blue_tooth);

        devices = new ArrayList<String>();
        deviceList = new ArrayList<BluetoothDevice>();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
        Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        enable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600); //3600为蓝牙设备可见时间
        startActivity(enable);

        //开启搜索蓝牙

        listview = (ListView) findViewById(R.id.listview);
        mAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1,devices);
        listview.setAdapter(mAdaptor);

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
            Toast.makeText(context,"search finished", Toast.LENGTH_SHORT);
            search_mutex = false;
        }
    }
}
