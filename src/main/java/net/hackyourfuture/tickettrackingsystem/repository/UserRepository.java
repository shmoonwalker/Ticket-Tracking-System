package net.hackyourfuture.tickettrackingsystem.repository;

import lombok.RequiredArgsConstructor;
import net.hackyourfuture.tickettrackingsystem.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<User> userRowMapper = (rs, rowNum) ->
            new User(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email")
            );

    public List<User> findAll() {
        String sql = """
                SELECT id, name, email
                FROM users
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, userRowMapper);
    }

    public Optional<User> findById(Long id) {
        String sql = """
                SELECT id, name, email
                FROM users
                WHERE id = ?
                """;

        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.of(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = """
                SELECT id, name, email
                FROM users
                WHERE email = ?
                """;

        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, email);
            return Optional.of(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public User create(User user) {
        String sql = """
                INSERT INTO users (name, email)
                VALUES (?, ?)
                RETURNING id, name, email
                """;

        return jdbcTemplate.queryForObject(
                sql,
                userRowMapper,
                user.name(),
                user.email()
        );
    }

    public User update(User user) {
        String sql = """
                UPDATE users
                SET name = ?, email = ?
                WHERE id = ?
                RETURNING id, name, email
                """;

        return jdbcTemplate.queryForObject(
                sql,
                userRowMapper,
                user.name(),
                user.email(),
                user.id()
        );
    }

    public boolean deleteById(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";

        int rowsAffected = jdbcTemplate.update(sql, id);

        return rowsAffected > 0;
    }
}