package com.myasterisk;

import org.asteriskjava.fastagi.*;

public class App {
    public static void main(String[] args) throws Exception {
        AgiServer server = new DefaultAgiServer();
        System.out.println("AGI Server starting on port 4573...");
        server.startup();
    }
}
