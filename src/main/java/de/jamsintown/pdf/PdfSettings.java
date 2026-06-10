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
    Boolean tocEnabled,
    String tocTitle,
    Float tocTitleSize,
    Float tocEntrySize,
    Boolean tocShowPageNumbers,
    Integer tocColumns,
    String coverTitlePosition,
    String coverTitleColor,
    String tocTitleColor
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
            1,                       // tocColumns
            "middle",                // coverTitlePosition
            "#000000",               // coverTitleColor
            "#000000"                // tocTitleColor
        );
    }

    public String coverTitlePositionOrDefault() { return coverTitlePosition != null ? coverTitlePosition : "middle"; }
    public String coverTitleColorOrDefault()    { return coverTitleColor    != null ? coverTitleColor    : "#000000"; }
    public String tocTitleColorOrDefault()      { return tocTitleColor      != null ? tocTitleColor      : "#000000"; }

    public boolean isTocEnabled()       { return tocEnabled == null || tocEnabled; }
    public String tocTitleOrDefault()   { return tocTitle != null && !tocTitle.isBlank() ? tocTitle : "Inhaltsverzeichnis"; }
    public float tocTitleSizeOrDefault(){ return tocTitleSize != null && tocTitleSize > 0 ? tocTitleSize : 24f; }
    public float tocEntrySizeOrDefault(){ return tocEntrySize != null && tocEntrySize > 0 ? tocEntrySize : 13f; }
    public boolean isTocShowPageNumbers(){ return tocShowPageNumbers == null || tocShowPageNumbers; }
    public int tocColumnsOrDefault()    { return tocColumns != null && tocColumns > 0 ? tocColumns : 1; }
}
