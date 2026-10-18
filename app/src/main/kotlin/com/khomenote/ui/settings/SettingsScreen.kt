package com.khomenote.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khomenote.backup.BackupManager
import com.khomenote.ui.asset.SECTION_TITLE_COLOR
import com.khomenote.ui.asset.SECTION_TITLE_TEXT_COLOR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 설정 화면 — 기존 홈 화면의 "카테고리 관리" 진입점과 하단 백업/복원 버튼을 이쪽으로 모았다(생활달력 리디자인 1단계). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageCategories: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { BackupManager.backup(context, uri) } }
                    .onSuccess { Toast.makeText(context, "백업이 완료되었습니다", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "백업 실패: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("백업 파일로 복원할까요?") },
            text = { Text("현재 저장된 모든 데이터가 백업 시점 상태로 교체됩니다. 되돌릴 수 없고, 사진 파일은 복원되지 않습니다. 복원 후 앱이 다시 시작됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { BackupManager.restore(context, uri) } }
                            .onSuccess {
                                // 기존 리포지토리/DAO가 닫힌 연결을 들고 있으므로, 새로
                                // 뜨는 프로세스에서 깨끗하게 다시 열리도록 앱을 재시작한다.
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                                if (intent != null) context.startActivity(intent)
                                Process.killProcess(Process.myPid())
                            }
                            .onFailure { Toast.makeText(context, "복원 실패: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("복원") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("설정") }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SettingsSectionHeader("카테고리")
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onManageCategories)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("카테고리 관리")
                Text("›", color = MaterialTheme.colorScheme.outline)
            }
            HorizontalDivider()

            SettingsSectionHeader("백업")
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { backupLauncher.launch(BackupManager.backupFileName()) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text("백업 만들기")
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { restoreLauncher.launch(arrayOf("*/*")) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text("백업에서 복원")
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        color = SECTION_TITLE_TEXT_COLOR,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SECTION_TITLE_COLOR, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
