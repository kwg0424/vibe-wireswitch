package com.example.wireswitch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wireswitch.data.VpnConfig
import com.example.wireswitch.data.VpnSchedule
import com.example.wireswitch.data.VpnSettings
import com.example.wireswitch.data.VpnSettingsStore
import com.example.wireswitch.data.SecretStore
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.example.wireswitch.util.ConfExport
import com.example.wireswitch.util.ConfImport
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext

/** 설정 — 권한, 일정, 외부 자동화, 맨 아래 VPN 구성 추가·내보내기. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: VpnSettings,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onSave: (VpnSettings) -> Unit,
    onClose: () -> Unit,
) {
    // 자동 제어 전체 켜기/끄기 스위치는 없앴다. 항상 켜져 있고, 일정을 쓰고 싶지 않으면 일정을 두지 않거나 일시정지한다.
    // (이전에 꺼 둔 상태로 저장돼 있어도 여기서 켜진 상태로 되돌린다.)
    val enabled = true
    // VPN 구성의 목록·편집은 첫 화면에서 하고, 추가는 이 화면 맨 아래 버튼으로 한다.
    var configs by remember { mutableStateOf(initial.configs) }
    var automationEnabled by remember { mutableStateOf(initial.automationEnabled) }
    var automationTargetIds by remember { mutableStateOf(initial.automationTargetIds) }
    var schedules by remember { mutableStateOf(initial.schedules) }
    // 시간 선택 중인 (일정 id, 시작 여부). null 이면 다이얼로그가 닫힌 상태.
    var pickerTarget by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }

    // 변경할 때마다 자동 저장한다. 글자를 칠 때마다 연결 동작이 일어나지 않도록 잠깐 멈추면 저장한다.
    val current = VpnSettings(
        enabled = enabled,
        configs = configs,
        automationEnabled = automationEnabled,
        automationTargetIds = automationTargetIds,
        schedules = schedules,
    )
    var lastSaved by remember { mutableStateOf(initial) }
    val latest by rememberUpdatedState(current)
    LaunchedEffect(current) {
        if (current != lastSaved) {
            delay(600)
            onSave(current)
            lastSaved = current
        }
    }
    // 대기 중에 화면을 닫거나 뒤로 가도 변경이 사라지지 않게 마지막으로 한 번 저장한다.
    // 설정이 별도 Activity 라서, 첫 화면이 다시 보이기 전(onPause)에 저장해 두어야 첫 화면이 최신 값을 읽는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && latest != lastSaved) {
                onSave(latest)
                lastSaved = latest
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (latest != lastSaved) {
                onSave(latest)
            }
        }
    }

    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    // QR 로 읽은 설정 전문. 이름을 입력받아 추가하기 전까지 메모리에만 둔다.
    var scannedText by remember { mutableStateOf<String?>(null) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    // 구성이 추가되면 첫 화면으로 돌아가 바로 보이게 한다.
    var addedCount by remember { mutableStateOf(0) }
    LaunchedEffect(addedCount) {
        if (addedCount > 0) {
            onClose()
        }
    }

    // 설정 전문(개인키 포함)은 구성 목록에 두지 않고 암호화해 따로 저장한다. 이름이 겹치면 추가하지 않는다.
    fun addConfig(name: String, text: String): Boolean {
        if (configs.any { it.tunnelName.trim() == name }) {
            return false
        }
        val nextId = (configs.maxOfOrNull { it.id } ?: 0L) + 1
        SecretStore.save(context, nextId, text)
        configs = configs + VpnConfig(id = nextId, tunnelName = name, imported = true)
        return true
    }

    // 설정 파일(.conf) 또는 zip 가져오기 — 파일 선택기는 권한이 필요 없다. 파일명(.conf 제외)이 구성 이름이다.
    val pickConfFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val displayName = ConfImport.displayName(context, uri).orEmpty()

        // .zip — 안의 .conf 파일을 전부 구성으로 추가한다(WireGuard 앱의 내보내기 zip 등).
        if (displayName.endsWith(".zip", ignoreCase = true)) {
            val result = ConfImport.readZip(context, uri)
            if (result == null) {
                addMessage = "zip 파일을 읽지 못했습니다."
                return@rememberLauncherForActivityResult
            }
            var added = 0
            var duplicated = 0
            result.confs.forEach { conf ->
                if (conf.name.isNotEmpty() && addConfig(conf.name, conf.text)) added++ else duplicated++
            }
            if (added > 0) {
                addedCount++
            }
            else {
                addMessage = (if (result.confs.isEmpty()) "zip 안에 WireGuard 설정 파일(.conf)이 없습니다." else "이미 있는 구성이라 추가하지 않았습니다.") +
                    (if (result.invalid > 0) " 형식이 달라 건너뜀 ${result.invalid}개." else "")
            }
            return@rememberLauncherForActivityResult
        }

        val name = displayName.removeSuffix(".conf").trim()
        val text = ConfImport.readConf(context, uri)
        when {
            text == null -> addMessage = "WireGuard 설정 파일이 아니거나 읽을 수 없습니다."
            name.isEmpty() -> addMessage = "파일 이름을 읽지 못했습니다. 설정 내용을 붙여넣어 추가하세요."
            !addConfig(name, text) -> addMessage = "이미 '$name' 구성이 있습니다."
            else -> addedCount++
        }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text("설정", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                },
                actions = { TextButton(onClick = onClose) { Text("닫기") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 모두 허용돼 있으면 보여줄 게 없으니 숨기고, 부족할 때만 나타난다.
            if (!permissionState.allGranted) {
                PermissionCard(permissionState, true, onRequestPermission)
            }

            SectionCard {
                Text("일정", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "요일·시간대마다 켜고 끌 VPN 을 고릅니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    if (schedules.isEmpty()) {
                        Text("등록된 일정이 없습니다", style = MaterialTheme.typography.bodySmall, color = Amber)
                    }
                    schedules.forEachIndexed { index, schedule ->
                        if (index > 0) {
                            HorizontalDivider(color = Surface2)
                        }
                        ScheduleEditor(
                            schedule = schedule,
                            configs = configs,
                            enabled = enabled,
                            onChange = { updated ->
                                schedules = schedules.map { if (it.id == updated.id) updated else it }
                            },
                            onDelete = { schedules = schedules.filter { it.id != schedule.id } },
                            onPickTime = { isStart -> pickerTarget = schedule.id to isStart },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val nextId = (schedules.maxOfOrNull { it.id } ?: 0L) + 1
                            schedules = schedules + VpnSchedule(id = nextId, configId = configs.first().id)
                        },
                        enabled = enabled && configs.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (configs.isEmpty()) "+ 일정 추가 (먼저 아래에서 VPN 구성을 추가하세요)" else "+ 일정 추가") }
                }
            }

            Column(modifier = Modifier.alpha(if (enabled) 1f else 0.4f)) {
                AutomationCard(
                    enabled = enabled,
                    automationEnabled = automationEnabled,
                    configs = configs,
                    targetIds = automationTargetIds,
                    onToggle = { automationEnabled = it },
                    onTargetChange = { id, selected ->
                        // 외부 제어 대상은 하나만 — 고르면 이전 선택을 대체하고, 다시 누르면 해제한다.
                        automationTargetIds = if (selected) setOf(id) else emptySet()
                    },
                )
            }

            // 구성 추가는 일정과 무관하게 맨 아래에서 항상 할 수 있다. 추가된 구성은 첫 화면에 나타난다.
            addMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Amber, modifier = Modifier.padding(start = 4.dp))
            }
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("VPN 구성 추가") }
            OutlinedButton(
                onClick = {
                    addMessage = null
                    showExportDialog = true
                },
                enabled = configs.any { it.isConfigured },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("VPN 구성 내보내기") }
        }
    }

    // 내보내기 — 개인키가 든 원본이 다운로드 폴더에 평문으로 저장되므로 먼저 알리고 확인을 받는다.
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Surface1,
            title = { Text("VPN 구성 내보내기", color = TextPrimary) },
            text = {
                Text(
                    "구성 ${configs.count { it.isConfigured }}개를 zip 파일 하나로 다운로드 폴더에 저장합니다.\n\n" +
                        "파일에는 개인키가 암호화 없이 들어갑니다.\n안전하게 보관하고, 쓰고 나면 삭제하세요.",
                    color = TextPrimary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    val fileName = ConfExport.exportToDownloads(context, configs.filter { it.isConfigured })
                    addMessage = if (fileName != null) {
                        "다운로드 폴더에 저장했습니다: $fileName"
                    }
                    else {
                        "내보내지 못했습니다."
                    }
                }) { Text("내보내기") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("취소") } },
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Surface1,
            title = { Text("VPN 구성 추가", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showAddDialog = false
                            addMessage = null
                            pickConfFile.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("설정파일(conf/zip) 추가") }
                    Button(
                        onClick = {
                            showAddDialog = false
                            addMessage = null
                            // Google 코드 스캐너 화면이 뜨고 결과(QR 에 담긴 설정 문자열)만 돌아온다. 카메라 권한이 필요 없다.
                            val options = GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            GmsBarcodeScanning.getClient(context, options).startScan()
                                .addOnSuccessListener { barcode ->
                                    val text = barcode.rawValue
                                    if (text != null && ConfImport.isValid(text)) {
                                        scannedText = text
                                    }
                                    else {
                                        addMessage = "WireGuard 설정 QR 코드가 아닙니다."
                                    }
                                }
                                .addOnFailureListener {
                                    addMessage = "QR 스캔을 시작하지 못했습니다. Google Play 서비스를 확인하세요."
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("QR코드 추가") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("취소") } },
        )
    }

    // QR 에는 구성 이름이 없으므로 읽은 뒤 이름을 입력받아 추가한다.
    scannedText?.let { text ->
        var name by remember { mutableStateOf("") }
        var nameError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { scannedText = null },
            containerColor = Surface1,
            title = { Text("QR 코드를 읽었습니다", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("구성 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    nameError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val trimmed = name.trim()
                        if (addConfig(trimmed, text)) {
                            scannedText = null
                            addedCount++
                        }
                        else {
                            nameError = "이미 '$trimmed' 구성이 있습니다."
                        }
                    },
                ) { Text("추가") }
            },
            dismissButton = { TextButton(onClick = { scannedText = null }) { Text("취소") } },
        )
    }

    pickerTarget?.let { (scheduleId, isStart) ->
        val target = schedules.firstOrNull { it.id == scheduleId }
        if (target == null) {
            pickerTarget = null
        }
        else {
            TimePickerDialog(
                initialHour = if (isStart) target.startHour else target.endHour,
                initialMinute = if (isStart) target.startMinute else target.endMinute,
                onDismiss = { pickerTarget = null },
                onConfirm = { hour, minute ->
                    val updated = if (isStart) {
                        target.copy(startHour = hour, startMinute = minute)
                    }
                    else {
                        target.copy(endHour = hour, endMinute = minute)
                    }
                    schedules = schedules.map { if (it.id == scheduleId) updated else it }
                    pickerTarget = null
                },
            )
        }
    }
}

@Composable
private fun PermissionCard(permissionState: PermissionState, enabled: Boolean, onRequestPermission: () -> Unit) {
    SectionCard {
        Text("권한", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        if (permissionState.allGranted) {
            Text("모두 허용되었습니다", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        else {
            // 아직 안 된 항목만 보여준다.
            if (!permissionState.vpnGranted) {
                PermissionRow("VPN 연결", "필요함 (처음 한 번 시스템 승인)")
            }
            if (!permissionState.exactAlarmGranted) {
                PermissionRow("정확한 알람", "필요함 (없으면 시각이 늦어질 수 있음)")
            }
            Button(onClick = onRequestPermission, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("권한 요청")
            }
        }
    }
}

/** 일정이 켜고 끌 VPN 을 고르는 드롭다운. */
@Composable
private fun ConfigPicker(
    configs: List<VpnConfig>,
    selectedId: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = configs.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("VPN: ${selected?.title?.ifBlank { "(이름 없음)" } ?: "선택하세요"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            configs.forEach { config ->
                DropdownMenuItem(
                    text = { Text(config.title.ifBlank { "(이름 없음)" }) },
                    onClick = {
                        expanded = false
                        onSelect(config.id)
                    },
                )
            }
        }
    }
}

/**
 * 일정 하나의 편집 UI. 평소엔 요약(요일·시간대, 대상 VPN, 상태)과 일시정지 스위치만 보이고,
 * 항목을 누르면 대상 VPN 선택·요일 칩·시작/종료 시각·삭제 버튼이 펼쳐진다.
 */
@Composable
private fun ScheduleEditor(
    schedule: VpnSchedule,
    configs: List<VpnConfig>,
    enabled: Boolean,
    onChange: (VpnSchedule) -> Unit,
    onDelete: () -> Unit,
    onPickTime: (isStart: Boolean) -> Unit,
) {
    var expanded by remember(schedule.id) { mutableStateOf(false) }
    val configTitle = configs.firstOrNull { it.id == schedule.configId }?.title?.ifBlank { "(이름 없음)" } ?: "VPN 선택 필요"

    Column(
        modifier = Modifier.alpha(if (schedule.paused) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val daysPrefix = if (schedule.days.isEmpty()) "" else "${daysSummaryLabel(schedule.days)} "
                Text(
                    "$daysPrefix${formatTime(schedule.startHour, schedule.startMinute)} ~ " +
                        "${if (schedule.crossesMidnight) "다음날 " else ""}${formatTime(schedule.endHour, schedule.endMinute)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                Text(
                    "$configTitle · " + when {
                        schedule.paused -> "일시정지됨"
                        schedule.days.isEmpty() -> "요일을 선택하세요"
                        else -> "동작 중"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            if (expanded) {
                TextButton(onClick = onDelete, enabled = enabled) { Text("삭제", color = Danger) }
            }
            else {
                Switch(
                    checked = !schedule.paused,
                    onCheckedChange = { onChange(schedule.copy(paused = !it)) },
                    enabled = enabled,
                )
            }
        }
        if (expanded) {
            ConfigPicker(
                configs = configs,
                selectedId = schedule.configId,
                enabled = enabled,
                onSelect = { onChange(schedule.copy(configId = it)) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_ORDER.forEach { day ->
                    DayChip(
                        label = dayLabel(day),
                        selected = schedule.days.contains(day),
                        enabled = enabled,
                        onClick = {
                            val days = if (schedule.days.contains(day)) schedule.days - day else schedule.days + day
                            onChange(schedule.copy(days = days))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onPickTime(true) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("켜기 ${formatTime(schedule.startHour, schedule.startMinute)}") }
                OutlinedButton(
                    onClick = { onPickTime(false) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("끄기 ${formatTime(schedule.endHour, schedule.endMinute)}") }
            }
        }
    }
}

@Composable
private fun AutomationCard(
    enabled: Boolean,
    automationEnabled: Boolean,
    configs: List<VpnConfig>,
    targetIds: Set<Long>,
    onToggle: (Boolean) -> Unit,
    onTargetChange: (configId: Long, selected: Boolean) -> Unit,
) {
    SectionCard {
        LabeledRow(
            title = "외부 앱 제어",
            subtitle = if (automationEnabled) "다른 앱이 보낸 연결/해제 명령을 받습니다" else "꺼져 있으면 외부 명령을 모두 무시합니다",
        ) {
            Switch(checked = automationEnabled, onCheckedChange = onToggle, enabled = enabled)
        }
        if (automationEnabled) {
            HorizontalDivider(color = Surface2)
            val selectable = configs.filter { it.isConfigured }
            if (selectable.isEmpty()) {
                Text(
                    "연결 가능한 VPN 구성이 없습니다. 아래 `VPN 구성 추가`로 추가하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
            }
            selectable.forEach { config ->
                val selected = config.id in targetIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onTargetChange(config.id, !selected) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = enabled)
                    Text(
                        config.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, state: String) {
    LabeledRow(title = label, subtitle = state) {
        Dot(Amber)
    }
}
