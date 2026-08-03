package com.maimai.maidx.assistant.controller;

import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.service.AssistantService;
import com.maimai.maidx.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI成绩助手", description = "自然语言成绩查询与规则推荐入口")
@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @Operation(summary = "查询AI成绩助手")
    @PostMapping("/query")
    public Result<AssistantQueryResponse> query(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody AssistantQueryRequest request) {
        return Result.success(assistantService.query(userId, request));
    }
}
