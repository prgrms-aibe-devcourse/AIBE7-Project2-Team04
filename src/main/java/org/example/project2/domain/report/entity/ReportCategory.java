package org.example.project2.domain.report.entity;

public enum ReportCategory {
    NO_SHOW("약속 불이행 및 노쇼"),
    ABUSE("욕설 및 비방, 부적절한 대화"),
    SPAM("광고 및 스팸 홍보"),
    MISINFORMATION("허위 사실 유포");

    private final String description;

    ReportCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}