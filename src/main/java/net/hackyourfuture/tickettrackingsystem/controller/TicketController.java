package net.hackyourfuture.tickettrackingsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.hackyourfuture.tickettrackingsystem.dto.request.CreateTicketRequest;
import net.hackyourfuture.tickettrackingsystem.dto.request.UpdateTicketRequest;
import net.hackyourfuture.tickettrackingsystem.dto.response.TicketResponse;
import net.hackyourfuture.tickettrackingsystem.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        TicketResponse response = ticketService.createTicket(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<TicketResponse> getAllTickets() {
        return ticketService.getAllTickets();
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id) {
        return ticketService.getTicketById(id);
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request
    ) {
        return ticketService.updateTicket(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ticketId}/assignees/{userId}")
    public ResponseEntity<Void> assignUserToTicket(
            @PathVariable Long ticketId,
            @PathVariable Long userId
    ) {
        ticketService.assignUserToTicket(ticketId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{ticketId}/assignees/{userId}")
    public ResponseEntity<Void> unassignUserFromTicket(
            @PathVariable Long ticketId,
            @PathVariable Long userId
    ) {
        ticketService.unassignUserFromTicket(ticketId, userId);

        return ResponseEntity.noContent().build();
    }
}