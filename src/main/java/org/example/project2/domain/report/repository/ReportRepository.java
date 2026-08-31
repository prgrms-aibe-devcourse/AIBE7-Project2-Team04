package org.example.project2.domain.report.repository;

import org.example.project2.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.reportedUser JOIN FETCH r.match ORDER BY r.id DESC")
    List<Report> findAllWithFetchedUsers();

    boolean existsByReporter_IdAndReportedUser_IdAndMatch_Id(UUID reporterId, UUID reportedUserId, Long matchId);
}