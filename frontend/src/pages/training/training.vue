<template>
  <view class="page">
    <view class="header">
      <text class="title">训练建议</text>
      <text class="subtitle">基于你的弱点标签，为你推荐针对性训练谱面</text>
    </view>

    <!-- 按标签分组的训练建议 -->
    <view class="suggestion-groups" v-if="groupedSuggestions.length > 0">
      <view v-for="group in groupedSuggestions" :key="group.tagName" class="suggestion-group">
        <view class="group-header">
          <view class="group-tag" :style="{ backgroundColor: TAG_BG_COLORS[group.tagName], borderColor: TAG_COLORS[group.tagName] }">
            <text class="group-tag-name" :style="{ color: TAG_COLORS[group.tagName] }">{{ group.tagName }}</text>
          </view>
          <text class="group-count">{{ group.items.length }} 首推荐</text>
        </view>

        <view v-for="item in group.items" :key="item.id" class="suggestion-card">
          <view class="card-left">
            <view class="song-title">{{ item.song?.title || '未知歌曲' }}</view>
            <view class="song-artist">{{ item.song?.artist || '' }}</view>
            <view class="song-meta">
              <text class="diff-badge" :style="{ backgroundColor: DIFFICULTY_COLORS[item.difficulty || 0] }">
                {{ item.difficultyDisplay }}
              </text>
              <text class="level-text" :style="{ color: getLevelColor(item.level || 0) }">Lv.{{ item.level }}</text>
              <text class="priority-badge" v-if="item.priority === 1">高优先</text>
            </view>
            <text class="suggestion-reason">{{ item.reason }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view class="empty-state" v-else>
      <text class="empty-text">暂无训练建议</text>
      <text class="empty-hint">请先在能力画像页面刷新，再查看训练建议</text>
    </view>

    <!-- 底部刷新 -->
    <view class="bottom-action" v-if="userStore.bindStatus">
      <button class="btn-refresh" @tap="handleRefresh">刷新训练建议</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTrainingSuggestions, refreshTraining } from '../../api'
import { useUserStore } from '../../stores/user'
import { TAG_COLORS, TAG_BG_COLORS, DIFFICULTY_COLORS, getLevelColor } from '../../utils/constants'

const userStore = useUserStore()
const suggestions = ref<any[]>([])

const groupedSuggestions = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const item of suggestions.value) {
    if (!groups[item.tagName]) {
      groups[item.tagName] = []
    }
    groups[item.tagName].push(item)
  }
  return Object.entries(groups).map(([tagName, items]) => ({ tagName, items }))
})

onShow(() => {
  if (userStore.bindStatus) {
    loadData()
  }
})

async function loadData() {
  try {
    suggestions.value = await getTrainingSuggestions()
  } catch {
    // ignore
  }
}

async function handleRefresh() {
  uni.showLoading({ title: '生成中...' })
  try {
    suggestions.value = await refreshTraining()
    uni.showToast({ title: '建议已刷新', icon: 'success' })
  } finally {
    uni.hideLoading()
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 160rpx;
}

.header {
  padding: 30rpx;
  background: linear-gradient(135deg, #1A1A2E, #16213E);
}

.title {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}

.subtitle {
  display: block;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 8rpx;
}

.suggestion-groups {
  padding: 30rpx;
}

.suggestion-group {
  margin-bottom: 40rpx;
}

.group-header {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.group-tag {
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  border: 1rpx solid;
  margin-right: 16rpx;
}

.group-tag-name {
  font-size: 14px;
  font-weight: 600;
}

.group-count {
  font-size: 13px;
  color: #999;
}

.suggestion-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 12rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.04);
}

.song-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.song-artist {
  font-size: 13px;
  color: #999;
  margin-top: 6rpx;
}

.song-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
}

.diff-badge {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.level-text {
  font-size: 14px;
  font-weight: 700;
}

.priority-badge {
  background: #E63946;
  color: #fff;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 11px;
}

.suggestion-reason {
  display: block;
  font-size: 12px;
  color: #999;
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #f5f5f5;
}

.empty-state {
  text-align: center;
  padding: 120rpx 60rpx;
}

.empty-text {
  display: block;
  font-size: 16px;
  color: #999;
}

.empty-hint {
  display: block;
  font-size: 13px;
  color: #ccc;
  margin-top: 12rpx;
}

.bottom-action {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 30rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
}

.btn-refresh {
  background: #FF6B35;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  height: 80rpx;
  font-size: 15px;
  font-weight: 600;
}
</style>
