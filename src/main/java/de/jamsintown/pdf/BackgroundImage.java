package de.jamsintown.pdf;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record BackgroundImage(
    Long bildId,
    float opacity,
    String tint,
    float offsetX,
    float offsetY,
    float zoom,
    String fillColor
) {
    public static BackgroundImage of(Long bildId) {
        return new BackgroundImage(bildId, 0.15f, null, 0f, 0f, 1f, null);
    }
}
