package com.example.wireswitch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wireswitch.data.VpnConfig
import com.example.wireswitch.data.VpnSettings
import com.example.wireswitch.data.VpnSettingsStore
import com.example.wireswitch.ui.HomeScreen
import com.example.wireswitch.ui.PermissionState
import com.example.wireswitch.ui.WireSwitchTheme
import com.example.wireswitch.util.Permissions
import com.example.wireswitch.work.SettingsPersistence
import com.example.wireswitch.work.VpnController
import com.example.wireswitch.work.VpnEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WireSwitchTheme { WireSwitchRoot() }
        }
    }
}

@Composable
private fun WireSwitchRoot() {
    val context = LocalContext.current
    val store = remember { VpnSettingsStore(context) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(store.load()) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    // 구성별 마지막 기록. 연결 시도 직후/화면 복귀 시 다시 읽는다.
    var lastEvents by remember { mutableStateOf(settings.configs.associate { it.id to store.lastEvent(it.id) }) }

    fun reloadEvents() {
        lastEvents = settings.configs.associate { it.id to store.lastEvent(it.id) }
    }

    // 저장 + 알람 재예약 + 일정에 맞는 상태로 조정 + 바로가기 갱신(설정 화면과 같은 저장 경로).
    fun persist(updated: VpnSettings) {
        SettingsPersistence.save(context, updated)
        reloadEvents()
    }

    // 첫 화면에서 편집한 내용은 입력이 잠깐 멈추면 저장한다.
    var pendingSave by remember { mutableStateOf<VpnSettings?>(null) }
    fun flushPending() {
        pendingSave?.let {
            persist(it)
            pendingSave = null
        }
    }
    LaunchedEffect(pendingSave) {
        val pending = pendingSave ?: return@LaunchedEffect
        delay(600)
        persist(pending)
        pendingSave = null
    }

    // 권한은 설정 앱을 거쳐 바뀔 수 있어 화면이 다시 보일 때마다 새로 읽는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                flushPending()
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                settings = store.load()
                reloadEvents()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionState = remember(refreshTick) {
        PermissionState(
            vpnGranted = VpnEngine.hasVpnPermission(context),
            exactAlarmGranted = Permissions.hasExactAlarmAccess(context),
        )
    }

    // 지금 실제로 켜져 있는 VPN. 엔진이 알려 주는 값이라 목록의 활성 표시와 경고, `연결 해제` 활성화에 그대로 쓴다.
    val activeId by VpnEngine.active.collectAsState()
    val activeIds = remember(activeId) { setOfNotNull(activeId) }

    // 구성을 켜는 실제 동작 — 막히는 작업이라 IO 스레드에서 하고 결과만 화면에 반영한다.
    fun connect(config: VpnConfig) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { VpnController.manual(context.applicationContext, config, true) }
            reloadEvents()
            message = if (result == VpnEngine.Result.OK) null else "연결하지 못했습니다: ${result.label}"
        }
    }

    // VPN 권한 승인 후 이어서 켤 구성. 시스템 승인 화면을 거치는 동안 보관한다.
    var pendingConnect by remember { mutableStateOf<VpnConfig?>(null) }
    val requestVpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        refreshTick++
        val target = pendingConnect
        pendingConnect = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            connect(target)
        }
        else if (target != null) {
            message = "VPN 권한을 허용해야 연결할 수 있습니다"
        }
    }

    HomeScreen(
        settings = settings,
        permissionState = permissionState,
        lastEvents = lastEvents,
        message = message,
        activeIds = activeIds,
        onChange = { updated ->
            settings = updated
            pendingSave = updated
        },
        onActivate = { config ->
            // 대기 중인 변경을 먼저 저장해 기록과 맞춘다.
            flushPending()
            // VPN 연결은 처음 한 번 시스템 승인이 필요하다. 아직이면 승인 화면을 띄우고, 허용되면 이어서 켠다.
            val intent = VpnEngine.permissionIntent(context)
            if (intent != null) {
                pendingConnect = config
                requestVpnPermission.launch(intent)
            }
            else {
                // 다른 VPN 이 켜져 있으면 엔진이 먼저 끄고 켠다(경고에서 확인을 받은 뒤 들어온 요청이다).
                connect(config)
            }
        },
        onDeactivate = { config ->
            flushPending()
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    VpnController.manual(context.applicationContext, config, false)
                }
                reloadEvents()
                message = if (result == VpnEngine.Result.OK) null else "연결을 끊지 못했습니다: ${result.label}"
            }
        },
        onDisconnect = {
            flushPending()
            scope.launch {
                val (count, failed) = withContext(Dispatchers.IO) {
                    VpnController.disconnectActive(context.applicationContext)
                }
                reloadEvents()
                message = if (count > 0 && failed > 0) "연결을 끊지 못했습니다" else null
            }
        },
        onOpenSettings = {
            // 설정은 별도 Activity — 열고 닫는 전환 애니메이션과 뒤로가기 제스처는 시스템이 처리한다.
            flushPending()
            context.startActivity(Intent(context, SettingsActivity::class.java))
        },
    )
}
