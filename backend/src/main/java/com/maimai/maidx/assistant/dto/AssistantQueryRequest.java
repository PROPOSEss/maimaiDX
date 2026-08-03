package com.maimai.maidx.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssistantQueryRequest {

    @NotBlank(message = "message 不能为空")
    private String message;
}
