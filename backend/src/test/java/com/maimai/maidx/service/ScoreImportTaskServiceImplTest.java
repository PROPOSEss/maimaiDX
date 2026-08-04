package com.maimai.maidx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.ImportTaskResponse;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.entity.ImportTask;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.enums.ImportTaskStatus;
import com.maimai.maidx.mq.TaskCreatedEvent;
import com.maimai.maidx.repository.ImportTaskRepository;
import com.maimai.maidx.repository.UserRepository;
import com.maimai.maidx.service.impl.ScoreImportTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
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
}
