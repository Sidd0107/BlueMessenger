package ca.toronto.csc301.chat;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.widget.Toast;

import com.example.siddharthgautam.csc301.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by akshay on 31/10/15.
 */
public class BluetoothController {

    ConnectionsList connectionsList;
    static BluetoothController instance;
    Context context = null;

    private BluetoothController(){
        connectionsList = ConnectionsList.getInstance();

    }

    public void setContext(Context context){
        this.context = context;
    }


    public static BluetoothController getInstance() {
        if(instance != null) {
            return instance;
        } else {
            BluetoothController bluetoothController = new BluetoothController();
            instance = bluetoothController;
            return  instance;
        }
    }

    public void establishConnection(BluetoothDevice device){
        if(connectionsList.findConnectedDevide(device) == null) {
            RequestConnectionThread connectionThread = new RequestConnectionThread(device);
            connectionThread.start();
        }
    }

    public void sendMessage(Message message){
        ConnectedDevice connectedDevice = connectionsList.findConnectedDevide(message.getDevice());
        connectedDevice.getHandelConnectedThread().write(message);
//        BluetoothSocket socket = connectedDevice.getSocket();
//        OutputStream outputStream = null;
//        OutputStream tmpOut = null;
//
//        try {
//            tmpOut = socket.getOutputStream();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        outputStream = tmpOut;
//
//        byte[] toSend = message.getMessage().getBytes();
//        try {
//            outputStream.write(toSend);
//            outputStream.flush();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    public void handelRecievedMessage(Message message){
        if(context != null){
            Toast.makeText(context, "New message Recieved", Toast.LENGTH_SHORT)
                    .show();
        }
    }

}
