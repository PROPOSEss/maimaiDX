<template>
  <view class="radar-chart-container">
    <canvas
      :canvas-id="canvasId"
      :id="canvasId"
      class="radar-canvas"
      :style="{ width: width + 'px', height: height + 'px' }"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'

interface Props {
  canvasId?: string
  labels: string[]
  values: number[]
  maxValue?: number
  width?: number
  height?: number
  color?: string
  bgColor?: string
}

const props = withDefaults(defineProps<Props>(), {
  canvasId: 'abilityRadar',
  maxValue: 100,
  width: 300,
  height: 300,
  color: 'rgba(64, 158, 255, 0.6)',
  bgColor: 'rgba(64, 158, 255, 0.15)'
})

onMounted(() => {
  drawRadar()
})

watch(() => props.values, () => {
  drawRadar()
}, { deep: true })

function drawRadar() {
  const ctx = uni.createCanvasContext(props.canvasId)
  const centerX = props.width / 2
  const centerY = props.height / 2
  const radius = Math.min(centerX, centerY) - 40
  const sides = props.labels.length
  const angleStep = (Math.PI * 2) / sides

  // 清除画布
  ctx.clearRect(0, 0, props.width, props.height)

  // 绘制背景网格（5层）
  for (let layer = 1; layer <= 5; layer++) {
    const r = (radius / 5) * layer
    ctx.beginPath()
    for (let i = 0; i <= sides; i++) {
      const angle = angleStep * i - Math.PI / 2
      const x = centerX + r * Math.cos(angle)
      const y = centerY + r * Math.sin(angle)
      if (i === 0) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
    }
    ctx.closePath()
    ctx.setStrokeStyle('#e4e7ed')
    ctx.setLineWidth(1)
    ctx.stroke()
  }

  // 绘制轴线
  for (let i = 0; i < sides; i++) {
    const angle = angleStep * i - Math.PI / 2
    ctx.beginPath()
    ctx.moveTo(centerX, centerY)
    ctx.lineTo(
      centerX + radius * Math.cos(angle),
      centerY + radius * Math.sin(angle)
    )
    ctx.setStrokeStyle('#e4e7ed')
    ctx.setLineWidth(1)
    ctx.stroke()
  }

  // 绘制数据区域
  ctx.beginPath()
  for (let i = 0; i <= sides; i++) {
    const idx = i % sides
    const value = Math.min(props.values[idx] || 0, props.maxValue)
    const r = (value / props.maxValue) * radius
    const angle = angleStep * idx - Math.PI / 2
    const x = centerX + r * Math.cos(angle)
    const y = centerY + r * Math.sin(angle)
    if (i === 0) {
      ctx.moveTo(x, y)
    } else {
      ctx.lineTo(x, y)
    }
  }
  ctx.closePath()
  ctx.setFillStyle(props.bgColor)
  ctx.fill()
  ctx.setStrokeStyle(props.color.replace('0.6', '1'))
  ctx.setLineWidth(2)
  ctx.stroke()

  // 绘制数据点
  for (let i = 0; i < sides; i++) {
    const value = Math.min(props.values[i] || 0, props.maxValue)
    const r = (value / props.maxValue) * radius
    const angle = angleStep * i - Math.PI / 2
    const x = centerX + r * Math.cos(angle)
    const y = centerY + r * Math.sin(angle)

    ctx.beginPath()
    ctx.arc(x, y, 4, 0, Math.PI * 2)
    ctx.setFillStyle(props.color.replace('0.6', '1'))
    ctx.fill()
  }

  // 绘制标签
  ctx.setFillStyle('#303133')
  ctx.setFontSize(12)
  ctx.setTextAlign('center')
  for (let i = 0; i < sides; i++) {
    const angle = angleStep * i - Math.PI / 2
    const labelR = radius + 28
    const x = centerX + labelR * Math.cos(angle)
    const y = centerY + labelR * Math.sin(angle)
    ctx.fillText(props.labels[i], x, y)
  }

  ctx.draw()
}
</script>

<style scoped>
.radar-chart-container {
  display: flex;
  justify-content: center;
  align-items: center;
}
.radar-canvas {
  /* uni-app canvas */
}
</style>
