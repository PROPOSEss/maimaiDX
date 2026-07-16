<template>
  <view class="page">
    <!-- 自定义导航栏 -->
    <view class="navbar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="navbar-content">
        <text class="navbar-title">MaiDX Insight</text>
      </view>
    </view>

    <view class="content" :style="{ paddingTop: navBarHeight + 'px' }">
      <!-- 玩家信息卡片 -->
      <view class="player-card" v-if="userStore.isLoggedInUser && userStore.playerInfo">
        <view class="player-header">
          <view class="rating-badge">
            <text class="rating-value">{{ userStore.playerInfo.rating }}</text>
            <text class="rating-label">RATING</text>
          </view>
          <view class="player-meta">
            <text class="player-name">{{ userStore.playerInfo.playerName || '未设置昵称' }}</text>
            <text class="player-maid">MAID: {{ maskMaId(userStore.playerInfo.maId) }}</text>
          </view>
        </view>
        <view class="player-stats">
          <view class="stat-item">
            <text class="stat-value">{{ userStore.playerInfo.maxRating || '-' }}</text>
            <text class="stat-label">最高Rating</text>
          </view>
          <view class="stat-divider" />
          <view class="stat-item">
            <text class="stat-value">{{ syncStatusText }}</text>
            <text class="stat-label">同步状态</text>
          </view>
          <view class="stat-divider" />
          <view class="stat-item" @tap="syncNow">
            <text class="stat-value sync-btn">同步成绩</text>
            <text class="stat-label">点击更新</text>
          </view>
        </view>
      </view>

      <!-- 未登录状态 -->
      <view class="login-card" v-else-if="!userStore.isLoggedInUser" @tap="goLogin">
        <text class="login-text">点击登录，开始分析你的舞萌能力</text>
        <text class="login-arrow">&#x2192;</text>
      </view>

      <!-- 已登录未绑定 -->
      <view class="bind-card" v-else-if="!userStore.bindStatus" @tap="goBind">
        <text class="bind-text">绑定MAID，解锁全部功能</text>
        <text class="bind-arrow">&#x2192;</text>
      </view>

      <!-- 功能入口 -->
      <view class="feature-grid">
        <view class="feature-item" @tap="goPage('/pages/ability/ability')">
          <view class="feature-icon radar-icon">📊</view>
          <text class="feature-name">能力画像</text>
          <text class="feature-desc">分析你的9维能力</text>
        </view>
        <view class="feature-item" @tap="goPage('/pages/scores/scores')">
          <view class="feature-icon score-icon">🏆</view>
          <text class="feature-name">B50成绩</text>
          <text class="feature-desc">查看最佳成绩</text>
        </view>
        <view class="feature-item" @tap="goPage('/pages/training/training')">
          <view class="feature-icon training-icon">🎯</view>
          <text class="feature-name">训练建议</text>
          <text class="feature-desc">针对性提升方案</text>
        </view>
        <view class="feature-item" @tap="goPage('/pages/songs/songs')">
          <view class="feature-icon song-icon">🎵</view>
          <text class="feature-name">谱面数据库</text>
          <text class="feature-desc">标签体系查询</text>
        </view>
      </view>

      <!-- 弱点速览 -->
      <view class="weakness-section" v-if="weaknesses.length > 0">
        <view class="section-header">
          <text class="section-title">⚠️ 你的弱点</text>
          <text class="section-more" @tap="goPage('/pages/ability/ability')">查看详情 &#x2192;</text>
        </view>
        <view class="weakness-list">
          <view
            v-for="item in weaknesses"
            :key="item.tagName"
            class="weakness-tag"
            :style="{ backgroundColor: TAG_BG_COLORS[item.tagName] || '#f5f5f5', borderColor: TAG_COLORS[item.tagName] || '#999' }"
            @tap="goPage('/pages/training/training')"
          >
            <text class="weakness-name" :style="{ color: TAG_COLORS[item.tagName] || '#333' }">{{ item.tagName }}</text>
            <text class="weakness-score">{{ item.weaknessScore.toFixed(0) }}%</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../stores/user'
import { getAbility } from '../../api'
import { TAG_COLORS, TAG_BG_COLORS } from '../../utils/constants'

const userStore = useUserStore()
const weaknesses = ref<any[]>([])
const statusBarHeight = ref(44)
const navBarHeight = ref(88)

onMounted(() => {
  const sysInfo = uni.getSystemInfoSync()
  statusBarHeight.value = sysInfo.statusBarHeight || 44
  navBarHeight.value = (sysInfo.statusBarHeight || 44) + 44
})

onShow(() => {
  if (userStore.isLoggedInUser && userStore.bindStatus) {
    loadWeaknesses()
  }
})

const syncStatusText = computed(() => {
  const status = userStore.playerInfo?.syncStatus
  if (status === 2) return '已同步'
  if (status === 1) return '同步中'
  if (status === -1) return '同步失败'
  return '未同步'
})

async function loadWeaknesses() {
  try {
    const data = await getAbility()
    weaknesses.value = data.weaknesses?.slice(0, 3) || []
  } catch {
    // ignore
  }
}

function syncNow() {
  uni.showLoading({ title: '同步中...' })
  userStore.refreshPlayerInfo().finally(() => {
    uni.hideLoading()
    loadWeaknesses()
  })
}

function maskMaId(maId: string) {
  if (!maId || maId.length < 4) return maId
  return maId.slice(0, 2) + '****' + maId.slice(-2)
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}

function goBind() {
  uni.navigateTo({ url: '/pages/bind/bind' })
}

function goPage(url: string) {
  uni.switchTab({ url })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1A1A2E 0%, #16213E 30%, #F5F5F5 60%);
}

.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: #1A1A2E;
}

.navbar-content {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 30rpx;
}

.navbar-title {
  color: #FF6B35;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

.content {
  padding: 20rpx 30rpx;
}

/* 玩家卡片 */
.player-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24rpx;
  padding: 40rpx;
  margin-bottom: 30rpx;
  box-shadow: 0 8rpx 30rpx rgba(0, 0, 0, 0.08);
}

.player-header {
  display: flex;
  align-items: center;
  margin-bottom: 30rpx;
}

.rating-badge {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #FF6B35, #E63946);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-right: 30rpx;
}

.rating-value {
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
}

.rating-label {
  color: rgba(255, 255, 255, 0.8);
  font-size: 18rpx;
}

.player-meta {
  flex: 1;
}

.player-name {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8rpx;
}

.player-maid {
  display: block;
  font-size: 13px;
  color: #999;
}

.player-stats {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding-top: 20rpx;
  border-top: 1rpx solid #f0f0f0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-top: 4rpx;
}

.sync-btn {
  color: #FF6B35;
}

.stat-divider {
  width: 1rpx;
  height: 60rpx;
  background: #eee;
}

/* 登录/绑定卡片 */
.login-card,
.bind-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24rpx;
  padding: 50rpx 40rpx;
  margin-bottom: 30rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8rpx 30rpx rgba(0, 0, 0, 0.08);
}

.login-text,
.bind-text {
  font-size: 16px;
  color: #333;
}

.login-arrow,
.bind-arrow {
  font-size: 20px;
  color: #FF6B35;
}

/* 功能网格 */
.feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-bottom: 30rpx;
}

.feature-item {
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);
}

.feature-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  margin-bottom: 16rpx;
}

.radar-icon { background: rgba(255, 107, 53, 0.12); }
.score-icon { background: rgba(255, 215, 0, 0.12); }
.training-icon { background: rgba(42, 157, 143, 0.12); }
.song-icon { background: rgba(114, 9, 183, 0.12); }

.feature-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6rpx;
}

.feature-desc {
  font-size: 12px;
  color: #999;
}

/* 弱点速览 */
.weakness-section {
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  margin-bottom: 30rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.section-more {
  font-size: 13px;
  color: #FF6B35;
}

.weakness-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.weakness-tag {
  padding: 12rpx 24rpx;
  border-radius: 30rpx;
  border: 1rpx solid;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.weakness-name {
  font-size: 13px;
  font-weight: 500;
}

.weakness-score {
  font-size: 12px;
  color: #999;
}
</style>
