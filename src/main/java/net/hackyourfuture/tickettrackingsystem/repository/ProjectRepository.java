package net.hackyourfuture.tickettrackingsystem.repository;

import lombok.RequiredArgsConstructor;
import net.hackyourfuture.tickettrackingsystem.dto.response.ProjectSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<ProjectSummaryResponse> findAllProjectSummaries(){
        String sql = """
                SELECT
                p.id,
                p.name,
                COUNT(*) FILTER (WHERE t.status = 'OPEN') AS open_tickets,
                COUNT(*) FILTER (WHERE t.status = 'IN_PROGRESS') AS in_progress_tickets,
                COUNT(*) FILTER (WHERE t.status = 'CLOSED') AS closed_tickets
                FROM projects p
                LEFT JOIN tickets t ON p.id = t.project_id
                GROUP BY p.id, p.name
                ORDER BY p.name
                """;
        return jdbcTemplate.query(sql, projectSummaryResponseRowMapper);
    }

    private final RowMapper<ProjectSummaryResponse> projectSummaryResponseRowMapper =
            (rs, rowNum) -> new ProjectSummaryResponse(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getInt("open_tickets"),
                    rs.getInt("in_progress_tickets"),
                    rs.getInt("closed_tickets")
            );

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM projects WHERE id = ?";

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);

        return count != null && count > 0;
    }
}
