package ca.toronto.csc301.chat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import com.example.siddharthgautam.csc301.MainActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by akshay on 31/10/15.
 */
public class ConnectionsList {
    HashMap<BluetoothDevice,ConnectedThread> map = new HashMap<BluetoothDevice,ConnectedThread>();
    HashMap<String, BluetoothDevice> macToDevice = new HashMap<String, BluetoothDevice>();
    HashMap<BluetoothDevice, ConnectThread> connectThreads = new HashMap<BluetoothDevice, ConnectThread>();
    private boolean broadcasted = false;//when you connect to the network, broadcast that youre in it.
    //mac -> name
    HashMap<String, String> networkDevices = new HashMap<String, String>();
    private Handler mHandler;
    static ConnectionsList instance;
    static private AcceptThread acceptThread;

    public void setHandler(Handler h){
        mHandler = h;
    }

    private ConnectionsList(){
        instance = this;;
    }

    //when another devices sends an update, we don't want ourself to be in our own copy
    private void removeLocal(){
        networkDevices.put(BluetoothAdapter.getDefaultAdapter().getAddress(), null);
    }

    public void newDeviceInNetwork(String mac, String name){
        networkDevices.put(mac, name);
        removeLocal();
    }

    private void forward(Event e){
        Set<String> excludedMacs = e.getExcludedTargets();
        Set<BluetoothDevice> keys = map.keySet();
        Iterator<BluetoothDevice> i = keys.iterator();
        //exclude the ones I can send to
        while(i.hasNext()){
            BluetoothDevice d = i.next();
            ConnectedThread t = map.get(d);
            if(t == null){
                continue;
            }
            if(t.getSocket().isConnected() == false){
                closeConnection(d);
                //makeConnectionTo(d);
                continue;
            }
            e.addExcludedTarget(d.getAddress());
        }
        //exclude myself
        e.addExcludedTarget(BluetoothAdapter.getDefaultAdapter().getAddress());
        //send to the ones i can send to, then everyone else will do the same..
        Iterator<BluetoothDevice> it = keys.iterator();
        while(it.hasNext()){
            BluetoothDevice d = it.next();
            if(excludedMacs.contains(d.getAddress()) == false) {
                ConnectedThread t = getConnectedThread(d);
                if(t!=null){
                    t.sendEvent(e);
                }
            }
        }
    }

    public void sendEvent(Event e){
        //check if sender is in my network list, if not add them just in case
        if(networkDevices.containsKey(e.getSender()) == false){
            newDeviceInNetwork(e.getSender(), e.getSenderName());
        }
        int type = e.getType();
        //this is meant to be a "process event" function - need to fix wording
        switch(type){
            case 1:
                forward(e);
                break;
            case 2://asking for a network devices update
                BluetoothDevice remote = getDeviceFromMac(e.getSender());
                ConnectedThread t = getConnectedThread(remote);
                Event response_e = new Event();
                response_e.setType(3);
                response_e.setData(networkDevices);
                t.sendEvent(response_e);
                break;
            case 3: //recieving a network devices update
                HashMap<String, String> devices = e.getData();
                Set<String> macs = devices.keySet();
                Iterator<String> iter = macs.iterator();
                while(iter.hasNext()) {
                    String mac = iter.next();
                    newDeviceInNetwork(mac, devices.get(mac));
                }
                break;
            case 4:
                //new device in the network, update my own local copy
                newDeviceInNetwork(e.getSender(), e.getSenderName());
                forward(e);
                break;
        }
    }

    public BluetoothDevice getDeviceFromMac(String mac){
        return macToDevice.get(mac);
    }

    public boolean isDeviceInNetwork(String mac){
        return networkDevices.containsKey(mac);
    }

    public String getNameFromMac(String mac){
        if(networkDevices.get(mac) == null){
            return "";
        }
        return networkDevices.get(mac);
    }

    public String getMacFromName(String name){
        if(name == null){
            return "";
        }
        Set<String> keys = networkDevices.keySet();
        Iterator<String> key_it = keys.iterator();
        while(key_it.hasNext()){
            String mac = key_it.next();
            String n = networkDevices.get(mac);
            if(n == null){
                closeConnection(getDeviceFromMac(mac));
                continue;
            }
            if(n.equals(name)){
                return mac;
            }
        }
        return "Unknown";
    }

    public boolean onConnected(BluetoothDevice d){
        ConnectedThread t = getConnectedThread(d);
        if(t == null){
            return false;
        }
        this.networkDevices.put(t.getSocket().getRemoteDevice().getAddress(),
                t.getSocket().getRemoteDevice().getName());

        //send an event asking for their devices.
        Event e = new Event();
        e.setType(2);
        e.setData(networkDevices);
        e.setSender(BluetoothAdapter.getDefaultAdapter().getAddress());
        e.setSenderName(BluetoothAdapter.getDefaultAdapter().getName());
        t.sendEvent(e);
        //broadcast that im in the network
        //if(broadcasted == false){//temp
            broadcasted = true;
            Event ex = new Event();
            ex.setType(4);
            ex.setSender(BluetoothAdapter.getDefaultAdapter().getAddress());
            ex.setSenderName(BluetoothAdapter.getDefaultAdapter().getName());
            t.sendEvent(ex);
        //}
        return true;
    }

    public void newConnection(BluetoothSocket s, BluetoothDevice d){

        if(map.get(d) == null){
            final ConnectedThread t = new ConnectedThread(s, mHandler);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    t.start();
                }
            }).start();
            map.put(d, t);
            macToDevice.put(d.getAddress(), d);
            //if(t.getSocket().isConnected()){
            //    if(t.getSocket().getRemoteDevice() != null){
                    onConnected(d);
            //    }
            //}
        }
        else{ //In case connection fails or is bad remake it
            if(map.get(d).getSocket().isConnected() == false){
                closeConnection(d);
                makeConnectionTo(d);
            }
        }
    }

    public Set<String> getNamesOfConnectedDevices(){
        Set<String> names = new HashSet<String>();
        Set<String> macs = networkDevices.keySet();
        Iterator<String> maci = macs.iterator();
        while(maci.hasNext()){
            String n = networkDevices.get(maci.next());
            if(n == null){
                continue;
            }
            names.add(n);
        }
        return names;
    }

    public ConnectedThread getConnectedThread(BluetoothDevice d){
        return this.map.get(d);
    }

    public void closeConnection(BluetoothDevice device){
        if(device == null){
            return;
        }
        ConnectedThread s = this.map.get(device);
        if(s != null){
            s.cancel();
        }
        ConnectThread t = this.connectThreads.get(device);
        if(t != null){
            t.cancel();
        }
        this.connectThreads.put(device, null);
        this.map.put(device, null);
        this.macToDevice.put(device.getAddress(), null);
        this.networkDevices.put(device.getAddress(), null);
    }

    public static ConnectionsList getInstance() {
        if(instance != null) {
            return instance;
        } else {
            ConnectionsList connectionsList = new ConnectionsList();
            instance = connectionsList;
            return  instance;
        }
    }

    public static void accept(){
        new Thread(new Runnable() {

            @Override
            public void run() {
                AcceptThread t = new AcceptThread();
                t.start();
            }
        }).start();
    }

    public static void makeConnectionTo(final BluetoothDevice device){
        ConnectThread t = ConnectionsList.getInstance().connectThreads.get(device);
        if(t != null){
            t.cancel();
            ConnectionsList.getInstance().connectThreads.put(device, null);
        }
        final ConnectThread tx = new ConnectThread(device);
        ConnectionsList.getInstance().connectThreads.put(device, tx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                tx.start();
            }
        }).start();
    }

}
