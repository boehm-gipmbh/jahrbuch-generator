package de.jamsintown.dtos;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FotoboxConfigDTO(long groupId, String groupName, String imageFormat) {}
