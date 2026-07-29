/**
 * 用户状态管理（Pinia）
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, mockLogin as mockLoginApi, getPlayerInfo, type LoginResult, type PlayerInfo } from '../api'
import { setAuth, clearAuth } from '../utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync('token') || '')
  const userId = ref(Number(uni.getStorageSync('userId')) || 0)
  const nickname = ref('')
  const avatar = ref('')
  const playerInfo = ref<PlayerInfo | null>(null)
  const bindStatus = ref(false)

  const isLoggedInUser = computed(() => !!token.value)

  async function loadFromStorage() {
    token.value = uni.getStorageSync('token') || ''
    userId.value = Number(uni.getStorageSync('userId')) || 0
    if (token.value && userId.value) {
      await refreshPlayerInfo()
    }
  }


  function applyLoginResult(result: LoginResult) {
    token.value = result.token
    userId.value = result.userId
    nickname.value = result.nickname || ''
    avatar.value = result.avatarUrl || ''
    bindStatus.value = result.bound

    setAuth(result.token, String(result.userId))

    if (result.bindInfo) {
      playerInfo.value = {
        id: result.bindInfo.playerId,
        maId: result.bindInfo.maId,
        playerName: result.bindInfo.playerName,
        rating: result.bindInfo.rating,
        maxRating: 0,
        classRank: '',
        lastSyncTime: '',
        syncStatus: 0,
        userId: result.userId,
      }
    } else {
      playerInfo.value = null
    }
  }

  async function mockLogin(username = 'tester') {
    const result = await mockLoginApi(username)
    applyLoginResult(result)
    return result
  }

  /**
   * 微信登录
   */
  async function wxLogin() {
    return new Promise((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: async (loginRes) => {
          try {
            const result = await loginApi({ code: loginRes.code })
            applyLoginResult(result)


            resolve(result)
          } catch (err) {
            reject(err)
          }
        },
        fail: (err) => {
          uni.showToast({ title: '微信登录失败', icon: 'none' })
          reject(err)
        },
      })
    })
  }

  /**
   * 刷新玩家信息
   */
  async function refreshPlayerInfo() {
    try {
      const info = await getPlayerInfo()
      playerInfo.value = info
      bindStatus.value = !!info
    } catch {
      // ignore
    }
  }

  /**
   * 退出登录
   */
  function logout() {
    token.value = ''
    userId.value = 0
    nickname.value = ''
    avatar.value = ''
    playerInfo.value = null
    bindStatus.value = false
    clearAuth()
  }

  return {
    token,
    userId,
    nickname,
    avatar,
    playerInfo,
    bindStatus,
    isLoggedInUser,
    loadFromStorage,
    wxLogin,
    mockLogin,
    refreshPlayerInfo,
    logout,
  }
})
