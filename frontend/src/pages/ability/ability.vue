<template>
  <view class="page">
    <view class="header">
      <text class="title">能力画像</text>
      <text class="subtitle">基于你的成绩和谱面标签分析</text>
    </view>

    <!-- 雷达图区域 -->
    <view class="radar-section" v-if="abilities.length > 0">
      <view class="radar-container">
        <!-- 用CSS实现的简易雷达图（后续替换为ECharts） -->
        <view class="radar-placeholder">
          <text class="radar-hint">雷达图</text>
          <view class="radar-grid">
            <view
              v-for="(item, index) in abilities"
              :key="item.tagName"
              class="radar-dot"
              :style="{
                transform: `rotate(${index * 40}deg) translateY(-${Math.min(item.avgScore / 10100, 1) * 120}rpx)`,
                backgroundColor: TAG_COLORS[item.tagName] || '#FF6B35'
              }"
            />
          </view>
        </view>
      </view>
    </view>

    <!-- 能力列表 -->
    <view class="ability-list" v-if="abilities.length > 0">
      <view
        v-for="item in abilities"
        :key="item.tagName"
        class="ability-card"
        :class="{ 'is-weakness': item.isWeakness }"
      >
        <view class="ability-header">
          <view class="ability-tag" :style="{ backgroundColor: TAG_BG_COLORS[item.tagName], borderColor: TAG_COLORS[item.tagName] }">
            <text class="ability-tag-name" :style="{ color: TAG_COLORS[item.tagName] }">{{ item.tagName }}</text>
          </view>
          <view class="ability-scores">
            <text class="ability-avg">均分 {{ item.avgScore?.toFixed(0) || 0 }}</text>
            <text class="ability-songs">{{ item.totalSongs }} 首</text>
          </view>
        </view>
        <view class="ability-bar-wrap">
          <view class="ability-bar" :style="{ width: `${Math.min((item.avgScore || 0) / 10100 * 100, 100)}%`, backgroundColor: TAG_COLORS[item.tagName] }" />
        </view>
        <view class="ability-footer" v-if="item.isWeakness">
          <text class="weakness-badge">⚠️ 弱点 {{ item.weaknessScore?.toFixed(0) }}%</text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view class="empty-state" v-else>
      <text class="empty-text">暂无能力数据</text>
      <text class="empty-hint">请先同步成绩后刷新</text>
    </view>

    <!-- 底部操作 -->
    <view class="bottom-actions" v-if="userStore.bindStatus">
      <button class="btn-refresh" @tap="handleRefresh">刷新画像</button>
      <button class="btn-training" @tap="goTraining">查看训练建议</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAbility, refreshAbility } from '../../api'
import { useUserStore } from '../../stores/user'
import { TAG_COLORS, TAG_BG_COLORS } from '../../utils/constants'

const userStore = useUserStore()
const abilities = ref<any[]>([])

onShow(() => {
  if (userStore.bindStatus) {
    loadData()
  }
})

async function loadData() {
  try {
    const data = await getAbility()
    abilities.value = data.abilities || []
  } catch {
    // ignore
  }
}

async function handleRefresh() {
  uni.showLoading({ title: '计算中...' })
  try {
    const data = await refreshAbility()
    abilities.value = data.abilities || []
    uni.showToast({ title: '画像已更新', icon: 'success' })
  } finally {
    uni.hideLoading()
  }
}

function goTraining() {
  uni.switchTab({ url: '/pages/training/training' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 200rpx;
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

.radar-section {
  padding: 30rpx;
}

.radar-container {
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);
}

.radar-placeholder {
  width: 240rpx;
  height: 240rpx;
  margin: 0 auto;
  position: relative;
}

.radar-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 14px;
  color: #999;
}

.ability-list {
  padding: 0 30rpx;
}

.ability-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.04);
}

.ability-card.is-weakness {
  border-left: 6rpx solid #E63946;
}

.ability-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.ability-tag {
  padding: 6rpx 20rpx;
  border-radius: 20rpx;
  border: 1rpx solid;
}

.ability-tag-name {
  font-size: 13px;
  font-weight: 600;
}

.ability-scores {
  display: flex;
  gap: 20rpx;
}

.ability-avg {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.ability-songs {
  font-size: 12px;
  color: #999;
}

.ability-bar-wrap {
  height: 12rpx;
  background: #f0f0f0;
  border-radius: 6rpx;
  overflow: hidden;
}

.ability-bar {
  height: 100%;
  border-radius: 6rpx;
  transition: width 0.6s ease;
}

.ability-footer {
  margin-top: 12rpx;
}

.weakness-badge {
  font-size: 12px;
  color: #E63946;
  background: rgba(230, 57, 70, 0.08);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}

.empty-state {
  text-align: center;
  padding: 100rpx 0;
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

.bottom-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 30rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
  display: flex;
  gap: 20rpx;
}

.btn-refresh {
  flex: 1;
  background: #FF6B35;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  height: 80rpx;
  font-size: 15px;
  font-weight: 600;
}

.btn-training {
  flex: 1;
  background: #fff;
  color: #FF6B35;
  border: 2rpx solid #FF6B35;
  border-radius: 12rpx;
  height: 80rpx;
  font-size: 15px;
  font-weight: 600;
}
</style>
