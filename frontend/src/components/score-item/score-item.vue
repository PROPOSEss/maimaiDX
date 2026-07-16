<template>
  <view class="score-item">
    <view class="score-left">
      <text class="score-title">{{ songTitle }}</text>
      <view class="score-meta">
        <text class="diff-badge" :class="difficultyClass">{{ diffName }} {{ level }}</text>
        <text v-if="dxScore" class="dx-score">DX {{ dxScore }}</text>
      </view>
    </view>
    <view class="score-right">
      <text class="achievement" :class="achievementClass">{{ achievementText }}</text>
      <text class="rank" :class="rankClass">{{ rank }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  songTitle: string
  difficulty: string
  level: number
  achievement?: number
  dxScore?: number
  combo?: number
}

const props = defineProps<Props>()

const diffName = computed(() => {
  const map: Record<string, string> = {
    BASIC: 'BASIC',
    ADVANCED: 'ADV',
    EXPERT: 'EXP',
    MASTER: 'MAS',
    'Re:MASTER': 'Re:M'
  }
  return map[props.difficulty] || props.difficulty
})

const difficultyClass = computed(() => {
  if (props.difficulty === 'Re:MASTER') return 'diff-re'
  if (props.difficulty === 'MASTER') return 'diff-master'
  if (props.difficulty === 'EXPERT') return 'diff-expert'
  return 'diff-normal'
})

const achievementText = computed(() => {
  return props.achievement ? props.achievement.toFixed(4) + '%' : '--'
})

const achievementClass = computed(() => {
  if (!props.achievement) return ''
  if (props.achievement >= 100.0) return 'achievement-s'
  if (props.achievement >= 99.0) return 'achievement-a'
  return 'achievement-b'
})

const rank = computed(() => {
  if (!props.achievement) return ''
  if (props.achievement >= 100.5) return 'SSS+'
  if (props.achievement >= 100.0) return 'SSS'
  if (props.achievement >= 99.5) return 'SS'
  if (props.achievement >= 99.0) return 'S'
  if (props.achievement >= 97.0) return 'AAA'
  if (props.achievement >= 95.0) return 'AA'
  return 'A'
})

const rankClass = computed(() => {
  const r = props.achievement || 0
  if (r >= 100.0) return 'rank-gold'
  if (r >= 99.0) return 'rank-purple'
  return 'rank-normal'
})
</script>

<style scoped>
.score-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f2f5;
}
.score-item:last-child {
  border-bottom: none;
}
.score-left {
  flex: 1;
}
.score-title {
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
}
.score-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 6rpx;
}
.diff-badge {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
}
.diff-normal { background: #67c23a20; color: #67c23a; }
.diff-expert { background: #e6a23c20; color: #e6a23c; }
.diff-master { background: #f56c6c20; color: #f56c6c; }
.diff-re { background: #9b59b620; color: #9b59b6; }
.dx-score {
  font-size: 20rpx;
  color: #909399;
}
.score-right {
  text-align: right;
}
.achievement {
  font-size: 28rpx;
  font-weight: 600;
  color: #606266;
}
.achievement-s { color: #f5a623; }
.achievement-a { color: #409eff; }
.rank {
  font-size: 22rpx;
  font-weight: 700;
  margin-top: 4rpx;
}
.rank-gold { color: #f5a623; }
.rank-purple { color: #9b59b6; }
.rank-normal { color: #909399; }
</style>
