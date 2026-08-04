package com.maimai.maidx.controller;

import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.maimai.maidx.dto.ImportTaskResponse;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.service.ScoreImportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Async Score Import", description = "RabbitMQ based asynchronous score import")
@RestController
@RequestMapping("/player/import")
@RequiredArgsConstructor
public class ScoreImportTaskController {

    private final ScoreImportTaskService scoreImportTaskService;

    @Operation(summary = "Submit asynchronous score import task")
    @PostMapping("/async")
    public Result<ImportTaskResponse> submit(@RequestParam Long userId,
                                             @Valid @RequestBody AsyncScoreImportRequest request) {
        return Result.success(scoreImportTaskService.submit(userId, request));
    }

    @Operation(summary = "Get asynchronous score import task by task id")
    @GetMapping("/tasks/{taskId}")
    public Result<ImportTaskResponse> getTask(@PathVariable Long taskId,
                                              @RequestParam Long userId) {
        return Result.success(scoreImportTaskService.getTask(userId, taskId));
    }

    @Operation(summary = "Get asynchronous score import task by request id")
    @GetMapping("/tasks/by-request/{requestId}")
    public Result<ImportTaskResponse> getTaskByRequest(@PathVariable String requestId,
                                                       @RequestParam Long userId) {
        return Result.success(scoreImportTaskService.getTaskByRequest(userId, requestId));
    }

    @Operation(summary = "Retry a SEND_FAILED asynchronous score import task")
    @PostMapping("/tasks/{taskId}/retry")
    public Result<ImportTaskResponse> retry(@PathVariable Long taskId,
                                            @RequestParam Long userId) {
        return Result.success(scoreImportTaskService.retrySendFailedTask(userId, taskId));
    }
}
