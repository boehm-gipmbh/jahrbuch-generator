package de.jamsintown.pdf;

import java.util.List;

public record PdfOptions(
    List<Long> storyIds,
    boolean includePendingBilder,
    boolean includePendingTexte,
    boolean coverPage,
    String coverTitle,
    boolean pageNumbers,
    String passepartoutStyle,
    String layoutStyle  // "scrapbook" (default) or "classic"
) {}
