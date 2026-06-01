package de.jamsintown.pdf;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Persistierbare Gruppen-Einstellungen für den PDF-Export.
 * Wird in der AppConfig der Gruppe als JSONB gespeichert.
 */
@RegisterForReflection
public record PdfSettings(
    float storyHeaderTitleSize,
    float storyHeaderSubtitleSize,
    float textTitleSize,
    float textDescriptionSize,
    float imageCaptionSize,
    float commentTopLevelSize,
    float commentReplySize,
    String passepartoutStyle,
    String pdfPassword,
    BackgroundImage coverFrontBackground,
    BackgroundImage coverBackBackground,
    BackgroundImage tocBackground,
    boolean tocEnabled,
    String tocTitle,
    float tocTitleSize,
    float tocEntrySize,
    boolean tocShowPageNumbers,
    int tocColumns
) {
    public static PdfSettings defaults() {
        return new PdfSettings(
            22f,                     // storyHeaderTitleSize
            10f,                     // storyHeaderSubtitleSize
            14f,                     // textTitleSize
            12f,                     // textDescriptionSize
            9f,                      // imageCaptionSize
            13f,                     // commentTopLevelSize
            11f,                     // commentReplySize
            "gold",                  // passepartoutStyle
            null,                    // pdfPassword
            null,                    // coverFrontBackground
            null,                    // coverBackBackground
            null,                    // tocBackground
            true,                    // tocEnabled
            "Inhaltsverzeichnis",    // tocTitle
            24f,                     // tocTitleSize
            13f,                     // tocEntrySize
            true,                    // tocShowPageNumbers
            1                        // tocColumns
        );
    }
}
