package com.example.tftp.server;

import com.example.tftp.model.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TftpServer {
    private static final int DEFAULT_PORT = 69;
    private static final String DEFAULT_DIR = "./tftp-server-files";
    private static final int THREAD_POOL_SIZE = 10;

    private int port;
    private String baseDir;
    private volatile boolean running;
    private ExecutorService threadPool;

    public TftpServer(int port, String baseDir) {
        this.port = port;
        this.baseDir = baseDir;
        this.running = true;
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void start() {
        File dir = new File(baseDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                System.err.println("Failed to create directory: " + dir.getAbsolutePath());
                return;
            }
        }

        System.out.println("TFTP Server starting on port " + port);
        System.out.println("Base directory: " + dir.getAbsolutePath());
        System.out.println("Press Ctrl+C to stop the server");

        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            serverSocket.setSoTimeout(1000);

            while (running) {
                try {
                    byte[] buffer = new byte[516];
                    DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);

                    serverSocket.receive(requestPacket);

                    threadPool.execute(new ClientHandler(serverSocket, requestPacket, baseDir));

                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error receiving packet: " + e.getMessage());
                    }
                }
            }

        } catch (SocketException e) {
            System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
        } finally {
            threadPool.shutdown();
            System.out.println("TFTP Server stopped");
        }
    }

    public void stop() {
        running = false;
    }

    static class ClientHandler implements Runnable {
        private DatagramSocket serverSocket;
        private DatagramPacket requestPacket;
        private String baseDir;

        public ClientHandler(DatagramSocket socket, DatagramPacket packet, String baseDir) {
            this.serverSocket = socket;
            this.requestPacket = packet;
            this.baseDir = baseDir;
        }

        @Override
        public void run() {
            InetAddress clientAddress = requestPacket.getAddress();
            int clientPort = requestPacket.getPort();
            TftpPacket tftpPacket = null;

            try (DatagramSocket clientSocket = new DatagramSocket()) {
                clientSocket.setSoTimeout(5000);

                byte[] requestData = new byte[requestPacket.getLength()];
                System.arraycopy(requestPacket.getData(), 0, requestData, 0, requestPacket.getLength());

                tftpPacket = TftpPacket.fromBytes(requestData);
                String filename = tftpPacket.getFilename();

                if (tftpPacket.getOpCode() == TftpOpCode.WRQ) {
                    File file = new File(baseDir, filename);

                    if (file.exists()) {
                        sendError(clientSocket, clientAddress, clientPort, TftpException.FILE_EXISTS,
                                "File already exists");
                        return;
                    }

                    handleWriteRequest(clientSocket, clientAddress, clientPort, tftpPacket);
                } else if (tftpPacket.getOpCode() == TftpOpCode.RRQ) {
                    handleReadRequest(clientSocket, clientAddress, clientPort, tftpPacket);
                }

            } catch (TftpException te) {
                try {
                    sendError(new DatagramSocket(), clientAddress, clientPort, te.getErrorCode(), te.getMessage());
                } catch (IOException ignored) {}
            } catch (Exception e) {
                try {
                    sendError(new DatagramSocket(), clientAddress, clientPort, TftpException.UNDEFINED,
                            "Unexpected server error");
                } catch (IOException ignored) {}
            }
        }

        private void handleWriteRequest(DatagramSocket socket, InetAddress clientAddress,
                                        int clientPort, TftpPacket request) throws TftpException {

            String filename = request.getFilename();
            File file = new File(baseDir, filename);

            try {
                if (!file.getCanonicalPath().startsWith(new File(baseDir).getCanonicalPath())) {
                    throw new TftpException("Access violation", TftpException.ACCESS_VIOLATION);
                }
            } catch (IOException ioe) {
                throw new TftpException("Access violation", TftpException.ACCESS_VIOLATION, ioe);
            }

            if (file.exists()) {
                throw new TftpException("File already exists", TftpException.FILE_EXISTS);
            }

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            System.out.println("[" + clientAddress + ":" + clientPort + "] Receiving file: " + filename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                int expectedBlock = 1;
                boolean lastPacket = false;

                // Initial ACK(0)
                sendPacket(socket, clientAddress, clientPort, TftpPacket.createACK(0));

                while (!lastPacket) {
                    TftpPacket dataPacket = receiveDataPacket(socket, clientAddress, clientPort, expectedBlock);
                    if (dataPacket == null) break;

                    byte[] data = dataPacket.getData();
                    fos.write(data);

                    sendPacket(socket, clientAddress, clientPort, TftpPacket.createACK(expectedBlock));

                    lastPacket = data.length < 512;
                    expectedBlock++;
                }

                System.out.println("[" + clientAddress + ":" + clientPort + "] File received: " + filename);

            } catch (IOException ioe) {
                if (file.exists()) file.delete();
                throw new TftpException("IO error during upload", TftpException.UNDEFINED, ioe);
            }
        }

        private void handleReadRequest(DatagramSocket socket, InetAddress clientAddress,
                                       int clientPort, TftpPacket request) throws TftpException {

            String filename = request.getFilename();
            File file = new File(baseDir, filename);

            try {
                if (!file.getCanonicalPath().startsWith(new File(baseDir).getCanonicalPath())) {
                    throw new TftpException("Access violation for file '" + filename + "'",
                            TftpException.ACCESS_VIOLATION);
                }

                if (!file.exists() || !file.isFile()) {
                    throw new TftpException("File '" + filename + "' not found", TftpException.FILE_NOT_FOUND);
                }

                if (!file.canRead()) {
                    throw new TftpException("Cannot read file '" + filename + "'", TftpException.ACCESS_VIOLATION);
                }

                System.out.println("[" + clientAddress + ":" + clientPort + "] Sending file: " + filename + " (" + file.length() + " bytes)");

                try (FileInputStream fis = new FileInputStream(file)) {
                    int blockNumber = 1;
                    byte[] buffer = new byte[512];
                    int bytesRead;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);

                        TftpPacket dataPacket = TftpPacket.createDATA(blockNumber, data);
                        sendPacketWithRetry(socket, clientAddress, clientPort, dataPacket, 5);

                        if (!receiveAck(socket, clientAddress, clientPort, blockNumber)) {
                            System.out.println("[" + clientAddress + ":" + clientPort + "] Timeout waiting for ACK " + blockNumber + ", aborting");
                            return;
                        }

                        blockNumber++;
                    }

                    TftpPacket finalPacket = TftpPacket.createDATA(blockNumber, new byte[0]);
                    sendPacketWithRetry(socket, clientAddress, clientPort, finalPacket, 5);
                    receiveAck(socket, clientAddress, clientPort, blockNumber);

                    System.out.println("[" + clientAddress + ":" + clientPort + "] File sent successfully: " + filename);
                } catch (IOException ioe) {
                    throw new TftpException("Failed to read file '" + filename + "'", TftpException.UNDEFINED, ioe);
                }
            } catch (IOException ioe) {
                throw new TftpException("Access violation for file '" + filename + "'", TftpException.ACCESS_VIOLATION, ioe);
            }
        }

        private void sendPacket(DatagramSocket socket, InetAddress address, int port,
                                TftpPacket packet) throws IOException {
            byte[] data = packet.toBytes();
            DatagramPacket udpPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(udpPacket);
        }

        private void sendPacketWithRetry(DatagramSocket socket, InetAddress address, int port,
                                         TftpPacket packet, int maxRetries) throws IOException {
            for (int i = 0; i < maxRetries; i++) {
                try {
                    sendPacket(socket, address, port, packet);
                    return;
                } catch (IOException e) {
                    if (i == maxRetries - 1) {
                        throw e;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry", ie);
                    }
                }
            }
        }

        private void sendError(DatagramSocket socket, InetAddress address, int port,
                               int errorCode, String message) throws IOException {
            TftpPacket errorPacket = TftpPacket.createERROR(errorCode, message);
            sendPacket(socket, address, port, errorPacket);
            System.err.println("[" + address + ":" + port + "] Error sent: " +
                    errorCode + " - " + message);
        }

        private boolean receiveAck(DatagramSocket socket, InetAddress expectedAddress, int expectedPort, int expectedBlock) throws IOException {
            byte[] buffer = new byte[516]; // ACK is small but use 516 for safety
            DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(ackPacket);

                // If TID doesn't match, send UNKNOWN_TRANSFER_ID to the sender and ignore packet
                InetAddress senderAddr = ackPacket.getAddress();
                int senderPort = ackPacket.getPort();
                if (!senderAddr.equals(expectedAddress) || senderPort != expectedPort) {
                    sendError(socket, senderAddr, senderPort, TftpException.UNKNOWN_TRANSFER_ID, "Unknown transfer ID");
                    return false;
                }

                byte[] dat = new byte[ackPacket.getLength()];
                System.arraycopy(ackPacket.getData(), 0, dat, 0, ackPacket.getLength());
                TftpPacket tftpAck = TftpPacket.fromBytes(dat);
                if (tftpAck.getOpCode() == TftpOpCode.ERROR) {
                    System.err.println("Received ERROR: " + tftpAck.getErrorMessage());
                    return false;
                }

                return tftpAck.getOpCode() == TftpOpCode.ACK &&
                        tftpAck.getBlockNumber() == expectedBlock;

            } catch (SocketTimeoutException e) {
                return false;
            }
        }

        private TftpPacket receiveDataPacket(DatagramSocket socket, InetAddress expectedAddress, int expectedPort, int expectedBlock)
                throws IOException {
            byte[] buffer = new byte[516];
            DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(dataPacket);

                InetAddress senderAddr = dataPacket.getAddress();
                int senderPort = dataPacket.getPort();
                if (!senderAddr.equals(expectedAddress) || senderPort != expectedPort) {
                    // reply to the unexpected sender with UNKNOWN_TRANSFER_ID (5)
                    sendError(socket, senderAddr, senderPort, TftpException.UNKNOWN_TRANSFER_ID, "Unknown transfer ID");
                    return null;
                }

                byte[] receivedData = new byte[dataPacket.getLength()];
                System.arraycopy(buffer, 0, receivedData, 0, dataPacket.getLength());

                TftpPacket packet = TftpPacket.fromBytes(receivedData);

                if (packet.getOpCode() == TftpOpCode.ERROR) {
                    System.err.println("Received ERROR: " + packet.getErrorMessage());
                    return null;
                }

                if (packet.getOpCode() != TftpOpCode.DATA) {
                    System.err.println("Expected DATA, got: " + packet.getOpCode());
                    return null;
                }

                if (packet.getBlockNumber() != expectedBlock) {
                    System.err.println("Expected block " + expectedBlock +
                            ", got: " + packet.getBlockNumber());
                    return null;
                }

                return packet;

            } catch (SocketTimeoutException e) {
                return null;
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String baseDir = DEFAULT_DIR;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("-d") && i + 1 < args.length) {
                baseDir = args[i + 1];
                i++;
            } else if (args[i].equals("-h") || args[i].equals("--help")) {
                printHelp();
                return;
            }
        }

        TftpServer server = new TftpServer(port, baseDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down TFTP server...");
            server.stop();
        }));

        server.start();
    }

    private static void printHelp() {
        System.out.println("TFTP Server - RFC 1350 compliant");
        System.out.println("Usage: java TftpServer [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -p PORT    Port number (default: 69)");
        System.out.println("  -d DIR     Base directory (default: ./tftp-server-files)");
        System.out.println("  -h, --help Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java TftpServer");
        System.out.println("  java TftpServer -p 6969 -d /var/tftp");
    }
}
