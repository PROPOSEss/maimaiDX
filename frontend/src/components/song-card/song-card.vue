<template>
  <view class="song-card" @click="onTap">
    <view class="card-header">
      <view class="song-info">
        <text class="song-title">{{ songTitle }}</text>
        <text class="song-artist">{{ artist }}</text>
      </view>
      <view class="difficulty-badge" :class="difficultyClass">
        <text class="difficulty-name">{{ diffName }}</text>
        <text class="difficulty-level">{{ level }}</text>
      </view>
    </view>
    <view v-if="tags && tags.length > 0" class="card-tags">
      <text
        v-for="tag in tags"
        :key="tag.name"
        class="mini-tag"
      >{{ tag.name }} {{ Math.round(tag.weight * 100) }}%</text>
    </view>
    <view v-if="showScore && achievement" class="card-score">
      <text class="achievement">{{ achievement.toFixed(1) }}%</text>
      <text class="rank-badge" :class="rankClass">{{ rank }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Tag {
  name: string
  weight: number
}

interface Props {
  songTitle: string
  artist?: string
  difficulty: string
  level: number
  tags?: Tag[]
  achievement?: number
  showScore?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showScore: false
})

const emit = defineEmits<{
  tap: []
}>()

function onTap() {
  emit('tap')
}

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
  if (props.difficulty === 'Re:MASTER' || props.level >= 14) return 'diff-re'
  if (props.difficulty === 'MASTER' || props.level >= 13) return 'diff-master'
  if (props.difficulty === 'EXPERT' || props.level >= 12) return 'diff-expert'
  return 'diff-normal'
})

const rank = computed(() => {
  if (!props.achievement) return ''
  if (props.achievement >= 100.5) return 'SSS+'
  if (props.achievement >= 100.0) return 'SSS'
  if (props.achievement >= 99.5) return 'SS'
  if (props.achievement >= 99.0) return 'S'
  if (props.achievement >= 98.0) return 'S+'
  if (props.achievement >= 97.0) return 'AAA'
  if (props.achievement >= 95.0) return 'AA'
  if (props.achievement >= 93.0) return 'A'
  return 'B'
})

const rankClass = computed(() => {
  const r = props.achievement || 0
  if (r >= 100.0) return 'rank-gold'
  if (r >= 99.0) return 'rank-purple'
  if (r >= 97.0) return 'rank-blue'
  return 'rank-green'
})
</script>

<style scoped>
.song-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.song-info {
  flex: 1;
}
.song-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
}
.song-artist {
  font-size: 22rpx;
  color: #909399;
  margin-top: 4rpx;
}
.difficulty-badge {
  display: flex;
  align-items: center;
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  gap: 8rpx;
}
.diff-normal { background: #67c23a20; }
.diff-expert { background: #e6a23c20; }
.diff-master { background: #f56c6c20; }
.diff-re { background: #9b59b620; }
.difficulty-name {
  font-size: 20rpx;
  font-weight: 600;
  color: #606266;
}
.difficulty-level {
  font-size: 20rpx;
  font-weight: 700;
  color: #303133;
}
.card-tags {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}
.mini-tag {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  background: #f0f2f5;
  border-radius: 12rpx;
  color: #909399;
}
.card-score {
  margin-top: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.achievement {
  font-size: 32rpx;
  font-weight: 700;
  color: #303133;
}
.rank-badge {
  font-size: 20rpx;
  font-weight: 600;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.rank-gold { background: #fde68a; color: #92400e; }
.rank-purple { background: #e9d5ff; color: #581c87; }
.rank-blue { background: #bfdbfe; color: #1e3a5f; }
.rank-green { background: #bbf7d0; color: #166534; }
</style>
