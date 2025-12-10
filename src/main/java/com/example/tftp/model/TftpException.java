package com.example.tftp.model;

public class TftpException extends Exception {
    private final int errorCode;

    public static final int UNDEFINED = 0;
    public static final int FILE_NOT_FOUND = 1;
    public static final int ACCESS_VIOLATION = 2;
    public static final int DISK_FULL = 3;
    public static final int ILLEGAL_OPERATION = 4;
    public static final int UNKNOWN_TRANSFER_ID = 5;
    public static final int FILE_EXISTS = 6;
    public static final int NO_SUCH_USER = 7;

    public TftpException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public TftpException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public TftpPacket toPacket() {
        return TftpPacket.createERROR(errorCode, getMessage());
    }
}