
package com.swiftp;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.net.wifi.WifiManager;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;

abstract public class FtpUtil {

    public static byte byteOfInt(int value, int which) {
        int shift = which * 8;
        return (byte)(value >> shift);
    }

    public static String ipToString(int addr, String sep) {
        if (addr > 0) {
            StringBuffer buf = new StringBuffer();
            buf.append(byteOfInt(addr, 0)).append(sep).append(byteOfInt(addr, 1)).append(sep)
                    .append(byteOfInt(addr, 2)).append(sep).append(byteOfInt(addr, 3));
            LogUtil.d("ipToString returning: " + buf.toString());
            return buf.toString();
        } else {
            return null;
        }
    }

    public static InetAddress intToInet(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = byteOfInt(value, i);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static String ipToString(int addr) {
        if (addr == 0) {
            LogUtil.d("ipToString won't convert value 0");
            return null;
        }
        return ipToString(addr, ".");
    }

    static byte[] jsonToByteArray(JSONObject json) throws JSONException {
        try {
            return json.toString().getBytes(FtpClientThread.STRING_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    static JSONObject byteArrayToJson(byte[] bytes) throws JSONException {
        try {
            return new JSONObject(new String(bytes, FtpClientThread.STRING_ENCODING));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static void newFileNotify(String path) {
        if (FtpServerThread.sContext != null) {
            LogUtil.d("Notifying others about new file: " + path);
            new MediaScannerNotifier(FtpServerThread.sContext, path);
        }
    }

    public static void deletedFileNotify(String path) {
        if (FtpServerThread.sContext != null) {
            LogUtil.d("Notifying others about deleted file: " + path);
            new MediaScannerNotifier(FtpServerThread.sContext, path);
        }
    }

    private static class MediaScannerNotifier implements MediaScannerConnectionClient {
        private MediaScannerConnection connection;

        private String path;

        public MediaScannerNotifier(Context context, String path) {
            this.path = path;
            connection = new MediaScannerConnection(context, this);
            connection.connect();
        }

        public void onMediaScannerConnected() {
            connection.scanFile(path, null); // null: we don't know MIME type
        }

        public void onScanCompleted(String path, Uri uri) {
            connection.disconnect();
        }
    }

    public static String[] concatStrArrays(String[] a1, String[] a2) {
        String[] retArr = new String[a1.length + a2.length];
        System.arraycopy(a1, 0, retArr, 0, a1.length);
        System.arraycopy(a2, 0, retArr, a1.length, a2.length);
        return retArr;
    }

    public static void sleepIgnoreInterupt(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    public static InetAddress getWifiIp() {
        if (FtpServerThread.sContext == null) {
            return null;
        }
        WifiManager wifiMgr = (WifiManager)FtpServerThread.sContext
                .getSystemService(Context.WIFI_SERVICE);
        if (isWifiEnabled()) {
            int ipAsInt = wifiMgr.getConnectionInfo().getIpAddress();
            if (ipAsInt == 0) {
                return null;
            } else {
                return intToInet(ipAsInt);
            }
        } else {
            return null;
        }
    }

    public static boolean isWifiEnabled() {
        if (FtpServerThread.sContext == null) {
            return false;
        }
        WifiManager wifiMgr = (WifiManager)FtpServerThread.sContext
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            return true;
        } else {
            return false;
        }
    }
}
