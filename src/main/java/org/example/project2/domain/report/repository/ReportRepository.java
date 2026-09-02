package org.example.project2.domain.report.repository;

import org.example.project2.domain.report.entity.Report;
import org.example.project2.domain.report.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.reportedUser JOIN FETCH r.match WHERE r.status = :status ORDER BY r.id DESC")
    List<Report> findAllWithFetchedUsersByStatus(@Param("status") ReportStatus status);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.reportedUser JOIN FETCH r.match ORDER BY r.id DESC")
    List<Report> findAllWithFetchedUsers();

    boolean existsByReporter_IdAndReportedUser_IdAndMatch_Id(UUID reporterId, UUID reportedUserId, Long matchId);

    @Query("SELECT r.match.id FROM Report r WHERE r.reporter.id = :reporterId AND r.match.id IN :matchIds")
    Set<Long> findReportedMatchIdsByMatchIdInAndReporterId(
            @Param("matchIds") List<Long> matchIds,
            @Param("reporterId") UUID reporterId
    );
}
