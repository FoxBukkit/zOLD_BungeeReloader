package com.foxelbox.bungeereloader;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Instance {
    static final Queue<Instance> free = new ConcurrentLinkedQueue<>();
    static Instance active = null;

    private final File instanceDir;
    Instance(File instanceDir) {
        instanceDir.mkdirs();
        this.instanceDir = instanceDir;
    }

    private Process process;
    private PrintWriter processStdin;

    private volatile ShouldRunThread shouldRunThread;
    private class ShouldRunThread extends Thread {
        boolean shouldRun = true;

        public void run() {
            try {
                while(shouldRun) {
                    try {
                        process.exitValue();
                        active = null;
                        Main.initRestart();
                        return;
                    } catch (IllegalThreadStateException e) {
                        //Process is still alive
                    }
                    Thread.sleep(2000);
                }
            }  catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void start() throws Exception {
        new ProcessBuilder(
                "rsync", "-v", "--delete", "-a",
                Main.sourceDir.getCanonicalPath() + "/",
                instanceDir.getCanonicalPath()
        )
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start().waitFor();

        process = new ProcessBuilder(
                Main.javaExec, "-Dbungee.epoll=true", "-Djava.awt.headless=true",
                "-Djline.terminal=jline.UnsupportedTerminal",
                "-Xmx256M", "-jar", "custom.jar"
        )
                .directory(instanceDir)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start();

        processStdin = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));

        shouldRunThread = new ShouldRunThread();
    }

    void stop() throws Exception {
        if(shouldRunThread != null) {
            shouldRunThread.shouldRun = false;
            shouldRunThread.join();
            shouldRunThread = null;
        }

        try {
            feedStdin("gqueuestop");
        } catch (Exception e) {
            e.printStackTrace();
        }

        process.waitFor();
        free.add(this);
    }

    void feedStdin(String str) throws Exception {
        processStdin.println(str);
        processStdin.flush();
    }
}
