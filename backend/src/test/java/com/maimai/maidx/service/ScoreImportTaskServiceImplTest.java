package com.maimai.maidx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.ImportTaskResponse;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.entity.ImportTask;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.enums.ImportTaskStatus;
import com.maimai.maidx.mq.TaskCreatedEvent;
import com.maimai.maidx.mq.TaskRetryEvent;
import com.maimai.maidx.repository.ImportTaskRepository;
import com.maimai.maidx.repository.UserRepository;
import com.maimai.maidx.service.impl.ScoreImportTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreImportTaskServiceImplTest {

    private ScoreImportTaskServiceImpl service;

    @Mock
    private ImportTaskRepository importTaskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        service = new ScoreImportTaskServiceImpl(importTaskRepository, userRepository, new ObjectMapper(), eventPublisher);
    }

    @Test
    void duplicateSubmitReturnsExistingTaskWithoutPublishingEvent() {
        User user = new User();
        user.setId(999L);
        ImportTask existing = task(7L, 999L, "req-1", ImportTaskStatus.PENDING);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        ImportTaskResponse response = service.submit(999L, request("req-1"));

        assertThat(response.getTaskId()).isEqualTo(7L);
        verify(importTaskRepository, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void duplicateKeyDuringInsertReturnsExistingTask() {
        User user = new User();
        user.setId(999L);
        ImportTask existing = task(8L, 999L, "req-2", ImportTaskStatus.PENDING);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(existing);
        when(importTaskRepository.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        ImportTaskResponse response = service.submit(999L, request("req-2"));

        assertThat(response.getTaskId()).isEqualTo(8L);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void newTaskPublishesTaskCreatedEvent() {
        User user = new User();
        user.setId(999L);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(importTaskRepository.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(11L);
            return 1;
        });

        ImportTaskResponse response = service.submit(999L, request("req-3"));

        assertThat(response.getTaskId()).isEqualTo(11L);
        ArgumentCaptor<TaskCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().taskId()).isEqualTo(11L);
    }

    @Test
    void differentUsersCanUseSameRequestId() {
        User user = new User();
        user.setId(1000L);
        when(userRepository.selectById(1000L)).thenReturn(user);
        when(importTaskRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(importTaskRepository.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(12L);
            return 1;
        });

        ImportTaskResponse response = service.submit(1000L, request("same-request"));

        assertThat(response.getUserId()).isEqualTo(1000L);
        assertThat(response.getTaskId()).isEqualTo(12L);
    }

    @Test
    void rejectsInvalidRequestBeforeInsertAndMessagePublish() {
        User user = new User();
        user.setId(999L);
        when(userRepository.selectById(999L)).thenReturn(user);

        assertThatThrownBy(() -> service.submit(999L, request("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId不能为空");

        verify(importTaskRepository, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getTaskRejectsTaskOwnedByOtherUser() {
        ImportTask task = task(20L, 1000L, "req-owned-by-other", ImportTaskStatus.PENDING);
        when(importTaskRepository.selectById(20L)).thenReturn(task);

        assertThatThrownBy(() -> service.getTask(999L, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导入任务不存在: 20");
    }

    @Test
    void getTaskReturnsOnlyCurrentUsersTask() {
        ImportTask task = task(21L, 999L, "req-owned", ImportTaskStatus.SUCCESS);
        when(importTaskRepository.selectById(21L)).thenReturn(task);

        ImportTaskResponse response = service.getTask(999L, 21L);

        assertThat(response.getTaskId()).isEqualTo(21L);
        assertThat(response.getUserId()).isEqualTo(999L);
    }

    @Test
    void retrySendFailedTaskChangesToPendingAndPublishesRetryEvent() {
        User user = user(999L);
        ImportTask pending = task(30L, 999L, "req-retry", ImportTaskStatus.PENDING);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(importTaskRepository.selectById(30L)).thenReturn(pending);

        ImportTaskResponse response = service.retrySendFailedTask(999L, 30L);

        assertThat(response.getTaskId()).isEqualTo(30L);
        assertThat(response.getStatus()).isEqualTo(ImportTaskStatus.PENDING.name());
        ArgumentCaptor<TaskRetryEvent> eventCaptor = ArgumentCaptor.forClass(TaskRetryEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().taskId()).isEqualTo(30L);
    }

    @Test
    void retryRejectsOtherUsersTaskAsNotFound() {
        User user = user(999L);
        ImportTask otherUserTask = task(31L, 1000L, "req-other", ImportTaskStatus.SEND_FAILED);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        when(importTaskRepository.selectById(31L)).thenReturn(otherUserTask);

        assertThatThrownBy(() -> service.retrySendFailedTask(999L, 31L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导入任务不存在: 31");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(value = ImportTaskStatus.class, names = {"SUCCESS", "PROCESSING", "PENDING", "FAILED"})
    void retryRejectsNonSendFailedStatus(ImportTaskStatus status) {
        User user = user(999L);
        ImportTask task = task(32L, 999L, "req-status", status);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        when(importTaskRepository.selectById(32L)).thenReturn(task);

        assertThatThrownBy(() -> service.retrySendFailedTask(999L, 32L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有SEND_FAILED状态的导入任务允许重试，当前状态: " + status.name());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retryPublishesEventOnlyForOneSuccessfulConditionalUpdate() {
        User user = user(999L);
        ImportTask pending = task(33L, 999L, "req-race", ImportTaskStatus.PENDING);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.update(isNull(), any(UpdateWrapper.class)))
                .thenReturn(1)
                .thenReturn(0);
        when(importTaskRepository.selectById(33L)).thenReturn(pending);

        service.retrySendFailedTask(999L, 33L);
        assertThatThrownBy(() -> service.retrySendFailedTask(999L, 33L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有SEND_FAILED状态的导入任务允许重试，当前状态: PENDING");

        verify(eventPublisher, times(1)).publishEvent(any(TaskRetryEvent.class));
    }

    @Test
    void retryDoesNotPublishEventWhenTaskCannotBeReloadedAfterUpdate() {
        User user = user(999L);
        when(userRepository.selectById(999L)).thenReturn(user);
        when(importTaskRepository.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(importTaskRepository.selectById(34L)).thenReturn(null);

        assertThatThrownBy(() -> service.retrySendFailedTask(999L, 34L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导入任务不存在: 34");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retryRejectsMissingUserBeforeUpdatingTask() {
        when(userRepository.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.retrySendFailedTask(404L, 35L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在: 404");

        verify(importTaskRepository, never()).update(any(), any(UpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private AsyncScoreImportRequest request(String requestId) {
        MvpDtos.ScoreImportItem item = new MvpDtos.ScoreImportItem();
        item.setSongId("mvp001");
        item.setDifficulty(3);
        item.setAchievement(new BigDecimal("99.50"));
        AsyncScoreImportRequest request = new AsyncScoreImportRequest();
        request.setRequestId(requestId);
        request.setRecords(List.of(item));
        return request;
    }

    private ImportTask task(Long id, Long userId, String requestId, ImportTaskStatus status) {
        ImportTask task = new ImportTask();
        task.setId(id);
        task.setUserId(userId);
        task.setRequestId(requestId);
        task.setStatus(status.name());
        task.setAttemptCount(0);
        return task;
    }

    private User user(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
