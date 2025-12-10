package com.example.tftp.io;

import com.example.tftp.model.TftpException;
import com.example.tftp.model.TftpPacket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;

public class TftpWriter {
    private static final int BUFFER_SIZE = 516; // 512 data + 4 header
    private static final int MAX_RETRIES = 5;

    private volatile boolean cancelled;
    private ProgressCallback progressCallback;
    private Integer serverDataPort;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    public interface ProgressCallback {
        void onProgress(long transferred, long total);
        void onLog(String message);
    }

    public TftpWriter() {
        this.cancelled = false;
        this.serverDataPort = null;
    }

    public void writeFile(String filename, File localFile, InetAddress serverAddress, int serverPort,
                          ProgressCallback callback) throws IOException, TftpException {

        this.progressCallback = callback;
        this.cancelled = false;
        this.serverDataPort = null;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        try (DatagramSocket socket = new DatagramSocket()) {
            this.socket = socket;
            socket.setSoTimeout(3000);

            // --- SEND WRQ ---
            TftpPacket wrq = TftpPacket.createWRQ(filename);
            DatagramPacket wrqPacket = new DatagramPacket(wrq.toBytes(), wrq.toBytes().length,
                    serverAddress, serverPort);
            socket.send(wrqPacket);
            if (callback != null) callback.onLog("WRQ sent for file: " + filename);

            try (FileInputStream fis = new FileInputStream(localFile)) {
                int blockNumber = 0;
                long bytesTransferred = 0;
                long totalSize = localFile.length();
                boolean lastPacket = false;

                while (!lastPacket && !cancelled) {
                    // --- wait for ACK for previous block ---
                    TftpPacket ackPacket = receivePacket(blockNumber);
                    if (ackPacket == null) {
                        throw new TftpException("Did not receive ACK for block " + blockNumber,
                                TftpException.UNDEFINED);
                    }

                    blockNumber++;
                    byte[] buffer = new byte[512];
                    int bytesRead = fis.read(buffer);
                    if (bytesRead < 512) lastPacket = true;
                    if (bytesRead == -1) bytesRead = 0;

                    byte[] dataToSend = new byte[bytesRead];
                    System.arraycopy(buffer, 0, dataToSend, 0, bytesRead);

                    TftpPacket dataPacket = TftpPacket.createDATA(blockNumber, dataToSend);
                    sendPacketWithRetry(dataPacket, MAX_RETRIES, "DATA block " + blockNumber);

                    bytesTransferred += bytesRead;

                    // --- обновляем прогресс после каждого блока ---
                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesTransferred, totalSize);
                    }

                    // проверка отмены
                    if (cancelled) {
                        if (progressCallback != null) progressCallback.onLog("Upload cancelled by user");
                        break;
                    }
                }
            }
        }
    }

    private TftpPacket receivePacket(int expectedBlock) throws TftpException, IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(receivePacket);
            InetAddress addr = receivePacket.getAddress();
            int port = receivePacket.getPort();

            // remember server port (TID) from first response
            if (serverDataPort == null) {
                serverDataPort = port;
                if (progressCallback != null) progressCallback.onLog("Detected server data port: " + serverDataPort);
            }

            if (port != serverDataPort) {
                if (progressCallback != null) {
                    progressCallback.onLog("Received packet from wrong source: " +
                            addr + ":" + port + " (expected " + serverDataPort + ")");
                }
                return null;
            }

            byte[] receivedData = new byte[receivePacket.getLength()];
            System.arraycopy(buffer, 0, receivedData, 0, receivePacket.getLength());

            TftpPacket packet = TftpPacket.fromBytes(receivedData);

            if (packet.getOpCode() == com.example.tftp.model.TftpOpCode.ERROR) {
                throw new TftpException("Server error: " + packet.getErrorMessage(), packet.getErrorCode());
            }

            return packet;

        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    private void sendPacketWithRetry(TftpPacket packet, int maxRetries, String packetName) throws IOException {
        for (int i = 0; i < maxRetries; i++) {
            if (cancelled) return; // немедленно прекращаем отправку
            try {
                byte[] data = packet.toBytes();
                int destPort = (serverDataPort != null) ? serverDataPort : serverPort;
                DatagramPacket udpPacket = new DatagramPacket(data, data.length, serverAddress, destPort);
                socket.send(udpPacket);
                return;
            } catch (IOException e) {
                if (i == maxRetries - 1) throw e;
                if (progressCallback != null)
                    progressCallback.onLog("Retry " + (i + 1) + "/" + maxRetries + " for " + packetName);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry", ie);
                }
            }
        }
    }
}
