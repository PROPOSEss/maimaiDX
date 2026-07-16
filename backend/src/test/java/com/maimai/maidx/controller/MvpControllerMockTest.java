package com.maimai.maidx.controller;

import com.maimai.maidx.config.GlobalExceptionHandler;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.service.MvpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MvpControllerMockTest {

    private MockMvc mockMvc;

    @Mock
    private MvpService mvpService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MvpController(mvpService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void querySongsReturnsMvpSongList() throws Exception {
        MvpDtos.SongQueryItem song = new MvpDtos.SongQueryItem();
        song.setSongId("mvp001");
        song.setTitle("Demo Future Bass");
        when(mvpService.querySongs(any(), any(), any(), any(), any(), eq(1), eq(20))).thenReturn(List.of(song));

        mockMvc.perform(get("/songs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].songId").value("mvp001"));
    }

    @Test
    void importScoresCreatesSnapshot() throws Exception {
        MvpDtos.ImportResult result = new MvpDtos.ImportResult();
        result.setSnapshotId(7L);
        result.setImportedCount(1);
        when(mvpService.importScores(eq(999L), any())).thenReturn(result);

        mockMvc.perform(post("/player/import?userId=999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":13250,\"records\":[{\"songId\":\"mvp001\",\"difficulty\":3,\"achievement\":99.72}]}") )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotId").value(7));
    }

    @Test
    void getGrowthComparesTwoSnapshots() throws Exception {
        MvpDtos.GrowthResponse response = new MvpDtos.GrowthResponse();
        response.setUserId(999L);
        response.setFromSnapshotId(1L);
        response.setToSnapshotId(2L);
        response.setRatingDelta(120);
        response.setEdgeRaDelta(8);
        when(mvpService.getGrowth(999L, 1L, 2L)).thenReturn(response);

        mockMvc.perform(get("/player/999/growth?fromSnapshotId=1&toSnapshotId=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(999))
                .andExpect(jsonPath("$.data.fromSnapshotId").value(1))
                .andExpect(jsonPath("$.data.toSnapshotId").value(2))
                .andExpect(jsonPath("$.data.ratingDelta").value(120))
                .andExpect(jsonPath("$.data.edgeRaDelta").value(8));
    }

    @Test
    void getGrowthReturnsBadRequestWhenSnapshotMissing() throws Exception {
        when(mvpService.getGrowth(999L, 999L, 3L))
                .thenThrow(new IllegalArgumentException("未找到成绩快照，请先导入成绩 JSON"));

        mockMvc.perform(get("/player/999/growth?fromSnapshotId=999&toSnapshotId=3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("未找到成绩快照，请先导入成绩 JSON"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
