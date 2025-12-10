package com.example.tftp.model;

public enum TftpMode {
    NETASCII("netascii"),
    OCTET("octet");

    private final String value;

    TftpMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TftpMode fromString(String mode) {
        for (TftpMode m : values()) {
            if (m.getValue().equalsIgnoreCase(mode)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Invalid TFTP mode: " + mode);
    }
}