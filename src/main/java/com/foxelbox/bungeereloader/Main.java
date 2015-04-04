package com.foxelbox.bungeereloader;

import java.io.*;
import java.util.Scanner;

public class Main {
    static File sourceDir;
    static String javaExec;

    private static volatile int highestNumber = 0;
    private static volatile boolean isRestarting = false;
    static synchronized void initRestart() throws Exception {
        if(isRestarting) {
            return;
        }
        isRestarting = true;

        final Instance currentInstance = Instance.active;
        Instance.active = Instance.free.poll();
        if(Instance.active == null) {
            Instance.active = new Instance(new File("inst" + highestNumber++));
        }
        Instance.active.start();

        new Thread() {
            public void run() {
                try {
                    if (currentInstance != null) {
                        currentInstance.stop();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isRestarting = false;
            }
        }.start();
    }

    public static void main(String[] args) throws Exception {
        javaExec = new File(new File(System.getProperty("java.home")), "bin/java").getCanonicalPath();

        sourceDir = new File("deploy");
        final File stopFile = new File(sourceDir, "restart_if_empty");

        initRestart();

        new Thread() {
            public void run() {
                try {
                    while (true) {
                        if(stopFile.exists() && stopFile.delete()) {
                            initRestart();
                        }
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }
        }.start();

        try {
            Scanner stdinReader = new Scanner(System.in);
            String line;
            while ((line = stdinReader.nextLine()) != null) {
                line = line.trim();
                if (line.equalsIgnoreCase("zeroreload")) {
                    initRestart();
                } else if(!line.isEmpty()) {
                    try {
                        Instance.active.feedStdin(line);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
