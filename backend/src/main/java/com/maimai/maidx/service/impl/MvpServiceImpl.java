package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.config.GlobalExceptionHandler.BusinessException;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.entity.RecommendationItem;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.ScoreSnapshot;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.enums.DifficultyEnum;
import com.maimai.maidx.repository.RecommendationItemRepository;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.ScoreSnapshotRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.MvpService;
import com.maimai.maidx.service.SongCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MvpServiceImpl implements MvpService {

    private static final BigDecimal TARGET_SSS = new BigDecimal("100.000");
    private static final BigDecimal TARGET_SSS_PLUS = new BigDecimal("100.500");

    private final SongRepository songRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final ScoreSnapshotRepository scoreSnapshotRepository;
    private final ScoreRecordRepository scoreRecordRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final SongCatalogService songCatalogService;

    @Override
    @Transactional
    public int syncSongs(List<MvpDtos.SongImportItem> items) throws IOException {
        List<MvpDtos.SongImportItem> source = items;
        if (source == null || source.isEmpty()) {
            Resource resource = resourceLoader.getResource("classpath:data/songs-mvp.json");
            source = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<MvpDtos.SongImportItem>>() {});
        }

        int chartCount = 0;
        for (MvpDtos.SongImportItem item : source) {
            Song song = upsertSong(item);
            for (MvpDtos.ChartImportItem chartItem : item.getCharts()) {
                upsertChart(song.getId(), chartItem);
                chartCount++;
            }
        }
        try {
            songCatalogService.clearSongCaches();
        } catch (RuntimeException e) {
            log.warn("Song cache clear failed after song sync: error={}", e.getClass().getSimpleName());
        }
        return chartCount;
    }

    @Override
    public List<MvpDtos.SongQueryItem> querySongs(String keyword, Integer level, BigDecimal minDs,
                                                  BigDecimal maxDs, String version, int page, int size) {
        List<Song> songs = songRepository.selectList(new LambdaQueryWrapper<Song>()
                .like(StringUtils.hasText(keyword), Song::getTitle, keyword)
                .eq(StringUtils.hasText(version), Song::getVersion, version)
                .orderByDesc(Song::getUpdatedAt));

        List<MvpDtos.SongQueryItem> filtered = songs.stream()
                .map(this::toSongQueryItem)
                .filter(item -> item.getCharts().stream().anyMatch(chart -> matchChart(chart, level, minDs, maxDs)))
                .toList();

        int from = Math.max(0, (Math.max(page, 1) - 1) * Math.max(size, 1));
        int to = Math.min(filtered.size(), from + Math.max(size, 1));
        if (from >= filtered.size()) {
            return List.of();
        }
        return filtered.subList(from, to);
    }

    @Override
    public List<MvpDtos.ChartItem> getCharts(String songId) {
        return songCatalogService.getCharts(songId);
    }

    @Override
    @Transactional
    public MvpDtos.ImportResult importScores(Long userId, MvpDtos.ScoreImportRequest request) {
        return importScores(userId, request, null);
    }

    @Override
    @Transactional
    public MvpDtos.ImportResult importScores(Long userId, MvpDtos.ScoreImportRequest request, String requestId) {
        if (request == null || request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new IllegalArgumentException("成绩 JSON 不能为空");
        }
        if (StringUtils.hasText(requestId)) {
            ScoreSnapshot existing = findSnapshotByRequestId(userId, requestId);
            if (existing != null) {
                return toImportResult(existing);
            }
        }

        ScoreSnapshot snapshot = new ScoreSnapshot();
        snapshot.setUserId(userId);
        snapshot.setRequestId(StringUtils.hasText(requestId) ? requestId : null);
        snapshot.setSource(StringUtils.hasText(request.getSource()) ? request.getSource() : "manual_json");
        snapshot.setRating(request.getRating() == null ? 0 : request.getRating());
        snapshot.setRecordCount(request.getRecords().size());
        snapshot.setImportedAt(LocalDateTime.now());
        try {
            scoreSnapshotRepository.insert(snapshot);
        } catch (DuplicateKeyException e) {
            if (StringUtils.hasText(requestId)) {
                ScoreSnapshot existing = findSnapshotByRequestId(userId, requestId);
                if (existing != null) {
                    return toImportResult(existing);
                }
            }
            throw e;
        }

        List<ScoreRecord> inserted = new ArrayList<>();
        for (MvpDtos.ScoreImportItem item : request.getRecords()) {
            Song song = findSongByPublicId(item.getSongId())
                    .orElseThrow(() -> new IllegalArgumentException("歌曲不存在: " + item.getSongId()));
            SongDifficulty chart = findChart(song.getId(), item.getDifficulty())
                    .orElseThrow(() -> new IllegalArgumentException("谱面不存在: " + item.getSongId() + " / " + item.getDifficulty()));

            ScoreRecord record = new ScoreRecord();
            record.setSnapshotId(snapshot.getId());
            record.setUserId(userId);
            record.setSongId(song.getId());
            record.setDifficultyId(chart.getId());
            record.setAchievementRate(item.getAchievement());
            record.setDxScore(item.getDxScore());
            record.setRank(item.getRate());
            record.setFc(item.getFc());
            record.setFs(item.getFs());
            record.setScore(item.getDxScore() == null ? 0 : item.getDxScore());
            record.setRa(calculateRa(chart.getLevelDecimal(), item.getAchievement()));
            record.setIsB50(0);
            record.setPlayCount(1);
            record.setBestPlayTime(LocalDateTime.now());
            record.setLastPlayTime(LocalDateTime.now());
            scoreRecordRepository.insert(record);
            inserted.add(record);
        }

        List<ScoreRecord> b50 = inserted.stream()
                .sorted(Comparator.comparing(ScoreRecord::getRa, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .toList();
        Set<Long> b50Ids = b50.stream().map(ScoreRecord::getId).collect(Collectors.toSet());
        Map<Long, Song> songMap = loadSongs(inserted.stream().map(ScoreRecord::getSongId).collect(Collectors.toSet()));
        for (ScoreRecord record : inserted) {
            record.setIsB50(b50Ids.contains(record.getId()) ? 1 : 0);
            Song song = songMap.get(record.getSongId());
            record.setB50Type(song != null && Objects.equals(song.getIsNew(), 1) ? "new" : "old");
            scoreRecordRepository.updateById(record);
        }

        return toImportResult(snapshot, inserted.size(), b50.size());
    }

    private ScoreSnapshot findSnapshotByRequestId(Long userId, String requestId) {
        return scoreSnapshotRepository.selectOne(new LambdaQueryWrapper<ScoreSnapshot>()
                .eq(ScoreSnapshot::getUserId, userId)
                .eq(ScoreSnapshot::getRequestId, requestId)
                .last("LIMIT 1"));
    }

    private MvpDtos.ImportResult toImportResult(ScoreSnapshot snapshot) {
        Long b50Count = scoreRecordRepository.selectCount(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getSnapshotId, snapshot.getId())
                .eq(ScoreRecord::getIsB50, 1));
        return toImportResult(snapshot, snapshot.getRecordCount() == null ? 0 : snapshot.getRecordCount(), b50Count.intValue());
    }

    private MvpDtos.ImportResult toImportResult(ScoreSnapshot snapshot, int importedCount, int b50Count) {
        MvpDtos.ImportResult result = new MvpDtos.ImportResult();
        result.setSnapshotId(snapshot.getId());
        result.setUserId(snapshot.getUserId());
        result.setRating(snapshot.getRating());
        result.setImportedCount(importedCount);
        result.setB50Count(b50Count);
        result.setImportedAt(snapshot.getImportedAt());
        return result;
    }

    @Override
    public MvpDtos.B50Response getB50(Long userId, Long snapshotId) {
        ScoreSnapshot snapshot = resolveSnapshot(userId, snapshotId);
        List<ScoreRecord> records = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .eq(ScoreRecord::getSnapshotId, snapshot.getId())
                .eq(ScoreRecord::getIsB50, 1)
                .orderByDesc(ScoreRecord::getRa));

        List<MvpDtos.ScoreItem> items = records.stream().map(this::toScoreItem).toList();
        MvpDtos.B50Response response = new MvpDtos.B50Response();
        response.setSnapshotId(snapshot.getId());
        response.setRating(snapshot.getRating());
        response.setCount(items.size());
        response.setEdgeRa(items.stream().map(MvpDtos.ScoreItem::getRa).filter(Objects::nonNull).min(Integer::compareTo).orElse(0));
        response.setRecords(items);
        response.setLevelDistribution(items.stream().collect(Collectors.groupingBy(i -> String.valueOf(i.getLevel()), LinkedHashMap::new, Collectors.counting())));
        response.setDsDistribution(items.stream().collect(Collectors.groupingBy(i -> dsBucket(i.getDs()), LinkedHashMap::new, Collectors.counting())));
        return response;
    }

    @Override
    @Transactional
    public MvpDtos.RecommendationResponse generateRecommendations(Long userId, Long snapshotId) {
        ScoreSnapshot snapshot = resolveSnapshot(userId, snapshotId);
        MvpDtos.B50Response b50 = getB50(userId, snapshot.getId());
        int edgeRa = b50.getEdgeRa() == null ? 0 : b50.getEdgeRa();

        recommendationItemRepository.delete(new LambdaQueryWrapper<RecommendationItem>()
                .eq(RecommendationItem::getUserId, userId)
                .eq(RecommendationItem::getSnapshotId, snapshot.getId()));

        List<ScoreRecord> snapshotScores = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .eq(ScoreRecord::getSnapshotId, snapshot.getId()));
        Map<Long, ScoreRecord> scoreByChart = snapshotScores.stream()
                .collect(Collectors.toMap(ScoreRecord::getDifficultyId, Function.identity(), (a, b) -> a));

        List<SongDifficulty> charts = songDifficultyRepository.selectList(new LambdaQueryWrapper<SongDifficulty>()
                .orderByDesc(SongDifficulty::getLevelDecimal));

        List<RecommendationItem> candidates = new ArrayList<>();
        for (SongDifficulty chart : charts) {
            ScoreRecord current = scoreByChart.get(chart.getId());
            BigDecimal achievement = current == null ? BigDecimal.ZERO : current.getAchievementRate();
            BigDecimal target = achievement.compareTo(new BigDecimal("99.800")) >= 0 ? TARGET_SSS_PLUS : TARGET_SSS;
            if (achievement.compareTo(target) >= 0) {
                continue;
            }

            int targetRa = calculateRa(chart.getLevelDecimal(), target);
            int currentRa = current == null || current.getRa() == null ? 0 : current.getRa();
            int expectedGain = Math.max(0, targetRa - Math.max(edgeRa, currentRa));
            if (expectedGain <= 0 && achievement.compareTo(new BigDecimal("98.000")) < 0) {
                continue;
            }

            RecommendationItem item = new RecommendationItem();
            item.setUserId(userId);
            item.setSnapshotId(snapshot.getId());
            item.setSongId(chart.getSongId());
            item.setChartId(chart.getId());
            item.setCurrentAchievement(current == null ? null : achievement);
            item.setTargetAchievement(target);
            item.setExpectedGain(expectedGain);
            item.setDifficultyLevel(difficultyLevel(chart, achievement));
            item.setRecommendScore(recommendScore(chart, achievement, expectedGain));
            item.setReason(buildReason(chart, achievement, target, expectedGain, edgeRa));
            candidates.add(item);
        }

        candidates.stream()
                .sorted(Comparator.comparing(RecommendationItem::getRecommendScore).reversed())
                .limit(20)
                .forEach(recommendationItemRepository::insert);
        return getRecommendations(userId, snapshot.getId());
    }

    @Override
    public MvpDtos.RecommendationResponse getRecommendations(Long userId, Long snapshotId) {
        ScoreSnapshot snapshot = resolveSnapshot(userId, snapshotId);
        List<RecommendationItem> items = recommendationItemRepository.selectList(new LambdaQueryWrapper<RecommendationItem>()
                .eq(RecommendationItem::getUserId, userId)
                .eq(RecommendationItem::getSnapshotId, snapshot.getId())
                .orderByDesc(RecommendationItem::getRecommendScore));
        MvpDtos.RecommendationResponse response = new MvpDtos.RecommendationResponse();
        response.setSnapshotId(snapshot.getId());
        response.setCount(items.size());
        response.setItems(items.stream().map(this::toRecommendationDto).toList());
        return response;
    }

    @Override
    public MvpDtos.GrowthResponse getGrowth(Long userId, Long fromSnapshotId, Long toSnapshotId) {
        if (fromSnapshotId == null || toSnapshotId == null) {
            throw new BusinessException(400, "fromSnapshotId 和 toSnapshotId 不能为空");
        }

        MvpDtos.B50Response fromB50 = getB50(userId, fromSnapshotId);
        MvpDtos.B50Response toB50 = getB50(userId, toSnapshotId);
        Map<String, MvpDtos.ScoreItem> fromMap = toB50RecordMap(fromB50.getRecords());
        Map<String, MvpDtos.ScoreItem> toMap = toB50RecordMap(toB50.getRecords());

        List<MvpDtos.GrowthScoreChangeItem> newB50 = toMap.entrySet().stream()
                .filter(entry -> !fromMap.containsKey(entry.getKey()))
                .map(entry -> toGrowthScoreChangeItem(null, entry.getValue()))
                .toList();
        List<MvpDtos.GrowthScoreChangeItem> droppedB50 = fromMap.entrySet().stream()
                .filter(entry -> !toMap.containsKey(entry.getKey()))
                .map(entry -> toGrowthScoreChangeItem(entry.getValue(), null))
                .toList();
        List<MvpDtos.GrowthScoreChangeItem> improvedScores = toMap.entrySet().stream()
                .filter(entry -> fromMap.containsKey(entry.getKey()))
                .map(entry -> toGrowthScoreChangeItem(fromMap.get(entry.getKey()), entry.getValue()))
                .filter(item -> item.getRaDelta() != null && item.getRaDelta() > 0)
                .toList();

        MvpDtos.GrowthResponse response = new MvpDtos.GrowthResponse();
        response.setUserId(userId);
        response.setFromSnapshotId(fromB50.getSnapshotId());
        response.setToSnapshotId(toB50.getSnapshotId());
        response.setFromRating(fromB50.getRating());
        response.setToRating(toB50.getRating());
        response.setRatingDelta(nullSafeInt(toB50.getRating()) - nullSafeInt(fromB50.getRating()));
        response.setFromEdgeRa(fromB50.getEdgeRa());
        response.setToEdgeRa(toB50.getEdgeRa());
        response.setEdgeRaDelta(nullSafeInt(toB50.getEdgeRa()) - nullSafeInt(fromB50.getEdgeRa()));
        response.setNewB50(newB50);
        response.setDroppedB50(droppedB50);
        response.setImprovedScores(improvedScores);
        return response;
    }

    @Override
    public MvpDtos.ReportResponse getReport(Long userId, Long snapshotId) {
        ScoreSnapshot snapshot = resolveSnapshot(userId, snapshotId);
        MvpDtos.ReportResponse response = new MvpDtos.ReportResponse();
        response.setUserId(userId);
        response.setSnapshotId(snapshot.getId());
        response.setRating(snapshot.getRating());
        response.setB50(getB50(userId, snapshot.getId()));
        response.setRecommendations(getRecommendations(userId, snapshot.getId()));
        return response;
    }

    private Song upsertSong(MvpDtos.SongImportItem item) {
        Song song = findSongByPublicId(item.getSongId()).orElseGet(Song::new);
        song.setSongId(item.getSongId());
        song.setTitle(item.getTitle());
        song.setArtist(item.getArtist());
        song.setGenre(item.getGenre());
        song.setBpm(item.getBpm());
        song.setVersion(item.getVersion());
        song.setIsNew(item.getIsNew() == null ? 0 : item.getIsNew());
        if (song.getId() == null) {
            songRepository.insert(song);
        } else {
            songRepository.updateById(song);
        }
        return song;
    }

    private void upsertChart(Long songId, MvpDtos.ChartImportItem item) {
        SongDifficulty chart = findChart(songId, item.getDifficulty()).orElseGet(SongDifficulty::new);
        chart.setSongId(songId);
        chart.setDifficulty(item.getDifficulty());
        chart.setLevel(item.getLevel());
        chart.setLevelDecimal(item.getDs());
        chart.setFitDiff(item.getFitDiff());
        chart.setNoteCount(item.getNotes());
        chart.setTapCount(item.getTap());
        chart.setHoldCount(item.getHold());
        chart.setSlideCount(item.getSlide());
        chart.setTouchCount(item.getTouch());
        chart.setBreakCount(item.getBreakCount());
        chart.setCharter(item.getCharter());
        if (chart.getId() == null) {
            songDifficultyRepository.insert(chart);
        } else {
            songDifficultyRepository.updateById(chart);
        }
    }

    private Optional<Song> findSongByPublicId(String songId) {
        return Optional.ofNullable(songRepository.selectOne(new LambdaQueryWrapper<Song>()
                .eq(Song::getSongId, songId)
                .last("LIMIT 1")));
    }

    private Optional<SongDifficulty> findChart(Long songId, Integer difficulty) {
        return Optional.ofNullable(songDifficultyRepository.selectOne(new LambdaQueryWrapper<SongDifficulty>()
                .eq(SongDifficulty::getSongId, songId)
                .eq(SongDifficulty::getDifficulty, difficulty)
                .last("LIMIT 1")));
    }

    private ScoreSnapshot resolveSnapshot(Long userId, Long snapshotId) {
        LambdaQueryWrapper<ScoreSnapshot> wrapper = new LambdaQueryWrapper<ScoreSnapshot>()
                .eq(ScoreSnapshot::getUserId, userId);
        if (snapshotId != null) {
            wrapper.eq(ScoreSnapshot::getId, snapshotId);
        } else {
            wrapper.orderByDesc(ScoreSnapshot::getImportedAt).last("LIMIT 1");
        }
        ScoreSnapshot snapshot = scoreSnapshotRepository.selectOne(wrapper);
        if (snapshot == null) {
            throw new IllegalArgumentException("未找到成绩快照，请先导入成绩 JSON");
        }
        return snapshot;
    }

    private MvpDtos.SongQueryItem toSongQueryItem(Song song) {
        MvpDtos.SongQueryItem item = new MvpDtos.SongQueryItem();
        item.setId(song.getId());
        item.setSongId(song.getSongId());
        item.setTitle(song.getTitle());
        item.setArtist(song.getArtist());
        item.setGenre(song.getGenre());
        item.setBpm(song.getBpm());
        item.setVersion(song.getVersion());
        item.setIsNew(song.getIsNew());
        item.setCharts(songCatalogService.getCharts(song.getSongId()));
        return item;
    }

    private MvpDtos.ScoreItem toScoreItem(ScoreRecord record) {
        Song song = songRepository.selectById(record.getSongId());
        SongDifficulty chart = songDifficultyRepository.selectById(record.getDifficultyId());
        MvpDtos.ScoreItem item = new MvpDtos.ScoreItem();
        item.setId(record.getId());
        item.setSongId(song == null ? null : song.getSongId());
        item.setTitle(song == null ? null : song.getTitle());
        item.setArtist(song == null ? null : song.getArtist());
        item.setDifficulty(chart == null ? null : chart.getDifficulty());
        item.setDifficultyName(chart == null ? null : difficultyName(chart.getDifficulty()));
        item.setLevel(chart == null ? null : chart.getLevel());
        item.setDs(chart == null ? null : chart.getLevelDecimal());
        item.setAchievement(record.getAchievementRate());
        item.setDxScore(record.getDxScore());
        item.setRate(record.getRank());
        item.setFc(record.getFc());
        item.setFs(record.getFs());
        item.setRa(record.getRa());
        item.setIsB50(record.getIsB50());
        item.setB50Type(record.getB50Type());
        return item;
    }

    private MvpDtos.RecommendationItemDto toRecommendationDto(RecommendationItem item) {
        Song song = songRepository.selectById(item.getSongId());
        SongDifficulty chart = songDifficultyRepository.selectById(item.getChartId());
        MvpDtos.RecommendationItemDto dto = new MvpDtos.RecommendationItemDto();
        dto.setId(item.getId());
        dto.setSongId(song == null ? null : song.getSongId());
        dto.setTitle(song == null ? null : song.getTitle());
        dto.setArtist(song == null ? null : song.getArtist());
        dto.setChartId(item.getChartId());
        dto.setDifficulty(chart == null ? null : chart.getDifficulty());
        dto.setDifficultyName(chart == null ? null : difficultyName(chart.getDifficulty()));
        dto.setLevel(chart == null ? null : chart.getLevel());
        dto.setDs(chart == null ? null : chart.getLevelDecimal());
        dto.setFitDiff(chart == null ? null : chart.getFitDiff());
        dto.setCurrentAchievement(item.getCurrentAchievement());
        dto.setTargetAchievement(item.getTargetAchievement());
        dto.setExpectedGain(item.getExpectedGain());
        dto.setDifficultyLevel(item.getDifficultyLevel());
        dto.setRecommendScore(item.getRecommendScore());
        dto.setReason(item.getReason());
        return dto;
    }

    private Map<String, MvpDtos.ScoreItem> toB50RecordMap(List<MvpDtos.ScoreItem> records) {
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        return records.stream()
                .filter(item -> item.getSongId() != null && item.getDifficulty() != null)
                .collect(Collectors.toMap(this::b50RecordKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private String b50RecordKey(MvpDtos.ScoreItem item) {
        return item.getSongId() + "#" + item.getDifficulty();
    }

    private MvpDtos.GrowthScoreChangeItem toGrowthScoreChangeItem(MvpDtos.ScoreItem from, MvpDtos.ScoreItem to) {
        MvpDtos.ScoreItem display = to != null ? to : from;
        MvpDtos.GrowthScoreChangeItem item = new MvpDtos.GrowthScoreChangeItem();
        item.setSongId(display == null ? null : display.getSongId());
        item.setTitle(display == null ? null : display.getTitle());
        item.setArtist(display == null ? null : display.getArtist());
        item.setDifficulty(display == null ? null : display.getDifficulty());
        item.setDifficultyName(display == null ? null : display.getDifficultyName());
        item.setLevel(display == null ? null : display.getLevel());
        item.setDs(display == null ? null : display.getDs());
        item.setFromAchievement(from == null ? null : from.getAchievement());
        item.setToAchievement(to == null ? null : to.getAchievement());
        item.setFromRa(from == null ? null : from.getRa());
        item.setToRa(to == null ? null : to.getRa());
        item.setRaDelta(to == null || from == null ? null : nullSafeInt(to.getRa()) - nullSafeInt(from.getRa()));
        return item;
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean matchChart(MvpDtos.ChartItem chart, Integer level, BigDecimal minDs, BigDecimal maxDs) {
        if (level != null && !Objects.equals(chart.getLevel(), level)) {
            return false;
        }
        if (minDs != null && (chart.getDs() == null || chart.getDs().compareTo(minDs) < 0)) {
            return false;
        }
        return maxDs == null || (chart.getDs() != null && chart.getDs().compareTo(maxDs) <= 0);
    }

    private int calculateRa(BigDecimal ds, BigDecimal achievement) {
        if (ds == null || achievement == null) {
            return 0;
        }
        BigDecimal coefficient;
        if (achievement.compareTo(TARGET_SSS_PLUS) >= 0) {
            coefficient = new BigDecimal("22.4");
        } else if (achievement.compareTo(TARGET_SSS) >= 0) {
            coefficient = new BigDecimal("21.6");
        } else if (achievement.compareTo(new BigDecimal("99.500")) >= 0) {
            coefficient = new BigDecimal("21.1");
        } else if (achievement.compareTo(new BigDecimal("99.000")) >= 0) {
            coefficient = new BigDecimal("20.8");
        } else if (achievement.compareTo(new BigDecimal("98.000")) >= 0) {
            coefficient = new BigDecimal("20.3");
        } else if (achievement.compareTo(new BigDecimal("97.000")) >= 0) {
            coefficient = new BigDecimal("20.0");
        } else {
            coefficient = new BigDecimal("18.0");
        }
        return ds.multiply(achievement).multiply(coefficient).divide(new BigDecimal("100"), 0, RoundingMode.DOWN).intValue();
    }

    private BigDecimal recommendScore(SongDifficulty chart, BigDecimal achievement, int expectedGain) {
        BigDecimal base = BigDecimal.valueOf(expectedGain * 3L);
        BigDecimal closeness = BigDecimal.valueOf(Math.max(0, 10050 - achievement.multiply(new BigDecimal("100")).intValue()))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal fitBonus = chart.getFitDiff() == null ? BigDecimal.ZERO : chart.getFitDiff().multiply(new BigDecimal("2"));
        return base.add(closeness).add(fitBonus).setScale(2, RoundingMode.HALF_UP);
    }

    private String difficultyLevel(SongDifficulty chart, BigDecimal achievement) {
        if (achievement.compareTo(new BigDecimal("99.800")) >= 0) {
            return "冲刺SSS+";
        }
        if (achievement.compareTo(new BigDecimal("99.000")) >= 0) {
            return "稳定提分";
        }
        if (chart.getFitDiff() != null && chart.getFitDiff().compareTo(new BigDecimal("14.5")) <= 0) {
            return "适合练习";
        }
        return "挑战候选";
    }

    private String buildReason(SongDifficulty chart, BigDecimal current, BigDecimal target, int expectedGain, int edgeRa) {
        List<String> reasons = new ArrayList<>();
        if (current.compareTo(new BigDecimal("99.800")) >= 0) {
            reasons.add("当前成绩接近SSS+");
        } else if (current.compareTo(new BigDecimal("99.000")) >= 0) {
            reasons.add("当前成绩接近SSS");
        } else if (current.compareTo(BigDecimal.ZERO) == 0) {
            reasons.add("尚未导入成绩，可作为同等级补全曲目");
        } else {
            reasons.add("当前成绩仍有明显提升空间");
        }
        if (chart.getFitDiff() != null && chart.getLevelDecimal() != null && chart.getFitDiff().compareTo(chart.getLevelDecimal()) <= 0) {
            reasons.add("定数较高但拟合难度适中");
        }
        if (expectedGain > 0) {
            reasons.add("预计达成" + target + "%后可高于B50边缘位" + edgeRa + "，提升约" + expectedGain + "ra");
        } else {
            reasons.add("适合当前稳定等级练习");
        }
        return String.join("；", reasons);
    }

    private String difficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "UNKNOWN";
        }
        try {
            return DifficultyEnum.fromCode(difficulty).getName();
        } catch (IllegalArgumentException ex) {
            return "UNKNOWN";
        }
    }

    private String dsBucket(BigDecimal ds) {
        if (ds == null) {
            return "unknown";
        }
        return ds.setScale(0, RoundingMode.DOWN).toPlainString();
    }

    private Map<Long, Song> loadSongs(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return songRepository.selectBatchIds(ids).stream().collect(Collectors.toMap(Song::getId, Function.identity()));
    }
}
