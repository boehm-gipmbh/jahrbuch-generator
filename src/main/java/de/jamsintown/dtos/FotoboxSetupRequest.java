package de.jamsintown.dtos;

import java.time.LocalDate;

public record FotoboxSetupRequest(String groupName, LocalDate validFrom, LocalDate validTo, String recipientEmail) {}
