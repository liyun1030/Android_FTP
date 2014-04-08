/*
Copyright 2009 David Revell

This file is part of SwiFTP.

SwiFTP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SwiFTP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SwiFTP.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swiftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class FtpClientThread extends Thread {

    public static final String STRING_ENCODING = "UTF-8";

    protected static final int CHUNK_SIZE = 65536;

    protected Socket mSocket;

    protected ByteBuffer mReadBuffer = ByteBuffer.allocate(256);

    protected boolean mPasvMode = false;

    protected boolean mBinaryMode = false;

    protected boolean mAuthenticated = false;

    protected Socket mDataSocket = null;

    protected File mRenameFrom = null;

    protected DataSocketFactory mDataSocketFactory;

    OutputStream mDataOutputStream = null;

    protected Source mSource;

    private File mWorkingFile = FtpServerThread.DEFAULT_FILE;

    int authFails = 0;

    public enum Source {
        LOCAL, PROXY
    };

    public static int MAX_AUTH_FAILS = 3;

    /**
     * Sends a string over the already-established data socket
     * 
     * @param string
     * @return Whether the send completed successfully
     */
    public boolean sendViaDataSocket(String string) {
        try {
            byte[] bytes = string.getBytes(STRING_ENCODING);
            LogUtil.d("Using data connection STRING_ENCODING: " + STRING_ENCODING);
            return sendViaDataSocket(bytes, bytes.length);
        } catch (UnsupportedEncodingException e) {
            LogUtil.d("Unsupported STRING_ENCODING for data socket send");
            return false;
        }
    }

    public boolean sendViaDataSocket(byte[] bytes, int len) {
        return sendViaDataSocket(bytes, 0, len);
    }

    /**
     * Sends a byte array over the already-established data socket
     * 
     * @param bytes
     * @param len
     * @return
     */
    public boolean sendViaDataSocket(byte[] bytes, int start, int len) {

        if (mDataOutputStream == null) {
            LogUtil.d("Can't send via null dataOutputStream");
            return false;
        }
        if (len == 0) {
            return true; // this isn't an "error"
        }
        try {
            mDataOutputStream.write(bytes, start, len);
        } catch (IOException e) {
            LogUtil.d("Couldn't write output stream for data socket");
            LogUtil.d(e.toString());
            return false;
        }
        mDataSocketFactory.reportTraffic(len);
        return true;
    }

    /**
     * Received some bytes from the data socket, which is assumed to already be
     * connected. The bytes are placed in the given array, and the number of
     * bytes successfully read is returned.
     * 
     * @param bytes Where to place the input bytes
     * @return >0 if successful which is the number of bytes read, -1 if no
     *         bytes remain to be read, -2 if the data socket was not connected,
     *         0 if there was a read error
     */
    public int receiveFromDataSocket(byte[] buf) {
        int bytesRead;

        if (mDataSocket == null) {
            LogUtil.d("Can't receive from null dataSocket");
            return -2;
        }
        if (!mDataSocket.isConnected()) {
            LogUtil.d("Can't receive from unconnected socket");
            return -2;
        }
        InputStream in;
        try {
            in = mDataSocket.getInputStream();
            // If the read returns 0 bytes, the stream is not yet
            // closed, but we just want to read again.
            while ((bytesRead = in.read(buf, 0, buf.length)) == 0) {
            }
            if (bytesRead == -1) {
                // If InputStream.read returns -1, there are no bytes
                // remaining, so we return 0.
                return -1;
            }
        } catch (IOException e) {
            LogUtil.d("Error reading data socket");
            return 0;
        }
        mDataSocketFactory.reportTraffic(bytesRead);
        return bytesRead;
    }

    /**
     * Called when we receive a PASV command.
     * 
     * @return Whether the necessary initialization was successful.
     */
    public int onPasv() {
        return mDataSocketFactory.onPasv();
    }

    /**
     * Called when we receive a PORT command.
     * 
     * @return Whether the necessary initialization was successful.
     */
    public boolean onPort(InetAddress dest, int port) {
        return mDataSocketFactory.onPort(dest, port);
    }

    /**
     * Will be called by (e.g.) CmdSTOR, CmdRETR, CmdLIST, etc. when they are
     * about to start actually doing IO over the data socket.
     * 
     * @return
     */
    public boolean startUsingDataSocket() {
        try {
            mDataSocket = mDataSocketFactory.onTransfer();
            if (mDataSocket == null) {
                LogUtil.d("dataSocketFactory.onTransfer() returned null");
                return false;
            }
            mDataOutputStream = mDataSocket.getOutputStream();
            return true;
        } catch (IOException e) {
            LogUtil.d("IOException getting OutputStream for data socket");
            mDataSocket = null;
            return false;
        }
    }

    public void quit() {
        closeSocket();
    }

    public void closeDataSocket() {
        if (mDataOutputStream != null) {
            try {
                mDataOutputStream.close();
            } catch (IOException e) {
            }
            mDataOutputStream = null;
        }
        if (mDataSocket != null) {
            try {
                mDataSocket.close();
            } catch (IOException e) {
            }
        }
        mDataSocket = null;
    }

    protected InetAddress getLocalAddress() {
        return mSocket.getLocalAddress();
    }

    public void run() {
        LogUtil.d("SessionThread started");
        writeString("220 LiquFTP ready\r\n");
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(mSocket.getInputStream()),
                    8192);
            while (true) {
                String line;
                line = in.readLine(); // will accept \r\n or \n for terminator
                if (line != null) {
                    LogUtil.d("Received line from client: " + line);
                    FtpCmd.dispatchCommand(this, line);
                } else {
                    LogUtil.d("readLine gave null, quitting");
                    break;
                }
            }
        } catch (IOException e) {
            LogUtil.d("Connection was dropped");
        }
        closeSocket();
    }

    /**
     * A static method to check the equality of two byte arrays, but only up to
     * a given length.
     */
    public static boolean compareLen(byte[] array1, byte[] array2, int len) {
        for (int i = 0; i < len; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    public void closeSocket() {
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public void writeBytes(byte[] bytes) {
        try {
            BufferedOutputStream out = new BufferedOutputStream(mSocket.getOutputStream(),
                    CHUNK_SIZE);
            out.write(bytes);
            out.flush();
            mDataSocketFactory.reportTraffic(bytes.length);
        } catch (IOException e) {
            LogUtil.d("writeBytes() Exception writing socket");
            closeSocket();
            return;
        }
    }

    public void writeString(String str) {
        byte[] strBytes = null;
        try {
            strBytes = str.getBytes(STRING_ENCODING);
        } catch (UnsupportedEncodingException e) {
            strBytes = str.getBytes();
        }
        writeBytes(strBytes);
    }

    protected Socket getSocket() {
        return mSocket;
    }

    public boolean isPasvMode() {
        return mPasvMode;
    }

    public FtpClientThread(Socket socket, DataSocketFactory dataSocketFactory, Source source) {
        mSocket = socket;
        mSource = source;
        mDataSocketFactory = dataSocketFactory;
    }

    static public ByteBuffer stringToBB(String s) {
        return ByteBuffer.wrap(s.getBytes());
    }

    public boolean isBinaryMode() {
        return mBinaryMode;
    }

    public void setBinaryMode(boolean binaryMode) {
        this.mBinaryMode = binaryMode;
    }

    public boolean isAuthenticated() {
        return mAuthenticated;
    }

    public void authAttempt(boolean authenticated) {
        if (authenticated) {
            LogUtil.d("Authentication complete");
            mAuthenticated = true;
        } else {
            if (mSource == Source.PROXY) {
                quit();
            } else {
                authFails++;
            }
            if (authFails > MAX_AUTH_FAILS) {
                quit();
            }
        }

    }

    public File getWorkingDir() {
        return mWorkingFile;
    }

    public void setWorkingDir(File workingDir) {
        try {
            mWorkingFile = workingDir.getCanonicalFile().getAbsoluteFile();
        } catch (IOException e) {
            LogUtil.d("setWorkingDir() SessionThread canonical error");
        }
    }

    public Socket getDataSocket() {
        return mDataSocket;
    }

    public void setDataSocket(Socket dataSocket) {
        this.mDataSocket = dataSocket;
    }

    public File getRenameFrom() {
        return mRenameFrom;
    }

    public void setRenameFrom(File renameFrom) {
        this.mRenameFrom = renameFrom;
    }

    public String getEncoding() {
        return STRING_ENCODING;
    }

}
