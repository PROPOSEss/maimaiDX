<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useUserStore } from './stores/user'

onLaunch(() => {
  // 初始化用户状态
  const userStore = useUserStore()
  userStore.loadFromStorage()
})
onShow(() => {
  console.log('App Show')
})
onHide(() => {
  console.log('App Hide')
})
</script>

<style>
/* 全局样式 */
page {
  background-color: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 28rpx;
  color: #303133;
  line-height: 1.5;
}

/* 通用卡片样式 */
.card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

/* 通用间距 */
.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #303133;
  padding: 24rpx 20rpx 16rpx;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 0;
}

.empty-state .empty-text {
  font-size: 26rpx;
  color: #909399;
  margin-top: 20rpx;
}

/* 加载中 */
.loading-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60rpx 0;
}

/* 通用按钮 */
.btn-primary {
  background: linear-gradient(135deg, #FF6B35, #FF8F65);
  color: #ffffff;
  border: none;
  border-radius: 40rpx;
  padding: 20rpx 48rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.btn-secondary {
  background: #ffffff;
  color: #FF6B35;
  border: 2rpx solid #FF6B35;
  border-radius: 40rpx;
  padding: 18rpx 48rpx;
  font-size: 28rpx;
  font-weight: 600;
}

/* Flex 工具类 */
.flex-row { display: flex; flex-direction: row; }
.flex-col { display: flex; flex-direction: column; }
.flex-center { display: flex; justify-content: center; align-items: center; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.flex-1 { flex: 1; }

/* 文字截断 */
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 安全底部 */
.safe-bottom {
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
