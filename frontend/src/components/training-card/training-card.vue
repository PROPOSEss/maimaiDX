<template>
  <view class="training-card" @click="onTap">
    <view class="card-top">
      <view class="priority-badge" :class="priorityClass">
        <text>P{{ priority }}</text>
      </view>
      <view class="target-info">
        <text class="target-tag">目标能力：{{ targetTag }}</text>
        <text class="target-reason">{{ targetReason }}</text>
      </view>
    </view>
    <view class="card-content">
      <view class="song-section">
        <text class="song-title">{{ songTitle }}</text>
        <view class="song-diff">
          <text class="diff-badge">{{ diffName }}</text>
          <text class="level">{{ level }}</text>
        </view>
      </view>
      <text class="recommend-reason">{{ recommendReason }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  songTitle: string
  difficulty: string
  level: number
  targetTag: string
  targetReason: string
  recommendReason: string
  priority: number
}

const props = defineProps<Props>()

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

const priorityClass = computed(() => {
  if (props.priority <= 1) return 'priority-1'
  if (props.priority <= 3) return 'priority-2'
  return 'priority-3'
})
</script>

<style scoped>
.training-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}
.card-top {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.priority-badge {
  min-width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
  color: #ffffff;
}
.priority-1 { background: #f56c6c; }
.priority-2 { background: #e6a23c; }
.priority-3 { background: #409eff; }
.target-info {
  flex: 1;
}
.target-tag {
  font-size: 26rpx;
  font-weight: 600;
  color: #303133;
}
.target-reason {
  font-size: 22rpx;
  color: #909399;
  margin-top: 4rpx;
}
.card-content {
  background: #f8f9fa;
  border-radius: 12rpx;
  padding: 16rpx;
}
.song-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.song-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
}
.song-diff {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.diff-badge {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  background: #e6a23c20;
  color: #e6a23c;
  border-radius: 6rpx;
  font-weight: 600;
}
.level {
  font-size: 24rpx;
  font-weight: 700;
  color: #606266;
}
.recommend-reason {
  font-size: 22rpx;
  color: #909399;
  margin-top: 12rpx;
  line-height: 1.6;
}
</style>
