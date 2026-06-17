package net.hackyourfuture.tickettrackingsystem.email;

public record EmailSendResult(
        String recipientEmail,
        boolean sent,
        String message
) {
}