<template>
  <view class="page">
    <view class="header">
      <text class="title">谱面数据库</text>
      <text class="subtitle">12+ 高难谱面标签查询</text>
    </view>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <input
        v-model="keyword"
        placeholder="搜索歌曲/艺术家"
        confirm-type="search"
        @confirm="doSearch"
        class="search-input"
      />
    </view>

    <!-- 歌曲列表 -->
    <view class="song-list">
      <view
        v-for="song in songList"
        :key="song.id"
        class="song-card"
        @tap="goDetail(song.songId)"
      >
        <view class="song-info">
          <text class="song-title">{{ song.title }}</text>
          <text class="song-artist">{{ song.artist }}</text>
          <text class="song-version">{{ song.version }}</text>
        </view>
      </view>
    </view>

    <view class="load-more" v-if="songList.length > 0">
      <text @tap="loadMore">加载更多</text>
    </view>

    <view class="empty" v-if="songList.length === 0 && !loading">
      <text>暂无歌曲数据，请先导入谱面数据库</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getSongList, searchSongs } from '../../api'

const keyword = ref('')
const songList = ref<any[]>([])
const currentPage = ref(1)
const loading = ref(false)

function doSearch() {
  currentPage.value = 1
  loadSongs()
}

async function loadSongs() {
  if (loading.value) return
  loading.value = true
  try {
    const res = keyword.value
      ? await searchSongs(keyword.value, currentPage.value, 20)
      : await getSongList(currentPage.value, 20)
    if (currentPage.value === 1) {
      songList.value = res.records
    } else {
      songList.value.push(...res.records)
    }
  } finally {
    loading.value = false
  }
}

function loadMore() {
  currentPage.value++
  loadSongs()
}

function goDetail(songId: string) {
  uni.navigateTo({ url: `/pages/song-detail/song-detail?id=${songId}` })
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f5f5; }
.header { padding: 30rpx; background: linear-gradient(135deg, #1A1A2E, #16213E); }
.title { font-size: 22px; font-weight: 700; color: #fff; }
.subtitle { display: block; font-size: 13px; color: rgba(255, 255, 255, 0.6); margin-top: 8rpx; }

.search-bar { padding: 20rpx 30rpx; }
.search-input {
  background: #fff;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  font-size: 14px;
  border: 1rpx solid #eee;
}

.song-list { padding: 0 30rpx; }
.song-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 12rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.04);
}
.song-title { display: block; font-size: 15px; font-weight: 600; color: #333; }
.song-artist { display: block; font-size: 13px; color: #666; margin-top: 6rpx; }
.song-version { display: block; font-size: 12px; color: #999; margin-top: 4rpx; }
.load-more { text-align: center; padding: 30rpx; color: #FF6B35; font-size: 13px; }
.empty { text-align: center; padding: 120rpx 60rpx; color: #999; font-size: 14px; }
</style>
