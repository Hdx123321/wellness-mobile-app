package com.wellnessmate.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局会话管理器。
 * 当任何 API 调用检测到 Token 过期（401）时，调用 [expireSession]，
 * AuthViewModel 观察 [expired] 状态变化后自动登出，MainActivity 会切到登录页。
 */
object SessionManager {
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    /** 由 Repositories 层在收到 401 时调用，触发全局登出。 */
    fun expireSession() {
        _expired.value = true
    }

    /** AuthViewModel 处理完登出后重置，防止重复触发。 */
    fun reset() {
        _expired.value = false
    }
}
