package com.example.tftp.server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.io.File;

public class TftpLogger {

    private static String logFilePath = "tftp_log.txt";

    public static void init(String directory) {
        File logFile = new File(directory, "tftp_log.txt");

        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            logFilePath = logFile.getAbsolutePath();
            System.out.println("Logger initialized: " + logFilePath);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static synchronized void log(String msg) {
        try (PrintWriter out = new PrintWriter(new FileWriter(logFilePath, true))) {
            out.println("[" + LocalDateTime.now() + "] " + msg);
        } catch (IOException e) {
            System.err.println("Failed to write to log: " + e.getMessage());
        }
    }
}
