package net.hackyourfuture.tickettrackingsystem.model;

import java.time.LocalDateTime;

public record Ticket(
        Long id,
        String title,
        String description,
        Long projectId,
        TicketStatus status,
        LocalDateTime creationDate,
        LocalDateTime updateDate
) {
}