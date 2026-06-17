package net.hackyourfuture.tickettrackingsystem.repository;

import lombok.RequiredArgsConstructor;
import net.hackyourfuture.tickettrackingsystem.model.Ticket;
import net.hackyourfuture.tickettrackingsystem.model.TicketStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TicketRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Ticket> ticketRowMapper = (rs, rowNum) ->
            new Ticket(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getLong("project_id"),
                    TicketStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("creation_date").toLocalDateTime(),
                    rs.getTimestamp("update_date") != null
                            ? rs.getTimestamp("update_date").toLocalDateTime()
                            : null
            );

    public Ticket create(Ticket ticket) {
        String sql = """
                INSERT INTO tickets (title, description, project_id, status)
                VALUES (?, ?, ?, ?::ticket_status)
                RETURNING id, title, description, project_id, status, creation_date, update_date
                """;

        return jdbcTemplate.queryForObject(
                sql,
                ticketRowMapper,
                ticket.title(),
                ticket.description(),
                ticket.projectId(),
                ticket.status().name()
        );
    }

    public List<Ticket> findAll() {
        String sql = """
                SELECT id, title, description, project_id, status, creation_date, update_date
                FROM tickets
                ORDER BY creation_date DESC
                """;

        return jdbcTemplate.query(sql, ticketRowMapper);
    }

    public Optional<Ticket> findById(Long id) {
        String sql = """
                SELECT id, title, description, project_id, status, creation_date, update_date
                FROM tickets
                WHERE id = ?
                """;

        try {
            Ticket ticket = jdbcTemplate.queryForObject(sql, ticketRowMapper, id);
            return Optional.of(ticket);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Ticket update(Ticket ticket) {
        String sql = """
                UPDATE tickets
                SET title = ?,
                    description = ?,
                    project_id = ?,
                    status = ?::ticket_status,
                    update_date = CURRENT_TIMESTAMP
                WHERE id = ?
                RETURNING id, title, description, project_id, status, creation_date, update_date
                """;

        return jdbcTemplate.queryForObject(
                sql,
                ticketRowMapper,
                ticket.title(),
                ticket.description(),
                ticket.projectId(),
                ticket.status().name(),
                ticket.id()
        );
    }

    public boolean deleteById(Long id) {
        String sql = "DELETE FROM tickets WHERE id = ?";

        int rowsAffected = jdbcTemplate.update(sql, id);

        return rowsAffected > 0;
    }

    public void assignUser(Long ticketId, Long userId) {
        String sql = """
                INSERT INTO user_ticket (ticket_id, user_id)
                VALUES (?, ?)
                """;

        jdbcTemplate.update(sql, ticketId, userId);
    }

    public boolean unassignUser(Long ticketId, Long userId) {
        String sql = """
                DELETE FROM user_ticket
                WHERE ticket_id = ?
                  AND user_id = ?
                """;

        int rowsAffected = jdbcTemplate.update(sql, ticketId, userId);

        return rowsAffected > 0;
    }

    public boolean assignmentExists(Long ticketId, Long userId) {
        String sql = """
                SELECT COUNT(*)
                FROM user_ticket
                WHERE ticket_id = ?
                  AND user_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                ticketId,
                userId
        );

        return count != null && count > 0;
    }

    public List<Long> findAssignedUserIds(Long ticketId) {
        String sql = """
                SELECT user_id
                FROM user_ticket
                WHERE ticket_id = ?
                ORDER BY user_id
                """;

        return jdbcTemplate.queryForList(sql, Long.class, ticketId);
    }
}