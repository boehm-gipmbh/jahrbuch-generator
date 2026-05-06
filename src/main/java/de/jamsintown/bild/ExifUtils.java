package de.jamsintown.bild;

import lombok.extern.slf4j.Slf4j;

import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimaler JPEG-EXIF-Parser für DateTimeOriginal (Tag 0x9003).
 * Reflection-frei — funktioniert im GraalVM Native Image.
 */
@Slf4j
public class ExifUtils {

    private static final DateTimeFormatter EXIF_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private ExifUtils() {}

    public static String readExifJson(Path imagePath) {
        try (RandomAccessFile f = new RandomAccessFile(imagePath.toFile(), "r")) {
            if (f.readUnsignedByte() != 0xFF || f.readUnsignedByte() != 0xD8) return null;
            while (f.getFilePointer() < f.length() - 4) {
                if (f.readUnsignedByte() != 0xFF) return null;
                int marker = f.readUnsignedByte();
                int segLen = f.readUnsignedShort();
                if (marker == 0xE1) {
                    byte[] app1 = new byte[segLen - 2];
                    f.readFully(app1);
                    String json = parseExifToJson(app1);
                    if (json != null) return json;
                } else if (marker == 0xDA) break;
                else f.skipBytes(segLen - 2);
            }
        } catch (Exception e) {
            log.debug("EXIF-JSON nicht lesbar für {}: {}", imagePath.getFileName(), e.getMessage());
        }
        return null;
    }

    private static String parseExifToJson(byte[] data) {
        if (data.length < 14 || data[0] != 'E' || data[1] != 'x' || data[2] != 'i'
                || data[3] != 'f' || data[4] != 0 || data[5] != 0) return null;

        int tb = 6;
        ByteOrder order = (data[tb] == 'I') ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        int ifd0 = tb + readInt(data, tb + 4, order);

        Map<String, Object> result = new LinkedHashMap<>();

        // IFD0
        String dt = (String) tagValue(data, tb, ifd0, (short) 0x9003, order);
        if (dt != null) result.put("capturedAt", dt);
        String make = (String) tagValue(data, tb, ifd0, (short) 0x010F, order);
        if (make != null) result.put("make", make);
        String model = (String) tagValue(data, tb, ifd0, (short) 0x0110, order);
        if (model != null) result.put("model", model);
        Number orientation = (Number) tagValue(data, tb, ifd0, (short) 0x0112, order);
        if (orientation != null) result.put("orientation", orientation);

        // ExifIFD (0x8769)
        Number exifIfdRaw = (Number) tagValue(data, tb, ifd0, (short) 0x8769, order);
        if (exifIfdRaw != null) {
            int exifIfd = tb + exifIfdRaw.intValue();
            if (!result.containsKey("capturedAt")) {
                String dtOrig = (String) tagValue(data, tb, exifIfd, (short) 0x9003, order);
                if (dtOrig != null) result.put("capturedAt", dtOrig);
            }
            Number iso = (Number) tagValue(data, tb, exifIfd, (short) 0x8827, order);
            if (iso != null) result.put("iso", iso);
            double[] expTime = (double[]) tagValue(data, tb, exifIfd, (short) 0x829A, order);
            if (expTime != null && expTime.length > 0) result.put("exposureTime", expTime[0]);
            double[] fNumber = (double[]) tagValue(data, tb, exifIfd, (short) 0x829D, order);
            if (fNumber != null && fNumber.length > 0) result.put("fNumber", fNumber[0]);
            double[] focalLen = (double[]) tagValue(data, tb, exifIfd, (short) 0x920A, order);
            if (focalLen != null && focalLen.length > 0) result.put("focalLength", focalLen[0]);
        }

        // GPS IFD (0x8825)
        Number gpsIfdRaw = (Number) tagValue(data, tb, ifd0, (short) 0x8825, order);
        if (gpsIfdRaw != null) {
            int gpsIfd = tb + gpsIfdRaw.intValue();
            String latRef = (String) tagValue(data, tb, gpsIfd, (short) 0x0001, order);
            double[] lat   = (double[]) tagValue(data, tb, gpsIfd, (short) 0x0002, order);
            String lonRef  = (String) tagValue(data, tb, gpsIfd, (short) 0x0003, order);
            double[] lon   = (double[]) tagValue(data, tb, gpsIfd, (short) 0x0004, order);
            Number altRef  = (Number) tagValue(data, tb, gpsIfd, (short) 0x0005, order);
            double[] alt   = (double[]) tagValue(data, tb, gpsIfd, (short) 0x0006, order);

            if (lat != null && lat.length == 3 && lon != null && lon.length == 3) {
                double latDeg = lat[0] + lat[1] / 60.0 + lat[2] / 3600.0;
                double lonDeg = lon[0] + lon[1] / 60.0 + lon[2] / 3600.0;
                if ("S".equals(latRef)) latDeg = -latDeg;
                if ("W".equals(lonRef)) lonDeg = -lonDeg;
                result.put("gpsLat", Math.round(latDeg * 1e7) / 1e7);
                result.put("gpsLon", Math.round(lonDeg * 1e7) / 1e7);
                if (alt != null && alt.length > 0) {
                    double altM = (altRef != null && altRef.intValue() == 1) ? -alt[0] : alt[0];
                    result.put("gpsAlt", Math.round(altM * 10.0) / 10.0);
                }
            }
        }

        if (result.isEmpty()) return null;
        return toJson(result);
    }

    private static Object tagValue(byte[] data, int tiffBase, int ifdOffset, short targetTag, ByteOrder order) {
        if (ifdOffset + 2 > data.length) return null;
        int count = readShort(data, ifdOffset, order);
        for (int i = 0; i < count; i++) {
            int e = ifdOffset + 2 + i * 12;
            if (e + 12 > data.length) break;
            if ((short) readShort(data, e, order) != targetTag) continue;
            int type = readShort(data, e + 2, order);
            int comps = readInt(data, e + 4, order);
            int valOff = e + 8;
            switch (type) {
                case 1: // BYTE
                    return data[valOff] & 0xFF;
                case 2: { // ASCII
                    int absOff = (comps > 4) ? tiffBase + readInt(data, valOff, order) : valOff;
                    if (absOff + comps > data.length) return null;
                    String s = new String(data, absOff, Math.max(0, comps - 1)).trim();
                    return s.isEmpty() ? null : s;
                }
                case 3: // SHORT
                    return readShort(data, valOff, order);
                case 4: case 9: // LONG / SLONG
                    return readInt(data, valOff, order);
                case 5: case 10: { // RATIONAL / SRATIONAL
                    if (comps < 1) return null;
                    int absOff = tiffBase + readInt(data, valOff, order);
                    double[] vals = new double[comps];
                    for (int c = 0; c < comps; c++) {
                        int pos = absOff + c * 8;
                        if (pos + 8 > data.length) break;
                        int num = readInt(data, pos, order);
                        int den = readInt(data, pos + 4, order);
                        vals[c] = (den != 0) ? (double) num / den : 0;
                    }
                    return vals;
                }
            }
        }
        return null;
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else if (v instanceof Double d) {
                sb.append(formatDouble(d));
            } else {
                sb.append(v);
            }
        }
        return sb.append('}').toString();
    }

    private static String formatDouble(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        String s = String.format(Locale.US, "%.7f", d).replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }

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
