package com.example.tftp.model;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class TftpPacketTest {

    @Test
    public void testRRQPacketSerialization() throws IOException {
        TftpPacket original = TftpPacket.createRRQ("test.txt", TftpMode.OCTET);
        byte[] bytes = original.toBytes();
        //bytes[bytes.length - 1] = 1;
        TftpPacket restored = TftpPacket.fromBytes(bytes);

        assertEquals(TftpOpCode.RRQ, restored.getOpCode());
        assertEquals("test.txt", restored.getFilename());
        assertEquals(TftpMode.OCTET, restored.getMode());
    }

    @Test
    public void testWRQPacketSerialization() throws IOException {
        TftpPacket original = TftpPacket.createWRQ("upload.txt", TftpMode.NETASCII);
        byte[] bytes = original.toBytes();
        TftpPacket restored = TftpPacket.fromBytes(bytes);

        assertEquals(TftpOpCode.WRQ, restored.getOpCode());
        assertEquals("upload.txt", restored.getFilename());
        assertEquals(TftpMode.NETASCII, restored.getMode());
    }

    @Test
    public void testDATAPacketSerialization() throws IOException {
        byte[] testData = "Hello, TFTP!".getBytes();
        TftpPacket original = TftpPacket.createDATA(123, testData);
        byte[] bytes = original.toBytes();
        //bytes[3] = 99;
        TftpPacket restored = TftpPacket.fromBytes(bytes);

        assertEquals(TftpOpCode.DATA, restored.getOpCode());
        assertEquals(123, restored.getBlockNumber());
        assertArrayEquals(testData, restored.getData());
    }

    @Test
    public void testACKPacketSerialization() throws IOException {
        TftpPacket original = TftpPacket.createACK(456);
        byte[] bytes = original.toBytes();
        TftpPacket restored = TftpPacket.fromBytes(bytes);

        assertEquals(TftpOpCode.ACK, restored.getOpCode());
        assertEquals(456, restored.getBlockNumber());
    }

    @Test
    public void testERRORPacketSerialization() throws IOException {
        TftpPacket original = TftpPacket.createERROR(1, "File not found");
        byte[] bytes = original.toBytes();
        TftpPacket restored = TftpPacket.fromBytes(bytes);

        assertEquals(TftpOpCode.ERROR, restored.getOpCode());
        assertEquals(1, restored.getErrorCode());
        assertEquals("File not found", restored.getErrorMessage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidOpCode() {
        TftpOpCode.fromValue(99);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMode() {
        TftpMode.fromString("invalid");
    }

    @Test(expected = IOException.class)
    public void testInvalidPacket() throws IOException {
        byte[] invalidData = {0, 0};
        TftpPacket.fromBytes(invalidData);
    }

    @Test(expected = IOException.class)  // Changed from IllegalArgumentException
    public void testInvalidOpCodeInPacket() throws IOException {
        byte[] invalidOpCodeData = {0, 99, 0}; // Invalid opcode 99
        TftpPacket.fromBytes(invalidOpCodeData);
    }

    @Test
    public void testEmptyPacket() {
        try {
            TftpPacket.fromBytes(new byte[0]);
            fail("Expected exception for empty packet");
        } catch (IOException e) {
            // Expected
        }
    }
}