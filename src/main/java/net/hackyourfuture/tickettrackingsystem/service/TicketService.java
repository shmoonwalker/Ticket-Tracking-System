package net.hackyourfuture.tickettrackingsystem.service;

import lombok.RequiredArgsConstructor;
import net.hackyourfuture.tickettrackingsystem.dto.request.CreateTicketRequest;
import net.hackyourfuture.tickettrackingsystem.dto.request.UpdateTicketRequest;
import net.hackyourfuture.tickettrackingsystem.dto.response.TicketResponse;
import net.hackyourfuture.tickettrackingsystem.exception.ResourceNotFoundException;
import net.hackyourfuture.tickettrackingsystem.model.Ticket;
import net.hackyourfuture.tickettrackingsystem.repository.ProjectRepository;
import net.hackyourfuture.tickettrackingsystem.repository.TicketRepository;
import net.hackyourfuture.tickettrackingsystem.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TicketResponse createTicket(CreateTicketRequest request) {
        if (!projectRepository.existsById(request.projectId())) {
            throw new ResourceNotFoundException(
                    "Project with id " + request.projectId() + " not found");
        }

        Ticket ticket = new Ticket(
                null,
                request.title(),
                request.description(),
                request.projectId(),
                request.status(),
                null,
                null
        );

        Ticket createdTicket = ticketRepository.create(ticket);

        return toTicketResponse(createdTicket);
    }

    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAll()
                .stream()
                .map(this::toTicketResponse)
                .toList();
    }

    public TicketResponse getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Ticket with id " + id + " not found"));

        return toTicketResponse(ticket);
    }

    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket existingTicket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Ticket with id " + id + " not found"));

        if (!projectRepository.existsById(request.projectId())) {
            throw new ResourceNotFoundException(
                    "Project with id " + request.projectId() + " not found");
        }

        Ticket updatedTicket = new Ticket(
                existingTicket.id(),
                request.title(),
                request.description(),
                request.projectId(),
                request.status(),
                existingTicket.creationDate(),
                existingTicket.updateDate()
        );

        Ticket savedTicket = ticketRepository.update(updatedTicket);

        return toTicketResponse(savedTicket);
    }

    public void deleteTicket(Long id) {
        boolean deleted = ticketRepository.deleteById(id);

        if (!deleted) {
            throw new ResourceNotFoundException(
                    "Ticket with id " + id + " not found");
        }
    }

    public void assignUserToTicket(Long ticketId, Long userId) {
        if (ticketRepository.findById(ticketId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Ticket with id " + ticketId + " not found");
        }

        if (userRepository.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "User with id " + userId + " not found");
        }

        if (ticketRepository.assignmentExists(ticketId, userId)) {
            throw new IllegalArgumentException(
                    "User " + userId + " is already assigned to ticket " + ticketId);
        }

        ticketRepository.assignUser(ticketId, userId);
    }

    public void unassignUserFromTicket(Long ticketId, Long userId) {
        if (ticketRepository.findById(ticketId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Ticket with id " + ticketId + " not found");
        }

        if (userRepository.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "User with id " + userId + " not found");
        }

        boolean removed = ticketRepository.unassignUser(ticketId, userId);

        if (!removed) {
            throw new ResourceNotFoundException(
                    "User " + userId + " is not assigned to ticket " + ticketId);
        }
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.id(),
                ticket.title(),
                ticket.description(),
                ticket.projectId(),
                ticket.status(),
                ticketRepository.findAssignedUserIds(ticket.id()),
                ticket.creationDate(),
                ticket.updateDate()
        );
    }
}