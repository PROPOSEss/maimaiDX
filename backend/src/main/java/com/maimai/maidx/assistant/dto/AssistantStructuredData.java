package com.maimai.maidx.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AssistantStructuredData {

    private List<AssistantQueryResponse.ScoreItem> scores = new ArrayList<>();

    private List<AssistantQueryResponse.RecommendationItem> recommendations = new ArrayList<>();

    private List<AssistantQueryResponse.TrainingSuggestionItem> suggestions = new ArrayList<>();

    private List<AssistantQueryResponse.ScoreItem> evidence = new ArrayList<>();

    private List<AssistantQueryResponse.SongItem> songs = new ArrayList<>();
}
