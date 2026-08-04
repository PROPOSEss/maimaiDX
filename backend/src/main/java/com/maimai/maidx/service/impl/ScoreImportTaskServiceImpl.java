package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.ImportTaskResponse;
import com.maimai.maidx.entity.ImportTask;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.enums.ImportTaskStatus;
import com.maimai.maidx.mq.TaskCreatedEvent;
import com.maimai.maidx.mq.TaskRetryEvent;
import com.maimai.maidx.repository.ImportTaskRepository;
import com.maimai.maidx.repository.UserRepository;
import com.maimai.maidx.service.ScoreImportTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScoreImportTaskServiceImpl implements ScoreImportTaskService {

    private static final int MAX_RECORDS = 200;

    private final ImportTaskRepository importTaskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ImportTaskResponse submit(Long userId, AsyncScoreImportRequest request) {
        validate(userId, request);
        ImportTask existing = findByUserAndRequest(userId, request.getRequestId());
        if (existing != null) {
            return ImportTaskResponse.from(existing);
        }

        ImportTask task = new ImportTask();
        task.setUserId(userId);
        task.setRequestId(request.getRequestId());
        task.setStatus(ImportTaskStatus.PENDING.name());
        task.setRequestPayload(toPayload(request));
        task.setAttemptCount(0);
        try {
            importTaskRepository.insert(task);
        } catch (DuplicateKeyException e) {
            ImportTask duplicated = findByUserAndRequest(userId, request.getRequestId());
            if (duplicated != null) {
                return ImportTaskResponse.from(duplicated);
            }
            throw e;
        }
        eventPublisher.publishEvent(new TaskCreatedEvent(task.getId()));
        return ImportTaskResponse.from(task);
    }

    @Override
    public ImportTaskResponse getTask(Long userId, Long taskId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须大于 0");
        }
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("taskId 必须大于 0");
        }
        ImportTask task = importTaskRepository.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("导入任务不存在: " + taskId);
        }
        if (!userId.equals(task.getUserId())) {
            throw new IllegalArgumentException("导入任务不存在: " + taskId);
        }
        return ImportTaskResponse.from(task);
    }

    @Override
    public ImportTaskResponse getTaskByRequest(Long userId, String requestId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须大于 0");
        }
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        ImportTask task = findByUserAndRequest(userId, requestId);
        if (task == null) {
            throw new IllegalArgumentException("导入任务不存在: " + requestId);
        }
        return ImportTaskResponse.from(task);
    }

    @Override
    @Transactional
    public ImportTaskResponse retrySendFailedTask(Long userId, Long taskId) {
        validateUserExists(userId);
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("taskId 必须大于 0");
        }

        int updated = importTaskRepository.update(null, new UpdateWrapper<ImportTask>()
                .eq("id", taskId)
                .eq("user_id", userId)
                .eq("status", ImportTaskStatus.SEND_FAILED.name())
                .set("status", ImportTaskStatus.PENDING.name())
                .set("error_message", null)
                .set("finished_at", null)
                .setSql("updated_at = NOW()"));

        if (updated != 1) {
            ImportTask current = importTaskRepository.selectById(taskId);
            if (current == null || !userId.equals(current.getUserId())) {
                throw new IllegalArgumentException("导入任务不存在: " + taskId);
            }
            throw new IllegalArgumentException("只有SEND_FAILED状态的导入任务允许重试，当前状态: " + current.getStatus());
        }

        ImportTask task = importTaskRepository.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new IllegalArgumentException("导入任务不存在: " + taskId);
        }
        eventPublisher.publishEvent(new TaskRetryEvent(taskId));
        return ImportTaskResponse.from(task);
    }

    private void validate(Long userId, AsyncScoreImportRequest request) {
        validateUserExists(userId);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getRequestId())) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        if (request.getRequestId().length() > 64) {
            throw new IllegalArgumentException("requestId长度不能超过64");
        }
        if (request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new IllegalArgumentException("records不能为空");
        }
        if (request.getRecords().size() > MAX_RECORDS) {
            throw new IllegalArgumentException("records最多导入200条");
        }
    }

    private void validateUserExists(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须大于 0");
        }
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
    }

    private ImportTask findByUserAndRequest(Long userId, String requestId) {
        return importTaskRepository.selectOne(new LambdaQueryWrapper<ImportTask>()
                .eq(ImportTask::getUserId, userId)
                .eq(ImportTask::getRequestId, requestId)
                .last("LIMIT 1"));
    }

    private String toPayload(AsyncScoreImportRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("导入请求无法序列化");
        }
    }
}
