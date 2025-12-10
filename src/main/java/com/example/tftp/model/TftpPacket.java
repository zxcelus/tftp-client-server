package com.example.tftp.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TftpPacket {
    private TftpOpCode opCode;
    private byte[] data;
    private String filename;
    private TftpMode mode;
    private int blockNumber;
    private int errorCode;
    private String errorMessage;

    // ---------------------- FACTORY METHODS ----------------------
    public static TftpPacket createRRQ(String filename) {
        return createRRQ(filename, TftpMode.OCTET);
    }

    public static TftpPacket createRRQ(String filename, TftpMode mode) {
        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.RRQ;
        packet.filename = filename;
        packet.mode = mode;
        return packet;
    }

    public static TftpPacket createWRQ(String filename) {
        return createWRQ(filename, TftpMode.OCTET);
    }

    public static TftpPacket createWRQ(String filename, TftpMode mode) {
        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.WRQ;
        packet.filename = filename;
        packet.mode = mode;
        return packet;
    }

    public static TftpPacket createDATA(int blockNumber, byte[] data) {
        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.DATA;
        packet.blockNumber = blockNumber;
        packet.data = data;
        return packet;
    }

    public static TftpPacket createACK(int blockNumber) {
        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.ACK;
        packet.blockNumber = blockNumber;
        return packet;
    }

    public static TftpPacket createERROR(int errorCode, String errorMessage) {
        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.ERROR;
        packet.errorCode = errorCode;
        packet.errorMessage = errorMessage;
        return packet;
    }

    // ---------------------- SERIALIZATION ----------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(2).putShort((short) opCode.getValue()).array());

        switch (opCode) {
            case RRQ:
            case WRQ:
                output.write(filename.getBytes(StandardCharsets.UTF_8));
                output.write(0);
                output.write(mode.getValue().getBytes(StandardCharsets.UTF_8));
                output.write(0);
                break;

            case DATA:
                output.write(ByteBuffer.allocate(2).putShort((short) blockNumber).array());
                if (data != null) {
                    output.write(data);
                }
                break;

            case ACK:
                output.write(ByteBuffer.allocate(2).putShort((short) blockNumber).array());
                break;

            case ERROR:
                output.write(ByteBuffer.allocate(2).putShort((short) errorCode).array());
                output.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                output.write(0);
                break;
        }

        return output.toByteArray();
    }

    // ---------------------- DESERIALIZATION ----------------------
    public static TftpPacket fromBytes(byte[] bytes) throws IOException {
        if (bytes.length < 2) throw new IOException("Invalid packet: too short");

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int opCodeValue = buffer.getShort() & 0xFFFF;

        TftpOpCode opCode;
        try {
            opCode = TftpOpCode.fromValue(opCodeValue);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid opcode in packet: " + opCodeValue, e);
        }

        try {
            switch (opCode) {
                case RRQ:
                case WRQ:
                    return parseRequestPacket(opCode, buffer);
                case DATA:
                    return parseDataPacket(buffer);
                case ACK:
                    return parseAckPacket(buffer);
                case ERROR:
                    return parseErrorPacket(buffer);
                default:
                    throw new IOException("Unknown opcode: " + opCodeValue);
            }
        } catch (BufferUnderflowException e) {
            throw new IOException("Malformed packet: insufficient data", e);
        }
    }


    private static TftpPacket parseRequestPacket(TftpOpCode opCode, ByteBuffer buffer) {
        StringBuilder filenameBuilder = new StringBuilder();
        char ch;
        while ((ch = (char) buffer.get()) != 0) filenameBuilder.append(ch);

        StringBuilder modeBuilder = new StringBuilder();
        while ((ch = (char) buffer.get()) != 0) modeBuilder.append(ch);

        TftpPacket packet = new TftpPacket();
        packet.opCode = opCode;
        packet.filename = filenameBuilder.toString();
        packet.mode = TftpMode.fromString(modeBuilder.toString());
        return packet;
    }

    private static TftpPacket parseDataPacket(ByteBuffer buffer) {
        int blockNumber = buffer.getShort() & 0xFFFF;
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.DATA;
        packet.blockNumber = blockNumber;
        packet.data = data;
        return packet;
    }

    private static TftpPacket parseAckPacket(ByteBuffer buffer) {
        int blockNumber = buffer.getShort() & 0xFFFF;

        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.ACK;
        packet.blockNumber = blockNumber;
        return packet;
    }

    private static TftpPacket parseErrorPacket(ByteBuffer buffer) {
        int errorCode = buffer.getShort() & 0xFFFF;
        StringBuilder messageBuilder = new StringBuilder();
        char ch;
        while (buffer.hasRemaining() && (ch = (char) buffer.get()) != 0) messageBuilder.append(ch);

        TftpPacket packet = new TftpPacket();
        packet.opCode = TftpOpCode.ERROR;
        packet.errorCode = errorCode;
        packet.errorMessage = messageBuilder.toString();
        return packet;
    }

    // ---------------------- GETTERS ----------------------
    public TftpOpCode getOpCode() { return opCode; }
    public byte[] getData() { return data; }
    public String getFilename() { return filename; }
    public TftpMode getMode() { return mode; }
    public int getBlockNumber() { return blockNumber; }
    public int getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public int getDataLength() { return data != null ? data.length : 0; }
}
