package com.example.wireswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wireswitch.data.VpnSettingsStore
import com.example.wireswitch.ui.PermissionState
import com.example.wireswitch.ui.SettingsScreen
import com.example.wireswitch.ui.WireSwitchTheme
import com.example.wireswitch.util.Permissions
import com.example.wireswitch.work.SettingsPersistence
import com.example.wireswitch.work.VpnEngine

/**
 * 설정 화면. 첫 화면에서 별도 Activity 로 열어, 열고 닫는 슬라이드 전환과 뒤로가기 제스처 애니메이션을
 * 직접 구현하지 않고 안드로이드 시스템(예측형 뒤로가기 포함)에 맡긴다.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WireSwitchTheme { SettingsRoot(onClose = { finish() }) }
        }
    }
}

@Composable
private fun SettingsRoot(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { VpnSettingsStore(context).load() }
    var refreshTick by remember { mutableStateOf(0) }

    // 권한은 시스템 화면을 거쳐 바뀔 수 있어 화면이 다시 보일 때마다 새로 읽는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
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

    val requestVpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshTick++ }

    SettingsScreen(
        initial = settings,
        permissionState = permissionState,
        onRequestPermission = {
            // 한 번에 하나씩 — 시스템 화면이 겹쳐 뜨는 걸 피하려고 우선순위대로 처리한다.
            val intent = VpnEngine.permissionIntent(context)
            when {
                intent != null -> requestVpnPermission.launch(intent)
                !permissionState.exactAlarmGranted ->
                    context.startActivity(Permissions.exactAlarmSettingsIntent(context))
            }
        },
        onSave = { updated -> SettingsPersistence.save(context, updated) },
        onClose = onClose,
    )
}
