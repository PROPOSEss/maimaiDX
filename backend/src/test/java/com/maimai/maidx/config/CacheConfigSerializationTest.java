package com.maimai.maidx.config;

import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.dto.SongDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigSerializationTest {

    private final RedisSerializer<Object> serializer = new CacheConfig().redisCacheValueSerializer();

    @Test
    void songDetailDtoCanRoundTripThroughJsonSerializer() {
        SongDetailResponse source = new SongDetailResponse();
        source.setId(1L);
        source.setSongId("s001");
        source.setTitle("中文歌曲");
        source.setArtist("测试艺术家");

        Object result = serializer.deserialize(serializer.serialize(source));

        assertThat(result).isInstanceOf(SongDetailResponse.class);
        SongDetailResponse response = (SongDetailResponse) result;
        assertThat(response.getSongId()).isEqualTo("s001");
        assertThat(response.getTitle()).isEqualTo("中文歌曲");
    }

    @Test
    void chartListDtoCanRoundTripThroughJsonSerializer() {
        MvpDtos.ChartItem chart = new MvpDtos.ChartItem();
        chart.setId(11L);
        chart.setDifficulty(3);
        chart.setDifficultyName("MASTER");
        chart.setDs(new BigDecimal("13.7"));
        chart.setCharter("测试谱师");

        Object result = serializer.deserialize(serializer.serialize(new ArrayList<>(List.of(chart))));

        assertThat(result).isInstanceOf(List.class);
        List<?> charts = (List<?>) result;
        assertThat(charts).hasSize(1);
        assertThat(charts.get(0)).isInstanceOf(MvpDtos.ChartItem.class);
        MvpDtos.ChartItem response = (MvpDtos.ChartItem) charts.get(0);
        assertThat(response.getDs()).isEqualByComparingTo("13.7");
        assertThat(response.getCharter()).isEqualTo("测试谱师");
    }
}
