package net.hackyourfuture.tickettrackingsystem.dto.response;

import net.hackyourfuture.tickettrackingsystem.model.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public record TicketResponse(
        Long id,
        String title,
        String description,
        Long projectId,
        TicketStatus status,
        List<Long> assignedUserIds,
        LocalDateTime creationDate,
        LocalDateTime updateDate
) {
}