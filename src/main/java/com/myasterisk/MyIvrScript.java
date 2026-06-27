package com.myasterisk;

import org.asteriskjava.fastagi.*;

public class MyIvrScript extends BaseAgiScript {
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        answer();

        String callerPhone = request.getCallerIdNumber();
        if (callerPhone == null) callerPhone = "1001";

        streamFile("iti-welcome");
        streamFile("iti-press1");
        streamFile("iti-press2");
        streamFile("iti-press3");

        int digit = waitForDigit(7000);

        if (digit == '1') {
            double balance = DBHelper.getBalance(callerPhone);
            streamFile("iti-balance");
            if (balance >= 0) {
               
                String balanceStr = String.valueOf((int) balance);
                for (char c : balanceStr.toCharArray()) {
                    streamFile("digits/" + c);
                }
            } else {
                streamFile("demo-nogo");
            }

        } else if (digit == '2') {
            streamFile("iti-transfer");
            streamFile("one-moment-please");

        } else if (digit == '3') {
            streamFile("tt-monkeysintro");
            streamFile("tt-monkeys");

        } else {
            streamFile("demo-nogo");
        }

        streamFile("iti-goodbye");
        hangup();
    }
}
