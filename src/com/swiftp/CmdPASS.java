
package com.swiftp;

public class CmdPASS extends FtpCmd implements Runnable {
    String input;

    public CmdPASS(FtpClientThread sessionThread, String input) {
        super(sessionThread, CmdPASS.class.toString());
        this.input = input;
    }

    public void run() {
        LogUtil.d("Executing PASS");
        sessionThread.writeString("230 Access granted\r\n");
        sessionThread.authAttempt(true);
    }
}
