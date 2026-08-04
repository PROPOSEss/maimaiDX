package com.maimai.maidx.dto;

import com.maimai.maidx.entity.ImportTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImportTaskResponse {

    private Long taskId;
    private String requestId;
    private Long userId;
    private String status;
    private Long snapshotId;
    private Integer attemptCount;
    private String errorMessage;
    private LocalDateTime processingStartedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ImportTaskResponse from(ImportTask task) {
        ImportTaskResponse response = new ImportTaskResponse();
        response.setTaskId(task.getId());
        response.setRequestId(task.getRequestId());
        response.setUserId(task.getUserId());
        response.setStatus(task.getStatus());
        response.setSnapshotId(task.getSnapshotId());
        response.setAttemptCount(task.getAttemptCount());
        response.setErrorMessage(task.getErrorMessage());
        response.setProcessingStartedAt(task.getProcessingStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        return response;
    }
}
