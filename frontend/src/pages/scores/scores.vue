<template>
  <view class="page">
    <view class="header">
      <text class="title">我的成绩</text>
    </view>

    <!-- 切换Tab -->
    <view class="tab-bar">
      <view class="tab-item active" @tap="switchTab('b50')">B50</view>
      <view class="tab-item" @tap="switchTab('all')">全部成绩</view>
    </view>

    <!-- B50成绩列表 -->
    <view class="score-list" v-if="currentTab === 'b50'">
      <view v-for="(item, index) in b50List" :key="item.id" class="score-card">
        <view class="rank-num">#{{ index + 1 }}</view>
        <view class="score-info">
          <view class="song-title">{{ item.title }}</view>
          <view class="song-meta">
            <text class="diff-badge" :style="{ backgroundColor: DIFFICULTY_COLORS[item.difficulty || 0] }">
              {{ item.difficultyName }}
            </text>
            <text class="level-text" :style="{ color: getLevelColor(item.level || 0) }">Lv.{{ item.level }}</text>
          </view>
        </view>
        <view class="score-right">
          <text class="score-value">{{ formatScore(item.score) }}</text>
          <text class="score-rank" :style="{ color: getRankColor(item.rank) }">{{ item.rank }}</text>
          <text class="score-fc" v-if="item.fc">{{ item.fc }}</text>
        </view>
      </view>
    </view>

    <!-- 全部成绩 -->
    <scroll-view scroll-y class="score-list" v-else @scrolltolower="loadMore">
      <view v-for="item in allScores" :key="item.id" class="score-card">
        <view class="score-info">
          <view class="song-title">{{ item.title }}</view>
          <view class="song-meta">
            <text class="diff-badge" :style="{ backgroundColor: DIFFICULTY_COLORS[item.difficulty || 0] }">
              {{ item.difficultyName }}
            </text>
            <text class="level-text" :style="{ color: getLevelColor(item.level || 0) }">Lv.{{ item.level }}</text>
          </view>
        </view>
        <view class="score-right">
          <text class="score-value">{{ formatScore(item.score) }}</text>
          <text class="score-rank" :style="{ color: getRankColor(item.rank) }">{{ item.rank }}</text>
        </view>
      </view>
      <view class="load-more" v-if="hasMore">
        <text>上拉加载更多</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getB50, getScoreList } from '../../api'
import { useUserStore } from '../../stores/user'
import { DIFFICULTY_COLORS, getLevelColor, getRankColor, formatScore } from '../../utils/constants'

const userStore = useUserStore()
const currentTab = ref('b50')
const b50List = ref<any[]>([])
const allScores = ref<any[]>([])
const currentPage = ref(1)
const hasMore = ref(true)

onShow(() => {
  if (userStore.bindStatus) {
    currentTab.value === 'b50' ? loadB50() : loadAll()
  }
})

function switchTab(tab: string) {
  currentTab.value = tab
  if (tab === 'b50') loadB50()
  else loadAll()
}

async function loadB50() {
  try {
    b50List.value = await getB50()
  } catch { /* ignore */ }
}

async function loadAll() {
  currentPage.value = 1
  try {
    const res = await getScoreList(1, 20)
    allScores.value = res.records
    hasMore.value = res.records.length >= 20
  } catch { /* ignore */ }
}

async function loadMore() {
  if (!hasMore.value) return
  currentPage.value++
  try {
    const res = await getScoreList(currentPage.value, 20)
    allScores.value.push(...res.records)
    hasMore.value = res.records.length >= 20
  } catch { /* ignore */ }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f5f5; }
.header { padding: 30rpx; background: linear-gradient(135deg, #1A1A2E, #16213E); }
.title { font-size: 22px; font-weight: 700; color: #fff; }

.tab-bar {
  display: flex;
  background: #fff;
  padding: 0 30rpx;
  border-bottom: 1rpx solid #eee;
}
.tab-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 15px;
  color: #666;
  position: relative;
}
.tab-item.active { color: #FF6B35; font-weight: 600; }
.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60rpx;
  height: 4rpx;
  background: #FF6B35;
  border-radius: 2rpx;
}

.score-list { padding: 20rpx 30rpx; }
.score-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 12rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.04);
}
.rank-num {
  font-size: 16px;
  font-weight: 700;
  color: #999;
  width: 60rpx;
  text-align: center;
}
.score-info { flex: 1; margin-left: 12rpx; }
.song-title { font-size: 15px; font-weight: 600; color: #333; }
.song-meta { display: flex; gap: 12rpx; margin-top: 8rpx; }
.diff-badge { padding: 4rpx 12rpx; border-radius: 8rpx; color: #fff; font-size: 11px; }
.level-text { font-size: 13px; font-weight: 700; }
.score-right { text-align: right; }
.score-value { display: block; font-size: 18px; font-weight: 700; color: #333; }
.score-rank { display: block; font-size: 14px; font-weight: 600; margin-top: 4rpx; }
.score-fc { display: block; font-size: 11px; color: #FFD700; margin-top: 2rpx; }
.load-more { text-align: center; padding: 30rpx; color: #999; font-size: 13px; }
</style>
