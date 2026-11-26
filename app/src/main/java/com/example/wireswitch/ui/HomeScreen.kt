package com.example.wireswitch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import com.example.wireswitch.util.ConfImport
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.wireswitch.data.SecretStore
import com.example.wireswitch.data.VpnConfig
import com.example.wireswitch.data.VpnSchedule
import com.example.wireswitch.data.VpnSettings
import com.example.wireswitch.util.ScheduleMath
import java.time.ZonedDateTime

data class PermissionState(
    /** VPN 연결 권한(시스템 승인). 처음 연결할 때 한 번 받는다. */
    val vpnGranted: Boolean,
    val exactAlarmGranted: Boolean,
) {
    val allGranted: Boolean
        get() = vpnGranted && exactAlarmGranted
}

/**
 * 첫 화면 — 위에서부터 일정 요약(읽기 전용), VPN 구성 목록(한 줄씩), 하단 고정 `연결 해제`.
 * 항목을 누르면 그 VPN 을 켜고(다른 VPN 이 켜져 있으면 경고), 이미 켜진 항목을 누르면 끈다.
 * 우측 `⋮` 로 자세히 보기·편집을 한다.
 * 구성 추가·일정 편집·외부 제어는 우측 상단 설정에서 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: VpnSettings,
    permissionState: PermissionState,
    lastEvents: Map<Long, String>,
    message: String?,
    activeIds: Set<Long>,
    onChange: (VpnSettings) -> Unit,
    onActivate: (VpnConfig) -> Unit,
    onDeactivate: (VpnConfig) -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // 자세히 보는 구성 id, 다른 VPN 이 켜져 있어 경고를 띄운 대상 구성.
    val context = LocalContext.current
    var detailId by remember { mutableStateOf<Long?>(null) }
    var warnTarget by remember { mutableStateOf<VpnConfig?>(null) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "WireSwitch",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = { TextButton(onClick = onOpenSettings) { Text("설정") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = onDisconnect,
                enabled = activeIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text("연결 해제") }
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
            if (!permissionState.allGranted) {
                SectionCard {
                    Text("권한이 더 필요합니다", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Text(
                        "설정 화면의 권한 항목에서 허용해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
            }

            Text(
                "VPN 구성",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
            if (settings.configs.isEmpty()) {
                SectionCard {
                    Text("등록된 VPN 구성이 없습니다", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "설정 맨 아래의 `VPN 구성 추가`로 추가하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("설정 열기") }
                }
            }

            settings.configs.forEach { config ->
                ConfigRow(
                    config = config,
                    schedules = settings.schedulesOf(config.id),
                    active = config.id in activeIds,
                    onClick = {
                        when {
                            // 설정 파일이 없으면 켤 수 없으니 자세히 보기를 연다.
                            !config.isConfigured -> detailId = config.id
                            // 이미 켜져 있으면 다시 눌러 끈다.
                            config.id in activeIds -> onDeactivate(config)
                            // 다른 VPN 이 이미 켜져 있으면 바로 켜지 않고 경고한다.
                            activeIds.isNotEmpty() -> warnTarget = config
                            else -> onActivate(config)
                        }
                    },
                    onDetail = { detailId = config.id },
                )
            }
        }
    }

    warnTarget?.let { target ->
        val others = settings.configs.filter { it.id in activeIds && it.id != target.id }
        AlertDialog(
            onDismissRequest = { warnTarget = null },
            containerColor = Surface1,
            title = { Text("이미 켜져 있는 VPN 이 있습니다", color = Amber) },
            text = {
                Text(
                    "${others.joinToString { it.title }} 이(가) 활성화되어 있습니다.\n" +
                        "끄고 ${target.title} 을(를) 켤까요?",
                    color = TextPrimary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    warnTarget = null
                    onActivate(target)
                }) { Text("끄고 켜기") }
            },
            dismissButton = { TextButton(onClick = { warnTarget = null }) { Text("취소") } },
        )
    }

    detailId?.let { id ->
        val config = settings.configs.firstOrNull { it.id == id }
        if (config == null) {
            detailId = null
        }
        else {
            ConfigDetailDialog(
                config = config,
                settings = settings,
                lastEvent = lastEvents[config.id].orEmpty(),
                onChange = { updated ->
                    onChange(settings.copy(configs = settings.configs.map { if (it.id == updated.id) updated else it }))
                },
                onDelete = {
                    detailId = null
                    // 켜져 있는 구성을 지우면 VPN 이 켜진 채 남지 않도록 먼저 끈다.
                    if (config.id in activeIds) {
                        onDeactivate(config)
                    }
                    // 암호화해 둔 설정(개인키 포함)도 함께 지운다.
                    SecretStore.delete(context, config.id)
                    onChange(
                        settings.copy(
                            configs = settings.configs.filter { it.id != config.id },
                            // 같은 id 가 새 구성에 재사용돼도 외부 제어 대상·일정에 남지 않게 한다.
                            automationTargetIds = settings.automationTargetIds - config.id,
                            schedules = settings.schedules.filter { it.configId != config.id },
                        ),
                    )
                },
                onDismiss = { detailId = null },
            )
        }
    }
}

/** 일정 한 줄 표기 — `평일 07:00 ~ 17:00`, 자정을 넘으면 `다음날`, 일시정지면 `· 일시정지`. */
private fun scheduleLabel(schedule: VpnSchedule): String {
    val days = if (schedule.days.isEmpty()) "요일 없음" else daysSummaryLabel(schedule.days)
    val range = "${formatTime(schedule.startHour, schedule.startMinute)} ~ " +
        "${if (schedule.crossesMidnight) "다음날 " else ""}${formatTime(schedule.endHour, schedule.endMinute)}"
    return "$days $range" + if (schedule.paused) " · 일시정지" else ""
}

/** 이름 아래 한 줄에 이 개수까지만 보이고, 넘으면 `+n` 으로 알린다. */
private const val VISIBLE_SCHEDULES = 2

/**
 * 구성 한 줄 — 상태 점, 이름, 우측 `⋮`(자세히). 줄을 누르면 그 VPN 을 켜거나 끈다.
 * 이름 아래에 이 구성의 일정을 한 줄로 보여 준다(2건까지, 3건부터는 `+n`). 일정이 없으면 `일정 없음`.
 * 켜진 구성은 초록 테두리와 옅은 초록 바탕으로 표시한다.
 */
@Composable
private fun ConfigRow(
    config: VpnConfig,
    schedules: List<VpnSchedule>,
    active: Boolean,
    onClick: () -> Unit,
    onDetail: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (active) Teal.copy(alpha = 0.14f).compositeOver(Surface1) else Surface1,
        border = if (active) BorderStroke(2.dp, Teal) else null,
    ) {
        Row(
            // 세로를 넉넉하게 — 누르기 쉽도록 최소 높이를 주고 위아래 여백을 둔다.
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Dot(if (active) Teal else TextMuted)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.title.ifBlank { "(이름 없음)" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                )
                if (schedules.isEmpty()) {
                    Text(
                        "일정 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted.copy(alpha = 0.7f),
                    )
                }
                else {
                    val shown = schedules.take(VISIBLE_SCHEDULES)
                    val hidden = schedules.size - shown.size
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            // 숨은 일정이 있으면 `+n` 앞에도 쉼표를 둔다: `…, 평일 12:00 ~ 13:00, +1`
                            shown.joinToString(", ") { scheduleLabel(it) } + if (hidden > 0) "," else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // 숨은 일정이 있으면 줄 끝에 개수를 남긴다(줄어들지 않는다).
                        if (hidden > 0) {
                            Text("+$hidden", style = MaterialTheme.typography.bodySmall, color = TealSoft, maxLines = 1)
                        }
                    }
                }
            }
            IconButton(onClick = onDetail) {
                Text("⋮", fontSize = 22.sp, color = TextMuted)
            }
        }
    }
}

/** 자세히 보기 — 이름 편집(오른쪽 `저장`), 설정 파일. 하단은 삭제 / 닫기. */
@Composable
private fun ConfigDetailDialog(
    config: VpnConfig,
    settings: VpnSettings,
    lastEvent: String,
    onChange: (VpnConfig) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 어떤 설정인지 구분할 수 있도록 저장된 설정 파일을 보여준다. 개인키는 가려서 보인다.
    val confText = remember(config.id) {
        SecretStore.load(context, config.id)?.let { ConfImport.maskSecrets(it) }
    }

    // 입력은 임시값으로 들고 있다가 `저장`을 눌러야 구성에 반영한다.
    var nameDraft by remember(config.id) { mutableStateOf(config.tunnelName) }
    val nameTaken = settings.configs.any { it.id != config.id && it.tunnelName.trim() == nameDraft.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text(config.title.ifBlank { "(이름 없음)" }, color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!config.isConfigured || confText == null) {
                    Text(
                        "설정 파일이 없습니다. 삭제 후 다시 추가하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }
                // 라벨은 "설정 파일" 처럼 입력칸 위에 따로 둔다 — 입력칸 테두리와 `저장` 버튼의 높이/위치를 일치시키기 위함.
                // 바꾼 이름이 유효할 때만 저장할 수 있고, 저장 후에도 팝업은 열려 있다.
                Text("구성 이름", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        isError = nameTaken,
                        // 이름이 겹칠 때만 안내한다.
                        supportingText = if (nameTaken) {
                            { Text("이미 같은 이름의 구성이 있습니다") }
                        }
                        else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = nameDraft.isNotBlank() && !nameTaken && nameDraft.trim() != config.tunnelName.trim(),
                        onClick = { onChange(config.copy(tunnelName = nameDraft.trim())) },
                        // 입력칸(56dp)과 같은 높이로 맞춘다.
                        modifier = Modifier.height(56.dp),
                    ) { Text("저장") }
                }
                // 설정 파일 — 서버 주소, 허용 IP 등으로 어떤 VPN 인지 구분한다. 개인키 값은 가린다.
                // 줄이 길면 아래로 줄바꿈하지 않고 한 줄에서 `...` 로 줄인다.
                confText?.let {
                    Text("설정 파일", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Surface(shape = RoundedCornerShape(10.dp), color = Surface2) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                        ) {
                            it.lines().forEach { line ->
                                Text(
                                    // 빈 줄이 높이를 잃지 않도록 공백 하나를 둔다.
                                    line.ifEmpty { " " },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        // 이름 저장은 입력칸 오른쪽 버튼으로 하고, 아래는 삭제와 닫기만 둔다. 닫기는 저장하지 않은 입력을 버린다.
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) { Text("삭제", color = Danger) }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}
