package org.example.project2.domain.report.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatMessageRepository;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.report.dto.CreateReportRequest;
import org.example.project2.domain.report.dto.ReportResponse;
import org.example.project2.domain.report.entity.Report;
import org.example.project2.domain.report.entity.ReportStatus;
import org.example.project2.domain.report.repository.ReportRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public void report(UUID reporterId, CreateReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Match match = matchRepository.findById(request.matchId())
                .orElseThrow(() -> new IllegalArgumentException("신고할 매칭 내역을 찾을 수 없습니다."));

        // 매칭 참여자 검증 및 피신고자(상대방) 추출
        User reportedUser;
        if (match.getRequest1().getUser().getId().equals(reporterId)) {
            reportedUser = match.getRequest2().getUser();
        } else if (match.getRequest2().getUser().getId().equals(reporterId)) {
            reportedUser = match.getRequest1().getUser();
        } else {
            throw new IllegalArgumentException("해당 매칭의 참여자만 신고할 수 있습니다.");
        }

        // 중복 신고 방지
        if (reportRepository.existsByReporter_IdAndReportedUser_IdAndMatch_Id(reporterId, reportedUser.getId(), request.matchId())) {
            throw new IllegalArgumentException("동일한 매칭에 대해 이미 상대방을 신고했습니다.");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .match(match)
                .category(request.category())
                .reason(request.reason())
                .build();

        reportRepository.save(report);
    }

    public List<ReportResponse> getAllReports() {
        return reportRepository.findAllWithFetchedUsersByStatus(ReportStatus.PENDING).stream()
                .map(report -> new ReportResponse(
                        report.getId(),
                        report.getReporter().getId(),
                        report.getReporter().getNickname(),
                        report.getReportedUser().getId(),
                        report.getReportedUser().getNickname(),
                        report.getMatch().getId(),
                        report.getCategory(),
                        report.getCategory().getDescription(),
                        report.getReason(),
                        report.getCreatedAt()
                ))
                .toList();
    }

    public List<ChatMessageDTO> getChatHistory(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 내역을 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomRepository.findByMatchId(report.getMatch().getId())
                .orElseThrow(() -> new IllegalArgumentException("연결된 채팅방이 존재하지 않습니다."));

        return chatMessageRepository.findByChatRoom_IdOrderByIdAsc(chatRoom.getId()).stream()
                .map(cm -> new ChatMessageDTO(
                        chatRoom.getId(),
                        cm.getSender().getId(),
                        cm.getContent()
                ))
                .toList();
    }

    @Transactional
    public void banUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.ban();
        userRepository.save(user);
    }

    @Transactional
    public void warnUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.warn();
        userRepository.save(user);
    }

    @Transactional
    public void dismissReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 내역을 찾을 수 없습니다."));
        report.dismiss();
    }

    @Transactional
    public void handleReport(Long reportId, String action) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 내역을 찾을 수 없습니다."));

        User reportedUser = report.getReportedUser();
        if ("warn".equalsIgnoreCase(action)) {
            reportedUser.warn();
        } else if ("ban".equalsIgnoreCase(action)) {
            reportedUser.ban();
        } else {
            throw new IllegalArgumentException("신고 처리 유형은 warn 또는 ban만 사용할 수 있습니다.");
        }

        report.markActioned();
    }
}
