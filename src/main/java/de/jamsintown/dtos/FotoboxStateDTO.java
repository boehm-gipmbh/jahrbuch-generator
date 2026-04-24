package de.jamsintown.dtos;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record FotoboxStateDTO(boolean stationEnabled, boolean cameraConnected, String cameraModel, List<String> todaysBilderPfade) {}
