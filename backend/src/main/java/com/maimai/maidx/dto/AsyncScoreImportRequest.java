package com.maimai.maidx.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AsyncScoreImportRequest {

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64")
    private String requestId;

    private String source = "manual_json";

    private Integer rating = 0;

    @Valid
    @NotEmpty(message = "records不能为空")
    @Size(max = 200, message = "records最多导入200条")
    private List<MvpDtos.ScoreImportItem> records = new ArrayList<>();

    public MvpDtos.ScoreImportRequest toScoreImportRequest() {
        MvpDtos.ScoreImportRequest request = new MvpDtos.ScoreImportRequest();
        request.setSource(source);
        request.setRating(rating);
        request.setRecords(records);
        return request;
    }
}
