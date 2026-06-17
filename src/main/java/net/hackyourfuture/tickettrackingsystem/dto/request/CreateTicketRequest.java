package net.hackyourfuture.tickettrackingsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.hackyourfuture.tickettrackingsystem.model.TicketStatus;

public record CreateTicketRequest(
        @NotBlank String title,
        String description,
        @NotNull Long projectId,
        @NotNull TicketStatus status
) {
}