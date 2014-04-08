
package com.swiftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class CmdRETR extends FtpCmd implements Runnable {
    // public static final String message = "TEMPLATE!!";
    protected String input;

    public CmdRETR(FtpClientThread sessionThread, String input) {
        super(sessionThread, CmdRETR.class.toString());
        this.input = input;
    }

    public void run() {
        LogUtil.d("RETR executing");
        String param = getParameter(input);
        File fileToRetr;
        String errString = null;

        mainblock: {
            fileToRetr = inputPathToChrootedFile(sessionThread.getWorkingDir(), param);
            if (violatesChroot(fileToRetr)) {
                errString = "550 Invalid name or chroot violation\r\n";
                break mainblock;
            } else if (fileToRetr.isDirectory()) {
                LogUtil.d("Ignoring RETR for directory");
                errString = "550 Can't RETR a directory\r\n";
                break mainblock;
            } else if (!fileToRetr.exists()) {
                LogUtil.d("Can't RETR nonexistent file: " + fileToRetr.getAbsolutePath());
                errString = "550 File does not exist\r\n";
                break mainblock;
            } else if (!fileToRetr.canRead()) {
                LogUtil.d("Failed RETR permission (canRead() is false)");
                errString = "550 No read permissions\r\n";
                break mainblock;
            }

            try {
                FileInputStream in = new FileInputStream(fileToRetr);
                byte[] buffer = new byte[FtpClientThread.CHUNK_SIZE];
                int bytesRead;
                if (sessionThread.startUsingDataSocket()) {
                    LogUtil.d("RETR opened data socket");
                } else {
                    errString = "425 Error opening socket\r\n";
                    LogUtil.d("Error in initDataSocket()");
                    break mainblock;
                }
                sessionThread.writeString("150 Sending file\r\n");
                if (sessionThread.isBinaryMode()) {
                    LogUtil.d("Transferring in binary mode");
                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (sessionThread.sendViaDataSocket(buffer, bytesRead) == false) {
                            errString = "426 Data socket error\r\n";
                            LogUtil.d("Data socket error");
                            break mainblock;
                        }
                    }
                } else { // We're in ASCII mode
                    LogUtil.d("Transferring in ASCII mode");
                    // We have to convert all solitary \n to \r\n
                    boolean lastBufEndedWithCR = false;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        int startPos = 0, endPos = 0;
                        byte[] crnBuf = {
                                '\r', '\n'
                        };
                        for (endPos = 0; endPos < bytesRead; endPos++) {
                            if (buffer[endPos] == '\n') {
                                sessionThread
                                        .sendViaDataSocket(buffer, startPos, endPos - startPos);
                                if (endPos == 0) {
                                    if (!lastBufEndedWithCR) {
                                        sessionThread.sendViaDataSocket(crnBuf, 1);
                                    }
                                } else if (buffer[endPos - 1] != '\r') {
                                    sessionThread.sendViaDataSocket(crnBuf, 1);
                                } else {
                                }
                                startPos = endPos;
                            }
                        }

                        sessionThread.sendViaDataSocket(buffer, startPos, endPos - startPos);
                        if (buffer[bytesRead - 1] == '\r') {
                            lastBufEndedWithCR = true;
                        } else {
                            lastBufEndedWithCR = false;
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                errString = "550 File not found\r\n";
                break mainblock;
            } catch (IOException e) {
                errString = "425 Network error\r\n";
                break mainblock;
            }
        }
        sessionThread.closeDataSocket();
        if (errString != null) {
            sessionThread.writeString(errString);
        } else {
            sessionThread.writeString("226 Transmission finished\r\n");
        }
        LogUtil.d("RETR done");
    }
}
