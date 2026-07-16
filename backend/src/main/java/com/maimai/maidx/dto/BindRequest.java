package com.maimai.maidx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MAID绑定请求
 */
@Data
public class BindRequest {

    @NotBlank(message = "MAID不能为空")
    private String maId;
}
