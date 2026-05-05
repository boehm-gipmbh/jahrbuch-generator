package de.jamsintown.bild;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
public class ExifUtils {

    private ExifUtils() {}

    public static ZonedDateTime readCapturedAt(Path imagePath) {
        try {
            com.drew.metadata.Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
            ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (dir != null) {
                java.util.Date date = dir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                        java.util.TimeZone.getDefault());
                if (date != null) {
                    return date.toInstant().atZone(ZoneId.systemDefault());
                }
            }
        } catch (Exception e) {
            log.debug("Kein EXIF-Datum lesbar für {}: {}", imagePath.getFileName(), e.getMessage());
        }
        return null;
    }
}
