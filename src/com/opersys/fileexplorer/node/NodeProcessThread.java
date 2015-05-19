/*
* Copyright (C) 2014-2015, Opersys inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.opersys.fileexplorer.node;

import android.net.LocalServerSocket;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import com.opersys.fileexplorer.FileExplorerService;
import com.opersys.fileexplorer.misc.PasswordGenerator;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Author: François-Denis Gonthier (francois-denis.gonthier@opersys.com)
 *
 * Simple process listener thread.
 */
public class NodeProcessThread extends Thread {

    private static final String TAG = "NodeProcessThread";

    private NodeKeepAliveSocketThread nodeKeepAliveThread;

    private ProcessBuilder nodeProcessBuilder;

    private String dir;
    private String exec;
    private String js;
    private String suExec;
    private String args;
    private boolean asRoot;

    private Handler msgHandler;
    private FileExplorerService service;
    private Process nodeProcess;

    private Timer tm;

    /**
     * This is the password as it has been sent to the interface. Null if
     * it hasn't been sent successfully or if it has not been sent yet
     */
    private String password;

    private synchronized void writeCmd(String cmdName, String ... args) throws IOException {
        String argsStr;
        PrintWriter pw;

        pw = new PrintWriter(new OutputStreamWriter(nodeProcess.getOutputStream()));

        pw.write(cmdName);

        if (args != null) {
            pw.write(" ");
            argsStr = TextUtils.join(" ", args);
            pw.write(argsStr);
        }

        pw.write("\n");
        pw.flush();
    }

    public String getPassword() {
        return this.password;
    }

    public void stopProcess() {
        tm.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.w(TAG, "The node process didn't end in a timely manner, destroying it");
                if (nodeProcess != null)
                    nodeProcess.destroy();
            }
        }, 5000);

        try {
            writeCmd("quit");

            if (nodeProcess != null)
                nodeProcess.getOutputStream().close();

        } catch (IOException e) {
            // If we could not send the quit command to the process, forcefully
            // destroy it. This means we will not be able to read the streams but
            // that is preferrable to having the process stick around.
            Log.w(TAG, "Could not send quite command to process, destroying it.");

            if (nodeProcess != null)
                nodeProcess.destroy();
        }
    }

    public void startProcess() {
        start();
    }

    @Override
    public void run() {
        final StringBuffer sin, serr;
        final NodeThreadEventData emptyEventData;
        BufferedReader bin, berr;
        String s;

        emptyEventData = new NodeThreadEventData();

        if (!nodeKeepAliveThread.isAlive())
            nodeKeepAliveThread.start();

        try {
            LocalServerSocket stopSocket = new LocalServerSocket("file-explorer");

            msgHandler.post(new Runnable() {
                @Override
                public void run() {
                    service.fireNodeServiceEvent(NodeThreadEvent.NODE_STARTING, emptyEventData);
                }
            });

            if (asRoot && suExec != null)
                nodeProcessBuilder
                        .directory(new File(dir))
                        .command(suExec, "-c", "cd " + dir + " && " + exec + " " + js + " " + args);
            else
                nodeProcessBuilder
                        .directory(new File(dir))
                        .command(exec, args, js);

            Log.i(TAG, "Starting process: " + TextUtils.join(" ", nodeProcessBuilder.command()));

            nodeProcess = nodeProcessBuilder.start();

            msgHandler.post(new Runnable() {
                @Override
                public void run() {
                    service.fireNodeServiceEvent(NodeThreadEvent.NODE_STARTED, emptyEventData);
                }
            });

            try {
                String pwd;

                pwd = PasswordGenerator.NewPassword(5);
                writeCmd("pass", pwd);
                this.password = pwd;

                Log.d(TAG, "The password was sent to the interface");

            } catch (IOException ex) {
                Log.w(TAG, "Could not send 'pass' command. You won't be able to log into the interface.");
            }

            // Wait for the process to quit
            try {
                nodeProcess.waitFor();
            } catch (InterruptedException e) {
                Log.i(TAG, "Interrupting wait on Node process");
            }

            // Successful (nor not) quit, cancel all the things
            tm.cancel();

            sin = new StringBuffer();
            serr = new StringBuffer();

            try {
                Log.d(TAG, "Reading process stdout...");

                if (nodeProcess.getInputStream() != null) {
                    bin = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()));

                    while ((s = bin.readLine()) != null) {
                        sin.append(s);
                    }
                }

                Log.d(TAG, "Done reading process stdout");

                Log.d(TAG, "Reading error stream");

                if (nodeProcess.getErrorStream() != null) {
                    berr = new BufferedReader(new InputStreamReader(nodeProcess.getErrorStream()));
                    while ((s = berr.readLine()) != null)
                        serr.append(s);
                }

                Log.d(TAG, "Done reading error stream");

            } catch (IOException ex) {
                Log.e(TAG, "Exception reading error output", ex);
            }

            if (nodeProcess.exitValue() == 0) {
                msgHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        service.fireNodeServiceEvent(NodeThreadEvent.NODE_STOPPED,
                                new NodeThreadEventData(sin.toString(), serr.toString()));
                    }
                });
            } else {
                msgHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        service.fireNodeServiceEvent(NodeThreadEvent.NODE_ERROR,
                                new NodeThreadEventData(sin.toString(), serr.toString()));
                    }
                });
            }

        } catch (IOException e) {
            final NodeThreadEventData evData = new NodeThreadEventData(e);

            msgHandler.post(new Runnable() {
                @Override
                public void run() {
                    service.fireNodeServiceEvent(NodeThreadEvent.NODE_ERROR, evData);
                }
            });

        } finally {
            // Make sure everything about the process is destroyed.
            nodeProcess.destroy();
            nodeProcess = null;
        }
    }

    public NodeProcessThread(String dir,
                             String execfile,
                             String jsfile,
                             String[] args,
                             boolean asRoot,
                             Handler msgHandler,
                             FileExplorerService service) {
        String[] suFiles = { "/system/xbin/su", "/system/bin/su" };

        for (String sf : suFiles)
            if (new File(sf).exists())
                this.suExec = sf;

        this.nodeKeepAliveThread = new NodeKeepAliveSocketThread();

        this.dir = dir;
        this.msgHandler = msgHandler;
        this.service = service;
        this.js = dir + "/" + jsfile;
        this.exec = dir + "/"+ execfile;
        this.args = TextUtils.join(" ", args) + " -s " + this.nodeKeepAliveThread.getSocketName();
        this.asRoot = asRoot;

        this.nodeProcessBuilder = new ProcessBuilder();

        // A forcekill timer used in case nodeprocess becomes unresponsive
        this.tm = new Timer("nodeKill", true);
    }

}