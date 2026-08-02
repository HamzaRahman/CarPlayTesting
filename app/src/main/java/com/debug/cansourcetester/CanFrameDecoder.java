package com.debug.cansourcetester;

/**
 * Re-implements PacketManager.unpackHead_2E_Standard (packetType 0), the
 * framing CanService.java uses by default -- and specifically for GID 1364
 * (Yage9DataDecoder, the Hiworld Honda decoder), which never overrides
 * packetType/baudrate so it falls back to this default framing at 38400
 * baud. Reverse-engineered from the decompiled PacketManager.java:
 *
 *   [0x2E] [B] [LEN] [LEN bytes of payload] [checksum]
 *
 * checksum = (~(B + LEN + sum(payload bytes)) & 0xFF), i.e. the frame is
 * valid when (running sum XOR 0xFF) & 0xFF equals the received checksum
 * byte. Feed bytes one at a time; feed() returns a completed, checksum-
 * verified frame (as [B, LEN, payload...]) once one is fully decoded, or
 * null while still mid-frame / on checksum failure.
 */
public class CanFrameDecoder {

    private static final int MAX_FRAME = 255;

    private int state = 0; // 0=waiting for sync, 1=B, 2=LEN, 3=payload, 4=checksum
    private final byte[] buf = new byte[MAX_FRAME];
    private int count = 0;
    private int remaining = 0;
    private int checksum = 0;

    public byte[] feed(int data) {
        data &= 0xFF;
        switch (state) {
            case 0:
                if (data == 0x2E) {
                    reset();
                    state = 1;
                }
                break;
            case 1:
                buf[count++] = (byte) data;
                checksum += data;
                state = 2;
                break;
            case 2:
                if (data == 0 || data > buf.length - 4) {
                    reset();
                    break;
                }
                buf[count++] = (byte) data;
                checksum += data;
                remaining = data;
                state = 3;
                break;
            case 3:
                buf[count++] = (byte) data;
                checksum += data;
                remaining--;
                if (remaining == 0) state = 4;
                break;
            case 4:
                boolean ok = ((checksum ^ 0xFF) & 0xFF) == data;
                byte[] result = null;
                if (ok) {
                    result = new byte[count];
                    System.arraycopy(buf, 0, result, 0, count);
                }
                reset();
                return result;
        }
        return null;
    }

    private void reset() {
        state = 0;
        count = 0;
        remaining = 0;
        checksum = 0;
    }

    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
