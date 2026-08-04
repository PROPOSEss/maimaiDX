package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.entity.ImportTask;
import com.maimai.maidx.enums.ImportTaskStatus;
import com.maimai.maidx.mq.AsyncImportBusinessException;
import com.maimai.maidx.mq.AsyncImportSystemException;
import com.maimai.maidx.repository.ImportTaskRepository;
import com.maimai.maidx.service.AsyncImportExecutionService;
import com.maimai.maidx.service.MvpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AsyncImportExecutionServiceImpl implements AsyncImportExecutionService {

    private final ImportTaskRepository importTaskRepository;
    private final MvpService mvpService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void execute(Long taskId) {
        ImportTask task = importTaskRepository.selectById(taskId);
        if (task == null) {
            return;
        }
        if (ImportTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            return;
        }
        if (!claimTask(task)) {
            return;
        }

        AsyncScoreImportRequest request;
        try {
            request = objectMapper.readValue(task.getRequestPayload(), AsyncScoreImportRequest.class);
        } catch (JsonProcessingException e) {
            throw new AsyncImportBusinessException("payload无法反序列化");
        }

        try {
            MvpDtos.ImportResult result = mvpService.importScores(task.getUserId(),
                    request.toScoreImportRequest(),
                    task.getRequestId());
            ImportTask success = new ImportTask();
            success.setId(taskId);
            success.setStatus(ImportTaskStatus.SUCCESS.name());
            success.setSnapshotId(result.getSnapshotId());
            success.setErrorMessage(null);
            success.setFinishedAt(LocalDateTime.now());
            importTaskRepository.updateById(success);
        } catch (IllegalArgumentException e) {
            throw new AsyncImportBusinessException(e.getMessage());
        } catch (RuntimeException e) {
            throw new AsyncImportSystemException("异步导入系统异常", e);
        }
    }

    private boolean claimTask(ImportTask task) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(15);
        ImportTask update = new ImportTask();
        update.setStatus(ImportTaskStatus.PROCESSING.name());
        update.setProcessingStartedAt(now);

        UpdateWrapper<ImportTask> wrapper = new UpdateWrapper<ImportTask>()
                .eq("id", task.getId())
                .and(w -> w.in("status",
                                ImportTaskStatus.PENDING.name(),
                                ImportTaskStatus.FAILED.name(),
                                ImportTaskStatus.SEND_FAILED.name())
                        .or(x -> x.eq("status", ImportTaskStatus.PROCESSING.name())
                                .lt("processing_started_at", staleBefore)));
        wrapper.setSql("attempt_count = attempt_count + 1");
        return importTaskRepository.update(update, wrapper) == 1;
    }
}
