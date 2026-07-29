/**
 * API 接口定义
 */
import { get, post, del } from '../utils/request'

// ========== 认证相关 ==========

export interface LoginParams {
  code: string
}

export interface LoginResult {
  token: string
  userId: number
  nickname: string
  avatarUrl: string
  bound: boolean
  bindInfo: {
    playerId: number
    maId: string
    playerName: string
    rating: number
  } | null
}

export function login(params: LoginParams) {
  return post<LoginResult>('/auth/login', params)
}

export function mockLogin(username = 'tester') {
  return post<LoginResult>(`/auth/mock-login?username=${encodeURIComponent(username)}`)
}

// ========== 玩家相关 ==========

export interface PlayerInfo {
  id: number
  userId: number
  maId: string
  playerName: string
  rating: number
  maxRating: number
  classRank: string
  lastSyncTime: string
  syncStatus: number
}

export function bindMaId(maId: string) {
  return post<PlayerInfo>('/player/bind', { maId })
}

export function unbindMaId() {
  return del<void>('/player/unbind')
}

export function getPlayerInfo() {
  return get<PlayerInfo>('/player/info')
}

export function syncScores() {
  return post<void>('/player/sync')
}

// ========== 成绩相关 ==========

export interface ScoreInfo {
  id: number
  difficultyId: number
  songId: string
  title: string
  artist: string
  difficulty: number
  difficultyName: string
  level: number
  score: number
  rank: string
  fc: string
  fs: string
  playCount: number
  bestPlayTime: string
}

export function getB50() {
  return get<ScoreInfo[]>('/score/b50')
}

export function getScoreList(page: number, size: number) {
  return get<{ records: ScoreInfo[]; total: number; current: number; size: number }>('/score/list', { page, size })
}

// ========== 能力画像 ==========

export interface TagAbility {
  tagName: string
  tagCode: string
  description: string
  avgScore: number
  avgRating: number
  totalSongs: number
  ssspCount: number
  weaknessScore: number
  isWeakness: boolean
}

export interface AbilityResult {
  playerId: number
  playerName: string
  rating: number
  abilities: TagAbility[]
  weaknesses: TagAbility[]
}

export function getAbility() {
  return get<AbilityResult>('/analysis/ability')
}

export function getRadarData() {
  return get<TagAbility[]>('/analysis/radar')
}

export function getWeaknesses() {
  return get<TagAbility[]>('/analysis/weaknesses')
}

export function refreshAbility() {
  return post<AbilityResult>('/analysis/ability/refresh')
}

// ========== 训练建议 ==========

export interface TrainingInfo {
  id: number
  tagName: string
  tagNameDisplay: string
  difficultyId: number
  song: {
    songId: string
    title: string
    artist: string
    cover: string
  }
  difficultyDisplay: string
  level: number
  priority: number
  reason: string
}

export function getTrainingSuggestions() {
  return get<TrainingInfo[]>('/training/suggestions')
}

export function refreshTraining() {
  return post<TrainingInfo[]>('/training/suggestions/refresh')
}

export function getTrainingByTag(tagName: string) {
  return get<TrainingInfo[]>(`/training/suggestions/${tagName}`)
}

// ========== 歌曲相关 ==========

export interface SongItem {
  id: number
  songId: string
  title: string
  titleEn: string
  artist: string
  bpm: number
  version: string
}

export interface SongDetail extends SongItem {
  artistEn: string
  genre: string
  difficulties: Array<{
    id: number
    difficulty: number
    difficultyName: string
    level: number
    levelDecimal: number
    noteCount: number
    tapCount: number
    holdCount: number
    slideCount: number
    touchCount: number
    breakCount: number
    features: Array<{
      tagName: string
      weight: number
      source: number
    }>
    playerScore: {
      score: number
      rank: string
      fc: string
      fs: string
      playCount: number
    } | null
  }>
  votedTags: string[]
  voteStats: VoteStat[]
}

export function getSongList(page: number, size: number, keyword?: string) {
  return get<{ records: SongItem[]; total: number }>('/song/list', { page, size, keyword })
}

export function searchSongs(keyword: string, page: number, size: number) {
  return get<{ records: SongItem[]; total: number }>('/song/search', { keyword, page, size })
}

export function getSongDetail(songId: string) {
  return get<SongDetail>(`/song/${songId}`)
}

// ========== 投票相关 ==========

export interface VoteStat {
  tagName: string
  totalWeight: number
  voteCount: number
}

export function submitVote(difficultyId: number, tagNames: string[]) {
  return post<void>('/vote/submit', { difficultyId, tagNames })
}

export function getMyVotes(difficultyId: number) {
  return get<string[]>(`/vote/my/${difficultyId}`)
}

export function getVoteStats(difficultyId: number) {
  return get<VoteStat[]>(`/vote/stats/${difficultyId}`)
}

export function getAvailableTags() {
  return get<{ name: string; code: string }[]>('/vote/tags')
}
