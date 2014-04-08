
package com.swiftp;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class FtpServerThread extends Thread {

    public static Context sContext = null;

    public static final String TAG = "LiquFtpServer";

    protected static final File DEFAULT_FILE = new File("/");

    protected static final String DIR = DEFAULT_FILE.getAbsolutePath();

    public static final int WAKE_INTERVAL_MS = 1000; // milliseconds

    public static final String WAKE_LOCK_TAG = "LiquFTP";

    private PowerManager.WakeLock mWakeLock = null;

    private WifiLock mWifiLock = null;

    private ServerSocket mServerSocket = null;

    private boolean mShutdown = false;

    private TcpListener mServerListener = null;

    private List<FtpClientThread> mClientThreads = new ArrayList<FtpClientThread>();

    private int mPort;

    public FtpServerThread(int port) {
        mPort = port;
    }

    private void initSocket() throws IOException {
        mServerSocket = new ServerSocket();
        mServerSocket.setReuseAddress(true);
        mServerSocket.bind(new InetSocketAddress(mPort));
    }

    private void stopAndRelease() {
        releaseWifiLock();
        releaseWakeLock();
    }

    protected ServerSocket getSocket() {
        return mServerSocket;
    }

    public void shutdown() {
        mShutdown = true;
    }

    public void run() {
        Log.d(TAG, "Server thread running");

        if (!FtpUtil.isWifiEnabled()) {
            Log.d(TAG, "Can not start ftp server, wifi disabled");
            return;
        }

        mShutdown = false;
        try {
            initSocket();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        takeLock();

        while (!mShutdown) {
            if (mServerListener != null) {
                if (!mServerListener.isAlive()) {
                    Log.d(TAG, "Joining crashed wifiListener thread");
                    try {
                        mServerListener.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mServerListener = null;
                }
            }
            if (mServerListener == null) {
                mServerListener = new TcpListener(this);
                mServerListener.start();
            }
            try {
                Thread.sleep(WAKE_INTERVAL_MS);
            } catch (InterruptedException e) {
                LogUtil.d("Thread interrupted");
            }
        }

        terminateAllSessions();

        if (mServerListener != null) {
            mServerListener.quit();
            mServerListener = null;
        }
        stopAndRelease();
    }

    /**
     * The FTPServerService must know about all running session threads so they
     * can be terminated on exit. Called when a new session is created.
     */
    protected void registerSessionThread(FtpClientThread newSession) {
        synchronized (this) {
            List<FtpClientThread> toBeRemoved = new ArrayList<FtpClientThread>();
            for (FtpClientThread client : mClientThreads) {
                if (!client.isAlive()) {
                    LogUtil.d("Cleaning up finished session...");
                    try {
                        client.join();
                        LogUtil.d("Thread joined");
                        toBeRemoved.add(client);
                        client.closeSocket();
                    } catch (InterruptedException e) {
                        LogUtil.d("Interrupted while joining");
                    }
                }
            }
            for (FtpClientThread removeThread : toBeRemoved) {
                mClientThreads.remove(removeThread);
            }

            // Cleanup is complete. Now actually add the new thread to the list.
            mClientThreads.add(newSession);
        }
        LogUtil.d("Registered session thread");
    }

    private void terminateAllSessions() {
        LogUtil.d("Terminating " + mClientThreads.size() + " session thread(s)");
        synchronized (this) {
            for (FtpClientThread sessionThread : mClientThreads) {
                if (sessionThread != null) {
                    sessionThread.closeDataSocket();
                    sessionThread.closeSocket();
                }
            }
        }
    }

    private void takeLock() {
        takeWifiLock();
        takeWakeLock();
    }

    private void takeWifiLock() {
        if (mWifiLock == null) {
            WifiManager manager = (WifiManager)sContext.getSystemService(Context.WIFI_SERVICE);
            mWifiLock = manager.createWifiLock(WAKE_LOCK_TAG);
            mWifiLock.setReferenceCounted(false);
        }
        mWifiLock.acquire();
    }

    private void releaseWifiLock() {
        if (mWifiLock != null) {
            mWifiLock.release();
            mWifiLock = null;
        }
    }

    private void takeWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)sContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, WAKE_LOCK_TAG);
            mWakeLock.setReferenceCounted(false);
        }
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }
}
