<template>
  <view class="page">
    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="!detail" class="state">歌曲详情加载失败</view>
    <template v-else>
      <view class="header">
        <text class="title">{{ detail.title }}</text>
        <text class="artist">{{ detail.artist }}</text>
        <view class="meta">
          <text>{{ detail.version }}</text>
          <text>BPM {{ detail.bpm }}</text>
          <text>{{ detail.genre }}</text>
        </view>
      </view>

      <view class="section">
        <text class="section-title">谱面</text>
        <view v-for="item in detail.difficulties" :key="item.id" class="difficulty">
          <view class="difficulty-head">
            <text class="difficulty-name">{{ item.difficultyName }}</text>
            <text class="level">{{ item.levelDecimal || item.level }}</text>
          </view>
          <view class="tags">
            <text v-for="tag in item.features" :key="`${item.id}-${tag.tagName}`" class="tag">
              {{ tag.tagName }} {{ Math.round(tag.weight) }}%
            </text>
          </view>
          <view v-if="item.playerScore" class="score">
            <text>{{ item.playerScore.rank }}</text>
            <text>{{ item.playerScore.score }}</text>
            <text>游玩 {{ item.playerScore.playCount }} 次</text>
          </view>
          <button class="vote-button" size="mini" @tap="openVote(item.id)">为此谱面投票</button>
        </view>
      </view>

      <view v-if="detail.voteStats.length" class="section">
        <text class="section-title">社区投票</text>
        <view v-for="stat in detail.voteStats" :key="stat.tagName" class="stat">
          <text>{{ stat.tagName }}</text>
          <text>{{ stat.voteCount }} 票</text>
        </view>
      </view>

      <view v-if="voteDifficultyId" class="vote-panel">
        <view class="vote-sheet">
          <text class="section-title">选择 1-3 个标签</text>
          <view class="vote-tags">
            <text
              v-for="tag in availableTags"
              :key="tag.name"
              class="vote-tag"
              :class="{ selected: selectedTags.includes(tag.name) }"
              @tap="toggleTag(tag.name)"
            >
              {{ tag.name }}
            </text>
          </view>
          <view class="actions">
            <button size="mini" @tap="voteDifficultyId = null">取消</button>
            <button size="mini" class="submit-button" :disabled="selectedTags.length === 0" @tap="sendVote">
              提交
            </button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getAvailableTags,
  getMyVotes,
  getSongDetail,
  submitVote,
  type SongDetail,
} from '../../api'

const loading = ref(true)
const songId = ref('')
const detail = ref<SongDetail | null>(null)
const availableTags = ref<Array<{ name: string; code: string }>>([])
const voteDifficultyId = ref<number | null>(null)
const selectedTags = ref<string[]>([])

async function loadDetail() {
  if (!songId.value) return
  loading.value = true
  try {
    const result = await getSongDetail(songId.value)
    result.difficulties = result.difficulties || []
    result.votedTags = result.votedTags || []
    result.voteStats = result.voteStats || []
    detail.value = result
  } finally {
    loading.value = false
  }
}

async function openVote(difficultyId: number) {
  voteDifficultyId.value = difficultyId
  const [tags, votes] = await Promise.all([
    getAvailableTags(),
    getMyVotes(difficultyId),
  ])
  availableTags.value = tags
  selectedTags.value = votes
}

function toggleTag(tagName: string) {
  if (selectedTags.value.includes(tagName)) {
    selectedTags.value = selectedTags.value.filter(tag => tag !== tagName)
    return
  }
  if (selectedTags.value.length < 3) {
    selectedTags.value.push(tagName)
  }
}

async function sendVote() {
  if (!voteDifficultyId.value || selectedTags.value.length === 0) return
  await submitVote(voteDifficultyId.value, selectedTags.value)
  voteDifficultyId.value = null
  uni.showToast({ title: '投票成功', icon: 'success' })
  await loadDetail()
}

onLoad((query?: Record<string, string>) => {
  songId.value = query?.id || ''
  loadDetail()
})
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f6f8; color: #222; }
.header { padding: 36rpx 30rpx; background: #1a1a2e; color: #fff; }
.title { display: block; font-size: 24px; font-weight: 700; }
.artist { display: block; margin-top: 8rpx; color: rgba(255, 255, 255, 0.7); }
.meta { display: flex; gap: 24rpx; margin-top: 20rpx; font-size: 12px; color: rgba(255, 255, 255, 0.55); }
.state { padding: 120rpx 30rpx; text-align: center; color: #888; }
.section { padding: 28rpx 30rpx; }
.section-title { display: block; margin-bottom: 20rpx; font-size: 18px; font-weight: 700; }
.difficulty { padding: 24rpx; margin-bottom: 18rpx; background: #fff; border-radius: 12rpx; }
.difficulty-head, .stat, .score, .actions { display: flex; justify-content: space-between; align-items: center; }
.difficulty-name { font-weight: 700; }
.level { color: #e84b4b; font-size: 20px; font-weight: 700; }
.tags, .vote-tags { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 18rpx; }
.tag, .vote-tag { padding: 8rpx 14rpx; background: #f0f2f5; border-radius: 8rpx; font-size: 12px; }
.vote-tag.selected { background: #ff6b35; color: #fff; }
.score { margin-top: 18rpx; color: #666; font-size: 12px; }
.vote-button { margin-top: 20rpx; }
.stat { padding: 18rpx 0; border-bottom: 1rpx solid #e8e8e8; }
.vote-panel { position: fixed; inset: 0; display: flex; align-items: flex-end; background: rgba(0, 0, 0, 0.45); }
.vote-sheet { width: 100%; padding: 30rpx; background: #fff; }
.actions { gap: 20rpx; margin-top: 30rpx; }
.actions button { flex: 1; }
.submit-button { background: #ff6b35; color: #fff; }
</style>
