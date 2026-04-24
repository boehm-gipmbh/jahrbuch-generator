package de.jamsintown.dtos;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FotoboxSetupResponse(long groupId, String groupName, String token) {}
