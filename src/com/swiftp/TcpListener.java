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

import java.net.ServerSocket;
import java.net.Socket;

public class TcpListener extends Thread {

    FtpServerThread mServer;

    public TcpListener(FtpServerThread server) {
        mServer = server;
    }

    public void quit() {
        try {
            mServer.getSocket().close();
        } catch (Exception e) {
            LogUtil.d("Exception closing TcpListener listenSocket");
        }
    }

    public void run() {
        ServerSocket socket = mServer.getSocket();
        try {
            while (true) {
                Socket clientSocket = socket.accept();
                LogUtil.d("New connection, spawned thread");
                FtpClientThread client = new FtpClientThread(clientSocket,
                        new NormalDataSocketFactory(), FtpClientThread.Source.LOCAL);
                client.start();
                mServer.registerSessionThread(client);
            }
        } catch (Exception e) {
            LogUtil.d("Exception in TcpListener");
        }
    }
}
