package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.maimai.maidx.entity.ImportTask;
import com.maimai.maidx.enums.ImportTaskStatus;
import com.maimai.maidx.repository.ImportTaskRepository;
import com.maimai.maidx.service.ImportTaskStatusService;
import com.maimai.maidx.utils.SensitiveDataSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ImportTaskStatusServiceImpl implements ImportTaskStatusService {

    private final ImportTaskRepository importTaskRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSendFailed(Long taskId, String reason) {
        ImportTask task = importTaskRepository.selectById(taskId);
        if (task == null || ImportTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            return;
        }
        ImportTask update = new ImportTask();
        update.setStatus(ImportTaskStatus.SEND_FAILED.name());
        update.setErrorMessage(SensitiveDataSanitizer.sanitize(reason));
        importTaskRepository.update(update, new LambdaUpdateWrapper<ImportTask>()
                .eq(ImportTask::getId, taskId)
                .ne(ImportTask::getStatus, ImportTaskStatus.SUCCESS.name()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long taskId, String reason) {
        ImportTask task = importTaskRepository.selectById(taskId);
        if (task == null || ImportTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            return;
        }
        ImportTask update = new ImportTask();
        update.setStatus(ImportTaskStatus.FAILED.name());
        update.setErrorMessage(SensitiveDataSanitizer.sanitize(reason));
        update.setFinishedAt(LocalDateTime.now());
        update.setAttemptCount((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
        importTaskRepository.update(update, new LambdaUpdateWrapper<ImportTask>()
                .eq(ImportTask::getId, taskId)
                .ne(ImportTask::getStatus, ImportTaskStatus.SUCCESS.name()));
    }
}
