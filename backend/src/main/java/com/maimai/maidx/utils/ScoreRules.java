package com.maimai.maidx.utils;

import com.maimai.maidx.entity.ScoreRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** Explicit project rating policy; not a version-locked official CN game rules engine. */
public final class ScoreRules {
    private ScoreRules() { }
    public static final BigDecimal RAW_MAX = new BigDecimal("101.0000");
    public static final BigDecimal RATING_CAP = new BigDecimal("100.5000");

    public static BigDecimal normalize(BigDecimal raw) {
        if (raw == null || raw.signum() < 0 || raw.compareTo(RAW_MAX) > 0 || raw.scale() > 4) {
            throw new IllegalArgumentException("achievement 必须为0到101之间且最多四位小数");
        }
        return raw.setScale(4, RoundingMode.UNNECESSARY);
    }

    public static int calculateRa(BigDecimal ds, BigDecimal raw) {
        BigDecimal achievement = normalize(raw).min(RATING_CAP);
        if (ds == null) return 0;
        String[] boundaries = {"100.5", "100", "99.5", "99", "98", "97", "94", "90", "80", "75", "70", "60", "50", "0"};
        String[] coefficients = {"22.4", "21.6", "21.1", "20.8", "20.3", "20", "16.8", "15.2", "13.6", "12", "11.2", "9.6", "8", "7"};
        for (int i = 0; i < boundaries.length; i++) {
            if (achievement.compareTo(new BigDecimal(boundaries[i])) >= 0) {
                return ds.multiply(achievement).multiply(new BigDecimal(coefficients[i]))
                        .divide(new BigDecimal("100"), 0, RoundingMode.DOWN).intValueExact();
            }
        }
        throw new IllegalStateException("unreachable achievement");
    }

    public static LocalDateTime playedAt(ScoreRecord record) {
        return record.getLastPlayTime() != null ? record.getLastPlayTime() : record.getBestPlayTime();
    }

    /** Greater is better; ID is a deterministic last tie-break, never a claimed play timestamp. */
    public static Comparator<ScoreRecord> bestComparator() {
        return Comparator.comparing(ScoreRecord::getRa, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ScoreRecord::getAchievementRate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ScoreRecord::getDxScore, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ScoreRules::playedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ScoreRecord::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    public static List<ScoreRecord> bestPerChart(Collection<ScoreRecord> records, int limit) {
        Map<Long, ScoreRecord> best = new LinkedHashMap<>();
        for (ScoreRecord record : records) {
            BigDecimal raw = record.getAchievementRate();
            if (record.getDifficultyId() == null || raw == null || raw.signum() < 0 || raw.compareTo(RAW_MAX) > 0 || raw.scale() > 4) continue;
            best.merge(record.getDifficultyId(), record, (a, b) -> bestComparator().compare(a, b) >= 0 ? a : b);
        }
        return best.values().stream().sorted(bestComparator().reversed()).limit(limit).toList();
    }
}
