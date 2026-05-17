package de.jamsintown.pdf;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record PdfOptions(
    List<Long> storyIds,
    boolean includePendingBilder,
    boolean includePendingTexte,
    boolean coverPage,
    String coverTitle,
    boolean pageNumbers,
    String passepartoutStyle
) {}
