package com.opersys.fileexplorer.node;

import android.net.LocalServerSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

/**
 * Date: 19/05/15
 * Time: 9:08 AM
 */
public class NodeKeepAliveSocketThread extends Thread {

    private String socketName;

    public NodeKeepAliveSocketThread() {
        socketName = UUID.randomUUID().toString();
        setDaemon(true);
    }

    public String getSocketName() {
        return this.socketName;
    }

    public void run() {
        String TAG = "NodeKeepAliveSocketThread";
        LocalServerSocket keepAliveSock = null;

        Log.d(TAG, "Keep alive socket thread starting");

        try {
            keepAliveSock = new LocalServerSocket(socketName);

            while (true) {
                try {
                    keepAliveSock.accept();
                    Log.d(TAG, "Accepted keepalive socket connection");

                } catch (IOException ex) {
                    Log.d(TAG, "Failed to accept socket connection");
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Error while creating the keepalive socket", ex);

        } finally {
            if (keepAliveSock != null) {
                try {
                    keepAliveSock.close();
                } catch (IOException ex) {
                    Log.w(TAG, "Error while trying to close the keepalive socket.");
                }
            }
        }
    }
}
