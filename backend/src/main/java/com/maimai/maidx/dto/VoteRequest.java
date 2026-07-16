package com.maimai.maidx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 投票请求
 */
@Data
public class VoteRequest {

    @NotNull(message = "谱面ID不能为空")
    private Long difficultyId;

    @Size(min = 1, max = 3, message = "标签数量需在1-3个之间")
    private List<@NotBlank(message = "标签名不能为空") String> tagNames;
}
