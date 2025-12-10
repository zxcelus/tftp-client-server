package com.example.tftp.model;

public enum TftpOpCode {
    RRQ(1),    // Read request
    WRQ(2),    // Write request
    DATA(3),   // Data packet
    ACK(4),    // Acknowledgment
    ERROR(5);  // Error packet

    private final int value;

    TftpOpCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static TftpOpCode fromValue(int value) {
        for (TftpOpCode opCode : values()) {
            if (opCode.getValue() == value) {
                return opCode;
            }
        }
        throw new IllegalArgumentException("Invalid TFTP opcode: " + value);
    }
}