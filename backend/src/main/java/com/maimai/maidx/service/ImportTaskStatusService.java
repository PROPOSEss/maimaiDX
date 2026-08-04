package com.maimai.maidx.service;

public interface ImportTaskStatusService {

    void markSendFailed(Long taskId, String reason);

    void markFailed(Long taskId, String reason);
}
