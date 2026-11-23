package com.example.wireswitch.work

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.wireswitch.data.SecretStore
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 이 앱이 직접 VPN 을 연결하는 엔진. WireGuard 공식 터널 라이브러리(GoBackend)를 쓰며
 * 별도의 WireGuard 앱이 필요 없다.
 *
 * - 안드로이드 VPN 은 한 번에 하나만 켤 수 있다. 다른 구성을 켜면 켜져 있던 것을 먼저 끈다.
 * - [setUp] 은 연결이 끝날 때까지 막히므로 **메인 스레드에서 부르면 안 된다.**
 * - 설정 파일 내용과 개인키는 로그에 남기지 않는다.
 */
object VpnEngine {

    private const val TAG = "VpnEngine"

    enum class Result(val label: String) {
        OK("완료"),
        NO_CONFIG("설정 없음"),
        NO_PERMISSION("VPN 권한 필요"),
        FAILED("연결 실패"),
    }

    /** 구성 하나에 대응하는 라이브러리용 터널. 이름은 라이브러리 규칙(영숫자, 15자 이하)에 맞게 만든다. */
    private class AppTunnel(val configId: Long) : Tunnel {
        override fun getName(): String = "ws$configId"

        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.UP) {
                _active.value = configId
            }
            else if (_active.value == configId) {
                _active.value = null
            }
        }
    }

    private val lock = Any()
    private var backend: GoBackend? = null
    private val tunnels = mutableMapOf<Long, AppTunnel>()

    private val _active = MutableStateFlow<Long?>(null)

    /** 지금 켜져 있는 구성 id. 없으면 null. 화면이 이 값을 관찰해 `활성` 표시를 갱신한다. */
    val active: StateFlow<Long?> = _active.asStateFlow()

    val activeId: Long?
        get() = _active.value

    /** VPN 연결 권한(시스템 승인)을 이미 받았는가. */
    fun hasVpnPermission(context: Context): Boolean = VpnService.prepare(context) == null

    /** 아직 승인받지 않았다면 승인 화면을 띄울 인텐트. 이미 받았으면 null. */
    fun permissionIntent(context: Context): Intent? = VpnService.prepare(context)

    /** [configId] 구성을 켜거나(true) 끈다. 백그라운드 스레드에서 호출해야 한다. */
    fun setUp(context: Context, configId: Long, up: Boolean): Result = synchronized(lock) {
        setUpLocked(context.applicationContext, configId, up)
    }

    /** 켜져 있는 VPN 을 전부 끈다. 끈 개수를 돌려준다. */
    fun disconnectAll(context: Context): Pair<Int, Int> = synchronized(lock) {
        val id = _active.value ?: return@synchronized 0 to 0
        val result = setUpLocked(context.applicationContext, id, false)
        1 to (if (result == Result.OK) 0 else 1)
    }

    private fun backend(context: Context): GoBackend =
        backend ?: GoBackend(context).also { backend = it }

    private fun setUpLocked(context: Context, configId: Long, up: Boolean): Result {
        return try {
            val engine = backend(context)
            val tunnel = tunnels.getOrPut(configId) { AppTunnel(configId) }

            if (!up) {
                if (_active.value == configId) {
                    engine.setState(tunnel, Tunnel.State.DOWN, null)
                }
                return Result.OK
            }

            if (!hasVpnPermission(context)) {
                return Result.NO_PERMISSION
            }
            val text = SecretStore.load(context, configId) ?: return Result.NO_CONFIG
            val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

            // 다른 구성이 켜져 있으면 먼저 끈다 — 안드로이드는 VPN 을 하나만 쓸 수 있다.
            _active.value?.takeIf { it != configId }?.let { otherId ->
                tunnels[otherId]?.let { engine.setState(it, Tunnel.State.DOWN, null) }
            }
            engine.setState(tunnel, Tunnel.State.UP, config)
            Result.OK
        }
        catch (e: Exception) {
            // 예외 메시지에 설정 내용 일부가 들어갈 수 있어 종류만 남긴다.
            Log.w(TAG, "구성 $configId ${if (up) "켜기" else "끄기"} 실패: ${e.javaClass.simpleName}")
            Result.FAILED
        }
    }
}
