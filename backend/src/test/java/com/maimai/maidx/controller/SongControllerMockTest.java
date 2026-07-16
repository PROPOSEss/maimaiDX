package com.maimai.maidx.controller;

import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.entity.SongFeature;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.PlayerBindService;
import com.maimai.maidx.service.SongDifficultyVoteService;
import com.maimai.maidx.service.SongFeatureService;
import com.maimai.maidx.service.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SongControllerMockTest {

    private MockMvc mockMvc;

    @Mock private SongService songService;
    @Mock private SongFeatureService songFeatureService;
    @Mock private SongDifficultyVoteService voteService;
    @Mock private SongDifficultyRepository songDifficultyRepository;
    @Mock private ScoreRecordRepository scoreRecordRepository;
    @Mock private PlayerBindService playerBindService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SongController(
                songService,
                songFeatureService,
                voteService,
                songDifficultyRepository,
                scoreRecordRepository,
                playerBindService)).build();
    }

    @Test
    void songDetailIncludesDifficultiesAndFeatures() throws Exception {
        Song song = new Song();
        song.setId(1L);
        song.setSongId("s001");
        song.setTitle("Titania");

        SongDifficulty difficulty = new SongDifficulty();
        difficulty.setId(11L);
        difficulty.setSongId(1L);
        difficulty.setDifficulty(3);
        difficulty.setLevel(14);

        SongFeature feature = new SongFeature();
        feature.setDifficultyId(11L);
        feature.setTagName("stamina");
        feature.setWeight(new BigDecimal("40"));
        feature.setSource(1);

        when(songService.getBySongId("s001")).thenReturn(song);
        when(songDifficultyRepository.selectList(any())).thenReturn(List.of(difficulty));
        when(songFeatureService.getFeaturesByDifficultyId(11L)).thenReturn(List.of(feature));
        when(voteService.getVoteStats(11L)).thenReturn(List.of());

        mockMvc.perform(get("/song/s001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songId").value("s001"))
                .andExpect(jsonPath("$.data.difficulties[0].id").value(11))
                .andExpect(jsonPath("$.data.difficulties[0].features[0].tagName").value("stamina"));
    }
}
