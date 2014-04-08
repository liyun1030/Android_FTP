
package com.swiftp;

/**
 * Since STOR and APPE are essentially identical except for append vs truncate,
 * the common code is in this class, and inherited by CmdSTOR and CmdAPPE.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

abstract public class CmdAbstractStore extends FtpCmd {
    public static final String message = "TEMPLATE!!";

    public CmdAbstractStore(FtpClientThread sessionThread, String input) {
        super(sessionThread, CmdAbstractStore.class.toString());
    }

    public void doStorOrAppe(String param, boolean append) {
        LogUtil.d("STOR/APPE executing with append=" + append);
        File storeFile = inputPathToChrootedFile(sessionThread.getWorkingDir(), param);

        String errString = null;
        FileOutputStream out = null;
        // DedicatedWriter dedicatedWriter = null;
        // int origPriority = Thread.currentThread().getPriority();
        // myLog.l(Log.DEBUG, "STOR original priority: " + origPriority);
        storing: {
            // Get a normalized absolute path for the desired file
            if (violatesChroot(storeFile)) {
                errString = "550 Invalid name or chroot violation\r\n";
                break storing;
            }
            if (storeFile.isDirectory()) {
                errString = "451 Can't overwrite a directory\r\n";
                break storing;
            }

            try {
                if (storeFile.exists()) {
                    if (!append) {
                        if (!storeFile.delete()) {
                            errString = "451 Couldn't truncate file\r\n";
                            break storing;
                        }
                        // Notify other apps that we just deleted a file
                        FtpUtil.deletedFileNotify(storeFile.getPath());
                    }
                }
                out = new FileOutputStream(storeFile, append);
            } catch (FileNotFoundException e) {
                try {
                    errString = "451 Couldn't open file \"" + param + "\" aka \""
                            + storeFile.getCanonicalPath() + "\" for writing\r\n";
                } catch (IOException io_e) {
                    errString = "451 Couldn't open file, nested exception\r\n";
                }
                break storing;
            }
            if (!sessionThread.startUsingDataSocket()) {
                errString = "425 Couldn't open data socket\r\n";
                break storing;
            }
            LogUtil.d("Data socket ready");
            sessionThread.writeString("150 Data socket ready\r\n");
            byte[] buffer = new byte[FtpClientThread.CHUNK_SIZE];
            int numRead;
            if (sessionThread.isBinaryMode()) {
                LogUtil.d("Mode is binary");
            } else {
                LogUtil.d("Mode is ascii");
            }
            while (true) {
                switch (numRead = sessionThread.receiveFromDataSocket(buffer)) {
                    case -1:
                        LogUtil.d("Returned from final read");
                        // We're finished reading
                        break storing;
                    case 0:
                        errString = "426 Couldn't receive data\r\n";
                        break storing;
                    case -2:
                        errString = "425 Could not connect data socket\r\n";
                        break storing;
                    default:
                        try {
                            if (sessionThread.isBinaryMode()) {
                                out.write(buffer, 0, numRead);
                            } else {
                                int startPos = 0, endPos;
                                for (endPos = 0; endPos < numRead; endPos++) {
                                    if (buffer[endPos] == '\r') {
                                        out.write(buffer, startPos, endPos - startPos);
                                        startPos = endPos + 1;
                                    }
                                }
                                if (startPos < numRead) {
                                    out.write(buffer, startPos, endPos - startPos);
                                }
                            }

                            out.flush();

                        } catch (IOException e) {
                            errString = "451 File IO problem. Device might be full.\r\n";
                            e.printStackTrace();
                            break storing;
                        }
                        break;
                }
            }
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
        }

        if (errString != null) {
            LogUtil.d("STOR error: " + errString.trim());
            sessionThread.writeString(errString);
        } else {
            sessionThread.writeString("226 Transmission complete\r\n");
            // Notify the music player (and possibly others) that a few file has
            // been uploaded.
            FtpUtil.newFileNotify(storeFile.getPath());
        }
        sessionThread.closeDataSocket();
        LogUtil.d("STOR finished");
    }
}
