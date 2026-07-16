package com.maimai.maidx.controller;

import com.maimai.maidx.entity.PlayerAbility;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.service.PlayerAbilityService;
import com.maimai.maidx.service.PlayerBindService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerMockTest {

    private MockMvc mockMvc;

    @Mock
    private PlayerAbilityService playerAbilityService;

    @Mock
    private PlayerBindService playerBindService;

    @BeforeEach
    void setUp() {
        AnalysisController controller = new AnalysisController(playerAbilityService, playerBindService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAbilityReturnsRadarDataWhenPlayerIsBound() throws Exception {
        PlayerBind bind = new PlayerBind();
        bind.setId(10L);
        bind.setUserId(999L);
        bind.setPlayerName("tester");
        bind.setRating(12000);

        PlayerAbility ability = new PlayerAbility();
        ability.setPlayerId(10L);
        ability.setTagName("mock-tag");
        ability.setAvgScore(new BigDecimal("99.5000"));
        ability.setAvgRating(new BigDecimal("13.7000"));
        ability.setTotalSongs(8);
        ability.setSsspCount(2);
        ability.setWeaknessScore(new BigDecimal("12.3000"));
        ability.setIsWeakness(0);

        PlayerAbility weakness = new PlayerAbility();
        weakness.setPlayerId(10L);
        weakness.setTagName("weak-tag");
        weakness.setAvgScore(new BigDecimal("94.0000"));
        weakness.setAvgRating(new BigDecimal("12.1000"));
        weakness.setTotalSongs(5);
        weakness.setSsspCount(0);
        weakness.setWeaknessScore(new BigDecimal("72.0000"));
        weakness.setIsWeakness(1);

        when(playerBindService.getByUserId(999L)).thenReturn(bind);
        when(playerAbilityService.getRadarData(10L)).thenReturn(List.of(ability));
        when(playerAbilityService.getPlayerWeaknesses(10L)).thenReturn(List.of(weakness));

        mockMvc.perform(get("/analysis/ability")
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playerId").value(10))
                .andExpect(jsonPath("$.data.playerName").value("tester"))
                .andExpect(jsonPath("$.data.rating").value(12000))
                .andExpect(jsonPath("$.data.abilities[0].tagName").value("mock-tag"))
                .andExpect(jsonPath("$.data.abilities[0].isWeakness").value(false))
                .andExpect(jsonPath("$.data.weaknesses[0].tagName").value("weak-tag"))
                .andExpect(jsonPath("$.data.weaknesses[0].isWeakness").value(true));
    }

    @Test
    void getAbilityReturnsErrorWhenPlayerIsNotBound() throws Exception {
        when(playerBindService.getByUserId(999L)).thenReturn(null);

        mockMvc.perform(get("/analysis/ability")
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
