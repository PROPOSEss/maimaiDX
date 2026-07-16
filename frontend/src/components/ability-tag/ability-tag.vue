<template>
  <view class="ability-tag" :class="[typeClass]">
    <text class="tag-name">{{ name }}</text>
    <text v-if="score !== undefined" class="tag-score">{{ formattedScore }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  name: string
  score?: number
  type?: 'normal' | 'weakness' | 'strength'
}

const props = withDefaults(defineProps<Props>(), {
  type: 'normal'
})

const typeClass = computed(() => {
  const map: Record<string, string> = {
    normal: 'tag-normal',
    weakness: 'tag-weakness',
    strength: 'tag-strength'
  }
  return map[props.type] || 'tag-normal'
})

const formattedScore = computed(() => {
  if (props.score === undefined) return ''
  return Math.round(props.score)
})
</script>

<style scoped>
.ability-tag {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  margin: 6rpx;
  gap: 8rpx;
}
.tag-normal {
  background: rgba(64, 158, 255, 0.15);
  color: #409eff;
}
.tag-weakness {
  background: rgba(245, 108, 108, 0.15);
  color: #f56c6c;
}
.tag-strength {
  background: rgba(103, 194, 58, 0.15);
  color: #67c23a;
}
.tag-name {
  font-size: 24rpx;
  font-weight: 500;
}
.tag-score {
  font-size: 22rpx;
  opacity: 0.8;
}
</style>
