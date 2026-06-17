package net.hackyourfuture.tickettrackingsystem.dto.response;

public record EmailNotificationResponse(
        String recipientEmail,
        boolean sent,
        String message
) {
}