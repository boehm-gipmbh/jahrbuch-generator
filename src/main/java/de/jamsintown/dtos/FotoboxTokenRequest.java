package de.jamsintown.dtos;

import java.time.LocalDate;

public record FotoboxTokenRequest(LocalDate validFrom, LocalDate validTo, String recipientEmail) {}
