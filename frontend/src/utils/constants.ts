/**
 * 标签颜色映射
 */
export const TAG_COLORS: Record<string, string> = {
  '反手': '#FF6B35',
  '交互': '#E63946',
  '撞手': '#F4A261',
  '纵连': '#2A9D8F',
  '体力': '#E76F51',
  '读谱': '#264653',
  '节奏难': '#7209B7',
  '错位星星': '#3A0CA3',
  'Touch圈': '#4CC9F0',
}

export const TAG_BG_COLORS: Record<string, string> = {
  '反手': 'rgba(255,107,53,0.15)',
  '交互': 'rgba(230,57,70,0.15)',
  '撞手': 'rgba(244,162,97,0.15)',
  '纵连': 'rgba(42,157,143,0.15)',
  '体力': 'rgba(231,111,81,0.15)',
  '读谱': 'rgba(38,70,83,0.15)',
  '节奏难': 'rgba(114,9,183,0.15)',
  '错位星星': 'rgba(58,12,163,0.15)',
  'Touch圈': 'rgba(76,201,240,0.15)',
}

/**
 * 难度等级颜色
 */
export function getLevelColor(level: number): string {
  if (level >= 15) return '#FF0050'
  if (level >= 14) return '#FF3377'
  if (level >= 13) return '#FF6699'
  if (level >= 12) return '#FF9E2C'
  return '#66BBFF'
}

/**
 * 难度名称
 */
export const DIFFICULTY_NAMES: Record<number, string> = {
  0: 'BASIC',
  1: 'ADVANCED',
  2: 'EXPERT',
  3: 'MASTER',
  4: 'Re:MASTER',
}

/**
 * 难度颜色
 */
export const DIFFICULTY_COLORS: Record<number, string> = {
  0: '#8FD8F0',
  1: '#9CF0AD',
  2: '#FFE066',
  3: '#FF9966',
  4: '#FF6688',
}

/**
 * 评级颜色
 */
export function getRankColor(rank: string): string {
  const colors: Record<string, string> = {
    'SSS+': '#FFD700',
    'SSS': '#FFA500',
    'SS': '#FF6347',
    'S': '#FF4500',
    'AAA': '#C0C0C0',
    'AA': '#CD853F',
    'A': '#8B7355',
  }
  return colors[rank] || '#999'
}

/**
 * 评级图标
 */
export function getRankIcon(rank: string): string {
  if (!rank) return ''
  return rank
}

/**
 * 格式化分数
 */
export function formatScore(score: number): string {
  return score.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}
