package net.hackyourfuture.tickettrackingsystem.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketStatus {

    OPEN("open"),
    IN_PROGRESS("in progress"),
    CLOSED("closed");

    private final String value;

    TicketStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}