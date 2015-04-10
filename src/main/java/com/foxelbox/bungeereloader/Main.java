/**
 * This file is part of BungeeReloader.
 *
 * BungeeReloader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BungeeReloader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with BungeeReloader.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.foxelbox.bungeereloader;

import java.io.*;
import java.util.Scanner;

public class Main {
    public static final boolean epoll = System.getProperty("bungee.epoll", "false").equalsIgnoreCase("true");

    static File sourceDir;
    static String javaExec;

    private static volatile int highestNumber = 0;
    private static volatile boolean isRestarting = false;
    static synchronized void initRestart() throws Exception {
        if(isRestarting) {
            return;
        }
        isRestarting = true;

        new Thread() {
            public void run() {
                try {
                    final Instance currentInstance = Instance.active;
                    Instance.active = Instance.free.poll();
                    if(Instance.active == null) {
                        Instance.active = new Instance(new File("inst" + highestNumber++));
                    }

                    if(epoll) {
                        //First start new bungee, then stop listeners (epoll = true)
                        Instance.active.start();
                        if (currentInstance != null) {
                            currentInstance.stop(true);
                        }
                    } else {
                        //First stop listeners, then start new bungee (epoll = false)
                        if (currentInstance != null) {
                            currentInstance.stop(false);
                            Thread.sleep(3000);
                        }
                        Instance.active.start();
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
        if(stopFile.exists()) {
            stopFile.delete();
        }

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
                } else if(line.equalsIgnoreCase("end")) {
                    System.exit(0);
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
