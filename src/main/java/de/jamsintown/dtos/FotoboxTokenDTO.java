package de.jamsintown.dtos;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FotoboxTokenDTO(String token, String groupName) {}
