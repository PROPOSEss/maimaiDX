package com.maimai.maidx.service;

import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.ImportTaskResponse;

public interface ScoreImportTaskService {

    ImportTaskResponse submit(Long userId, AsyncScoreImportRequest request);

    ImportTaskResponse getTask(Long userId, Long taskId);

    ImportTaskResponse getTaskByRequest(Long userId, String requestId);

    ImportTaskResponse retrySendFailedTask(Long userId, Long taskId);
}
