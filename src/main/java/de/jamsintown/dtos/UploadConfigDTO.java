package de.jamsintown.dtos;

import java.util.List;

/**
 * DTO-Klasse für die Upload-Konfiguration
 */
public class UploadConfigDTO {
    public long maxSize;
    public List<String> allowedTypes;

    public UploadConfigDTO(long maxSize, List<String> allowedTypes) {
        this.maxSize = maxSize;
        this.allowedTypes = allowedTypes;
    }
}