package de.jamsintown.bild;

import lombok.extern.slf4j.Slf4j;

import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimaler JPEG-EXIF-Parser für DateTimeOriginal (Tag 0x9003).
 * Reflection-frei — funktioniert im GraalVM Native Image.
 */
@Slf4j
public class ExifUtils {

    private static final DateTimeFormatter EXIF_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private ExifUtils() {}

    public static ZonedDateTime readCapturedAt(Path imagePath) {
        try (RandomAccessFile f = new RandomAccessFile(imagePath.toFile(), "r")) {
            // JPEG-Marker prüfen
            if (f.readUnsignedByte() != 0xFF || f.readUnsignedByte() != 0xD8) return null;

            while (f.getFilePointer() < f.length() - 4) {
                if (f.readUnsignedByte() != 0xFF) return null;
                int marker = f.readUnsignedByte();
                int segLen = f.readUnsignedShort(); // inkl. Längenfeld selbst

                if (marker == 0xE1) { // APP1 = EXIF
                    byte[] app1 = new byte[segLen - 2];
                    f.readFully(app1);
                    ZonedDateTime dt = parseExif(app1);
                    if (dt != null) return dt;
                } else if (marker == 0xDA) { // SOS — Bilddaten beginnen
                    break;
                } else {
                    f.skipBytes(segLen - 2);
                }
            }
        } catch (Exception e) {
            log.debug("EXIF nicht lesbar für {}: {}", imagePath.getFileName(), e.getMessage());
        }
        return null;
    }

    private static ZonedDateTime parseExif(byte[] data) {
        // "Exif\0\0" Header prüfen
        if (data.length < 14 || data[0] != 'E' || data[1] != 'x' || data[2] != 'i'
                || data[3] != 'f' || data[4] != 0 || data[5] != 0) return null;

        int tiffOffset = 6;
        ByteOrder order = (data[tiffOffset] == 'I') ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

        // IFD0-Offset lesen
        int ifd0Offset = tiffOffset + readInt(data, tiffOffset + 4, order);

        // DateTimeOriginal (0x9003) in IFD0 und SubIFD suchen
        String dt = findTag(data, tiffOffset, ifd0Offset, (short) 0x9003, order);
        if (dt == null) {
            // SubIFD (ExifIFD, Tag 0x8769) suchen
            String subIfdOffsetStr = findTag(data, tiffOffset, ifd0Offset, (short) 0x8769, order);
            if (subIfdOffsetStr != null) {
                try {
                    int subIfdOffset = tiffOffset + Integer.parseInt(subIfdOffsetStr);
                    dt = findTag(data, tiffOffset, subIfdOffset, (short) 0x9003, order);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (dt == null) return null;

        try {
            return LocalDateTime.parse(dt.trim(), EXIF_FMT).atZone(ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    /** Sucht ein Tag im IFD und gibt den Wert als String zurück. */
    private static String findTag(byte[] data, int tiffBase, int ifdOffset, short targetTag, ByteOrder order) {
        if (ifdOffset + 2 > data.length) return null;
        int count = readShort(data, ifdOffset, order);
        for (int i = 0; i < count; i++) {
            int entryOffset = ifdOffset + 2 + i * 12;
            if (entryOffset + 12 > data.length) break;
            short tag = (short) readShort(data, entryOffset, order);
            if (tag == targetTag) {
                int type = readShort(data, entryOffset + 2, order);
                int components = readInt(data, entryOffset + 4, order);
                int valueOffset = entryOffset + 8;

                if (type == 2) { // ASCII
                    int dataLen = components;
                    int absOffset = (dataLen > 4)
                            ? tiffBase + readInt(data, valueOffset, order)
                            : valueOffset;
                    if (absOffset + dataLen > data.length) return null;
                    return new String(data, absOffset, Math.max(0, dataLen - 1));
                } else if (type == 4 || type == 9) { // LONG / SLONG — für SubIFD-Offset
                    return String.valueOf(readInt(data, valueOffset, order));
                }
            }
        }
        return null;
    }

    private static int readShort(byte[] data, int offset, ByteOrder order) {
        int a = data[offset] & 0xFF, b = data[offset + 1] & 0xFF;
        return (order == ByteOrder.LITTLE_ENDIAN) ? (b << 8 | a) : (a << 8 | b);
    }

    private static int readInt(byte[] data, int offset, ByteOrder order) {
        int a = data[offset] & 0xFF, b = data[offset + 1] & 0xFF;
        int c = data[offset + 2] & 0xFF, d = data[offset + 3] & 0xFF;
        return (order == ByteOrder.LITTLE_ENDIAN)
                ? (d << 24 | c << 16 | b << 8 | a)
                : (a << 24 | b << 16 | c << 8 | d);
    }
}
