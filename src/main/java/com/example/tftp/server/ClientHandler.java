package com.example.tftp.server;

import com.example.tftp.model.TftpException;
import com.example.tftp.model.TftpPacket;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class ClientHandler extends Thread {
    private static final int DEFAULT_BLOCK_SIZE = 512;
    private static final int TIMEOUT = 5000;
    private static final int MAX_RETRIES = 5;

    private DatagramSocket socket;
    private InetAddress clientAddress;
    private int clientPort;
    private TftpPacket requestPacket;
    private String baseDirectory;

    public ClientHandler(DatagramSocket socket, InetAddress clientAddress,
                         int clientPort, TftpPacket requestPacket, String baseDirectory) {
        this.socket = socket;
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        this.requestPacket = requestPacket;
        this.baseDirectory = baseDirectory;
    }

    @Override
    public void run() {
        try {
            if (requestPacket.getOpCode() == com.example.tftp.model.TftpOpCode.RRQ) {
                handleReadRequest();
            } else if (requestPacket.getOpCode() == com.example.tftp.model.TftpOpCode.WRQ) {
                handleWriteRequest();
            }
        } catch (Exception e) {
            System.err.println("Error handling client " + clientAddress + ":" +
                    clientPort + ": " + e.getMessage());
            sendError(0, e.getMessage());
        }
    }

    private void handleReadRequest() throws IOException, TftpException {
        String filename = requestPacket.getFilename();
        File file = new File(baseDirectory, filename);

        if (!file.exists() || !file.isFile()) {
            sendError(1, "File not found");
            return;
        }

        if (!file.getCanonicalPath().startsWith(new File(baseDirectory).getCanonicalPath())) {
            sendError(2, "Access violation");
            return;
        }

        try (FileInputStream fileInput = new FileInputStream(file)) {
            int blockNumber = 1;
            boolean lastPacket = false;

            while (!lastPacket) {
                byte[] buffer = new byte[DEFAULT_BLOCK_SIZE];
                int bytesRead = fileInput.read(buffer);

                if (bytesRead == -1) {
                    bytesRead = 0;
                }

                byte[] dataToSend = new byte[bytesRead];
                System.arraycopy(buffer, 0, dataToSend, 0, bytesRead);

                TftpPacket dataPacket = TftpPacket.createDATA(blockNumber, dataToSend);
                sendPacket(dataPacket);

                TftpPacket ackPacket = receiveAck(blockNumber);

                if (ackPacket.getOpCode() != com.example.tftp.model.TftpOpCode.ACK ||
                        ackPacket.getBlockNumber() != blockNumber) {
                    throw new TftpException("Invalid ACK received", 0);
                }

                lastPacket = bytesRead < DEFAULT_BLOCK_SIZE;
                blockNumber++;
            }

            System.out.println("File " + filename + " sent to " +
                    clientAddress + ":" + clientPort);
        }
    }

    private void handleWriteRequest() throws IOException, TftpException {
        String filename = requestPacket.getFilename();
        File file = new File(baseDirectory, filename);

        if (!file.getCanonicalPath().startsWith(new File(baseDirectory).getCanonicalPath())) {
            sendError(2, "Access violation");
            return;
        }

        TftpPacket ack0 = TftpPacket.createACK(0);
        sendPacket(ack0);

        try (FileOutputStream fileOutput = new FileOutputStream(file)) {
            int blockNumber = 1;
            boolean lastPacket = false;

            while (!lastPacket) {
                TftpPacket dataPacket = receiveData(blockNumber);

                byte[] data = dataPacket.getData();
                fileOutput.write(data);

                TftpPacket ack = TftpPacket.createACK(blockNumber);
                sendPacket(ack);

                lastPacket = data.length < DEFAULT_BLOCK_SIZE;
                blockNumber++;
            }

            System.out.println("File " + filename + " received from " +
                    clientAddress + ":" + clientPort);
        }
    }

    private void sendPacket(TftpPacket packet) throws IOException {
        byte[] data = packet.toBytes();
        DatagramPacket udpPacket = new DatagramPacket(data, data.length,
                clientAddress, clientPort);
        socket.send(udpPacket);
    }

    private TftpPacket receivePacket() throws IOException, TftpException {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                byte[] buffer = new byte[516];
                DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout(TIMEOUT);
                socket.receive(udpPacket);

                byte[] receivedData = new byte[udpPacket.getLength()];
                System.arraycopy(buffer, 0, receivedData, 0, udpPacket.getLength());

                return TftpPacket.fromBytes(receivedData);

            } catch (SocketTimeoutException e) {
                if (i == MAX_RETRIES - 1) {
                    throw new TftpException("Timeout receiving packet", 0, e);
                }
            }
        }
        throw new TftpException("Failed to receive packet after retries", 0);
    }

    private TftpPacket receiveAck(int expectedBlockNumber)
            throws IOException, TftpException {

        TftpPacket packet = receivePacket();

        if (packet.getOpCode() != com.example.tftp.model.TftpOpCode.ACK) {
            throw new TftpException("Expected ACK packet, got: " +
                    packet.getOpCode(), 0);
        }

        if (packet.getBlockNumber() != expectedBlockNumber) {
            throw new TftpException("Expected ACK for block " +
                    expectedBlockNumber + ", got: " +
                    packet.getBlockNumber(), 0);
        }

        return packet;
    }

    private TftpPacket receiveData(int expectedBlockNumber)
            throws IOException, TftpException {

        TftpPacket packet = receivePacket();

        if (packet.getOpCode() != com.example.tftp.model.TftpOpCode.DATA) {
            throw new TftpException("Expected DATA packet, got: " +
                    packet.getOpCode(), 0);
        }

        if (packet.getBlockNumber() != expectedBlockNumber) {
            throw new TftpException("Expected DATA for block " +
                    expectedBlockNumber + ", got: " +
                    packet.getBlockNumber(), 0);
        }

        return packet;
    }

    private void sendError(int errorCode, String errorMessage) {
        try {
            TftpPacket errorPacket = TftpPacket.createERROR(errorCode, errorMessage);
            sendPacket(errorPacket);
        } catch (IOException e) {
            System.err.println("Failed to send error packet: " + e.getMessage());
        }
    }
}