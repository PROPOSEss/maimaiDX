package com.maimai.maidx.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.entity.ImportTask;
import com.maimai.maidx.enums.ImportTaskStatus;
import com.maimai.maidx.mq.AsyncImportBusinessException;
import com.maimai.maidx.repository.ImportTaskRepository;
import com.maimai.maidx.service.impl.AsyncImportExecutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncImportExecutionServiceImplTest {

    private AsyncImportExecutionServiceImpl service;

    @Mock
    private ImportTaskRepository importTaskRepository;

    @Mock
    private MvpService mvpService;

    @BeforeEach
    void setUp() {
        service = new AsyncImportExecutionServiceImpl(importTaskRepository, mvpService, new ObjectMapper());
    }

    @Test
    void successfulExecutionImportsScoresAndMarksTaskSuccess() throws Exception {
        ImportTask task = task(1L, ImportTaskStatus.PENDING, payload("req-1"));
        when(importTaskRepository.selectById(1L)).thenReturn(task);
        when(importTaskRepository.update(any(ImportTask.class), any(UpdateWrapper.class))).thenReturn(1);
        MvpDtos.ImportResult result = new MvpDtos.ImportResult();
        result.setSnapshotId(99L);
        when(mvpService.importScores(any(), any(), any())).thenReturn(result);

        service.execute(1L);

        verify(mvpService).importScores(999L, taskRequest(), "req-1");
        ArgumentCaptor<ImportTask> captor = ArgumentCaptor.forClass(ImportTask.class);
        verify(importTaskRepository).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ImportTaskStatus.SUCCESS.name());
        assertThat(captor.getValue().getSnapshotId()).isEqualTo(99L);
    }

    @Test
    void repeatedSuccessConsumptionDoesNotImportAgain() throws Exception {
        ImportTask task = task(1L, ImportTaskStatus.SUCCESS, payload("req-1"));
        when(importTaskRepository.selectById(1L)).thenReturn(task);

        service.execute(1L);

        verify(mvpService, never()).importScores(any(), any(), any());
    }

    @Test
    void unclaimedProcessingTaskIsSkippedWithoutImport() throws Exception {
        ImportTask task = task(1L, ImportTaskStatus.PROCESSING, payload("req-1"));
        when(importTaskRepository.selectById(1L)).thenReturn(task);
        when(importTaskRepository.update(any(ImportTask.class), any(UpdateWrapper.class))).thenReturn(0);

        service.execute(1L);

        verify(mvpService, never()).importScores(any(), any(), any());
    }

    @Test
    void onlyOneConsumerImportsWhenAtomicClaimSucceedsOnce() throws Exception {
        ImportTask task = task(1L, ImportTaskStatus.PENDING, payload("req-1"));
        when(importTaskRepository.selectById(1L)).thenReturn(task);
        when(importTaskRepository.update(any(ImportTask.class), any(UpdateWrapper.class)))
                .thenReturn(1)
                .thenReturn(0);
        MvpDtos.ImportResult result = new MvpDtos.ImportResult();
        result.setSnapshotId(99L);
        when(mvpService.importScores(any(), any(), any())).thenReturn(result);

        service.execute(1L);
        service.execute(1L);

        verify(mvpService, times(1)).importScores(any(), any(), any());
    }

    @Test
    void businessErrorRollsBackByThrowingBusinessException() throws Exception {
        ImportTask task = task(1L, ImportTaskStatus.PENDING, payload("req-1"));
        when(importTaskRepository.selectById(1L)).thenReturn(task);
        when(importTaskRepository.update(any(ImportTask.class), any(UpdateWrapper.class))).thenReturn(1);
        when(mvpService.importScores(any(), any(), any())).thenThrow(new IllegalArgumentException("歌曲不存在"));

        assertThatThrownBy(() -> service.execute(1L))
                .isInstanceOf(AsyncImportBusinessException.class)
                .hasMessage("歌曲不存在");
    }

    @Test
    void invalidPayloadIsBusinessFailure() {
        ImportTask task = task(1L, ImportTaskStatus.PENDING, "{bad json");
        when(importTaskRepository.selectById(1L)).thenReturn(task);
        when(importTaskRepository.update(any(ImportTask.class), any(UpdateWrapper.class))).thenReturn(1);

        assertThatThrownBy(() -> service.execute(1L))
                .isInstanceOf(AsyncImportBusinessException.class)
                .hasMessage("payload无法反序列化");
    }

    private String payload(String requestId) throws Exception {
        return new ObjectMapper().writeValueAsString(asyncRequest(requestId));
    }

    private AsyncScoreImportRequest asyncRequest(String requestId) {
        AsyncScoreImportRequest request = new AsyncScoreImportRequest();
        request.setRequestId(requestId);
        request.setRecords(List.of(scoreItem()));
        return request;
    }

    private MvpDtos.ScoreImportRequest taskRequest() {
        return asyncRequest("req-1").toScoreImportRequest();
    }

    private MvpDtos.ScoreImportItem scoreItem() {
        MvpDtos.ScoreImportItem item = new MvpDtos.ScoreImportItem();
        item.setSongId("mvp001");
        item.setDifficulty(3);
        item.setAchievement(new BigDecimal("99.50"));
        return item;
    }

    private ImportTask task(Long id, ImportTaskStatus status, String payload) {
        ImportTask task = new ImportTask();
        task.setId(id);
        task.setUserId(999L);
        task.setRequestId("req-1");
        task.setStatus(status.name());
        task.setRequestPayload(payload);
        task.setAttemptCount(0);
        return task;
    }
}
