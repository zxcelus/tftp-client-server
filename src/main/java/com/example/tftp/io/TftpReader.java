package com.example.tftp.io;

import com.example.tftp.model.TftpException;
import com.example.tftp.model.TftpPacket;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;

public class TftpReader {
    private static final int BUFFER_SIZE = 516; // 512 data + 4 header
    private static final int MAX_RETRIES = 5;

    private volatile boolean cancelled;
    private ProgressCallback progressCallback;
    private Integer serverDataPort; // порт сервера для DATA

    public interface ProgressCallback {
        void onProgress(long transferred, long total); // total may be -1 if unknown
        void onLog(String message);
    }

    public TftpReader() {
        this.cancelled = false;
        this.serverDataPort = null;
    }

    public void readFile(String remoteFilename, File localFile, InetAddress serverAddress, int serverPort,
                         ProgressCallback callback) throws IOException, TftpException {

        this.progressCallback = callback;
        this.cancelled = false;
        this.serverDataPort = null; // reset before new transfer

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);

            // --- SEND RRQ ---
            TftpPacket rrq = TftpPacket.createRRQ(remoteFilename);
            DatagramPacket rrqPacket = new DatagramPacket(rrq.toBytes(), rrq.toBytes().length,
                    serverAddress, serverPort);
            socket.send(rrqPacket);
            if (callback != null) callback.onLog("RRQ sent for file: " + remoteFilename);

            try (FileOutputStream fos = new FileOutputStream(localFile)) {
                int expectedBlock = 1;
                boolean lastPacket = false;
                long bytesWritten = 0;

                while (!lastPacket && !cancelled) {
                    ReceivedData rd = receiveDataPacket(socket, expectedBlock, callback);
                    if (rd == null || rd.packet == null) {
                        throw new TftpException("Failed to receive data for block " + expectedBlock,
                                TftpException.UNDEFINED);
                    }

                    byte[] data = rd.packet.getData();
                    if (data != null && data.length > 0) {
                        fos.write(data);
                        bytesWritten += data.length;
                    }

                    // send ACK back to the server's TID
                    sendAck(socket, rd.addr, rd.port, expectedBlock, callback);

                    expectedBlock++;
                    if (data == null || data.length < 512) lastPacket = true;

                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesWritten, -1); // total unknown for download
                    }

                    if (cancelled) {
                        if (progressCallback != null) progressCallback.onLog("Download cancelled by user");
                        break;
                    }
                }
            }
        }
    }

    private static class ReceivedData {
        TftpPacket packet;
        InetAddress addr;
        int port;
    }

    private ReceivedData receiveDataPacket(DatagramSocket socket, int expectedBlock,
                                           ProgressCallback callback) throws IOException, TftpException {

        int retries = 0;
        while (retries < MAX_RETRIES && !cancelled) {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                InetAddress addr = receivePacket.getAddress();
                int port = receivePacket.getPort();

                if (serverDataPort == null) {
                    serverDataPort = port;
                    if (callback != null) callback.onLog("Server data port set to " + serverDataPort);
                }

                if (port != serverDataPort) {
                    if (callback != null)
                        callback.onLog("Ignored packet from wrong port: " + addr + ":" + port + " (expected " + serverDataPort + ")");
                    continue;
                }

                byte[] receivedData = new byte[receivePacket.getLength()];
                System.arraycopy(buffer, 0, receivedData, 0, receivePacket.getLength());

                TftpPacket packet = TftpPacket.fromBytes(receivedData);

                if (packet.getOpCode() == com.example.tftp.model.TftpOpCode.ERROR) {
                    throw new TftpException("Server error: " + packet.getErrorMessage(),
                            packet.getErrorCode());
                }

                if (packet.getOpCode() != com.example.tftp.model.TftpOpCode.DATA) {
                    if (callback != null) callback.onLog("Unexpected packet type: " + packet.getOpCode());
                    continue;
                }

                if (packet.getBlockNumber() != expectedBlock) {
                    if (callback != null)
                        callback.onLog("Received block " + packet.getBlockNumber() + ", expected " + expectedBlock);
                    continue;
                }

                ReceivedData rd = new ReceivedData();
                rd.packet = packet;
                rd.addr = addr;
                rd.port = port;
                return rd;

            } catch (SocketTimeoutException e) {
                retries++;
                if (callback != null && retries < MAX_RETRIES) {
                    callback.onLog("Timeout waiting for block " + expectedBlock +
                            ", retry " + retries + "/" + MAX_RETRIES);
                }
            }
        }

        if (cancelled) return null;

        throw new TftpException("Failed to receive data after " + MAX_RETRIES + " retries",
                TftpException.UNDEFINED);
    }

    private void sendAck(DatagramSocket socket, InetAddress address, int destPort,
                         int blockNumber, ProgressCallback callback) throws IOException {

        int actualDest = (serverDataPort != null) ? serverDataPort : destPort;
        TftpPacket ack = TftpPacket.createACK(blockNumber);
        DatagramPacket ackPacket = new DatagramPacket(ack.toBytes(), ack.toBytes().length,
                address, actualDest);
        socket.send(ackPacket);

        if (callback != null && blockNumber % 20 == 0) {
            callback.onLog("ACK sent for block " + blockNumber + " to port " + actualDest);
        }
    }
}
