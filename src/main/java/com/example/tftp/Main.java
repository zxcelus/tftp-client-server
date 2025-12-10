package com.example.tftp;

import com.example.tftp.client.view.ClientGUI;
import com.example.tftp.server.TftpServer;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0) {
            if (args[0].equals("server")) {
                String[] serverArgs = new String[args.length - 1];
                System.arraycopy(args, 1, serverArgs, 0, serverArgs.length);
                TftpServer.main(serverArgs);
            }
            else if (args[0].equals("client")) {
                SwingUtilities.invokeLater(() -> {
                    ClientGUI frame = new ClientGUI();
                    frame.setVisible(true);
                });
            } else {
                printUsage();
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                ClientGUI frame = new ClientGUI();
                frame.setVisible(true);
            });
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar tftp-client-server.jar [mode] [options]");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  client     - Start TFTP client (default)");
        System.out.println("  server     - Start TFTP server");
        System.out.println();
        System.out.println("Server options:");
        System.out.println("  -p PORT    - Port number (default: 69)");
        System.out.println("  -d DIR     - Base directory (default: ./tftp-server-files)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar tftp-client-server.jar");
        System.out.println("  java -jar tftp-client-server.jar client");
        System.out.println("  java -jar tftp-client-server.jar server -p 6969 -d /var/tftp");
    }
}