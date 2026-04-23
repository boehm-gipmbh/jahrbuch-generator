package de.jamsintown.dtos;

import java.util.List;

public record FotoboxStateDTO(boolean stationEnabled, boolean cameraConnected, String cameraModel, List<String> todaysBilderPfade) {}
