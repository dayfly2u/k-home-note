package com.khomenote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khomenote.di.AppContainer
import com.khomenote.notification.consumeConsumableItemIdFromIntent
import com.khomenote.notification.consumeOneTimeTaskAssetIdFromIntent
import com.khomenote.ui.asset.AssetDetailScreen
import com.khomenote.ui.asset.AssetDetailViewModel
import com.khomenote.ui.asset.AssetDetailViewModelFactory
import com.khomenote.ui.asset.AssetFormScreen
import com.khomenote.ui.asset.AssetFormViewModel
import com.khomenote.ui.asset.AssetFormViewModelFactory
import com.khomenote.ui.asset.AssetListScreen
import com.khomenote.ui.asset.AssetListViewModel
import com.khomenote.ui.asset.AssetListViewModelFactory
import com.khomenote.ui.home.HomeScreen
import com.khomenote.ui.home.HomeViewModel
import com.khomenote.ui.home.HomeViewModelFactory
import com.khomenote.ui.item.ItemDetailScreen
import com.khomenote.ui.item.ItemDetailViewModel
import com.khomenote.ui.item.ItemDetailViewModelFactory
import com.khomenote.ui.item.ItemFormScreen
import com.khomenote.ui.item.ItemFormViewModel
import com.khomenote.ui.item.ItemFormViewModelFactory
import com.khomenote.ui.navigation.Screen
import com.khomenote.ui.settings.SettingsScreen
import com.khomenote.ui.settings.SettingsViewModel
import com.khomenote.ui.settings.SettingsViewModelFactory
import com.khomenote.ui.theme.KHomeNoteTheme

class MainActivity : ComponentActivity() {

    // onNewIntent(싱글태스크 웜스타트 딥링크)에서도 접근할 수 있어야 하므로
    // setContent{} 안의 remember{}가 아니라 Activity 클래스 프로퍼티로 둔다.
    private val backStack = mutableStateListOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.getInstance(applicationContext)
        setContent {
            KHomeNoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* 거부해도 앱은 그대로 쓸 수 있다 — 이후 알림만 조용히 안 뜬다(NotificationHelper가 권한 체크 후 스킵) */ }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        consumeConsumableItemIdFromIntent(intent)?.let { itemId ->
                            backStack.add(Screen.ItemDetail(itemId))
                        }
                        consumeOneTimeTaskAssetIdFromIntent(intent)?.let { assetId ->
                            backStack.add(Screen.AssetDetail(assetId))
                        }
                    }

                    AppRoot(container = container, backStack = backStack, window = window)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeConsumableItemIdFromIntent(intent)?.let { itemId ->
            backStack.add(Screen.ItemDetail(itemId))
        }
        consumeOneTimeTaskAssetIdFromIntent(intent)?.let { assetId ->
            backStack.add(Screen.AssetDetail(assetId))
        }
    }
}

// 화면을 뒤로가기로 떠나도 viewModel()이 기본으로 잡는 액티비티 전역 스토어에 그 화면의
// ViewModel이 절대 onCleared()되지 않고 계속 쌓인다 — golf-round-tracker에서 이게 실제
// FK 위반 크래시로 이어진 적이 있다(지워진 항목의 Flow를 여전히 구독하던 낡은 ViewModel이
// 되살아나 다시 write를 시도함). 화면 인스턴스별로 전용 ViewModelStore를 두고, 그 화면이
// 백스택에서 pop되는 바로 그 지점에서 clear()해서 "화면이 사라지는 순간 == VM이 정리되는
// 순간"을 보장한다. lightweight-navigation skill 참고.
private class ScreenViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

// 하단 탭 3개 — 탭을 바꾸면 그 탭의 루트 화면 하나만 남도록 백스택을 리셋한다
// (예: AssetDetail까지 들어간 상태에서 "홈" 탭을 누르면 그 중간 화면들은 다 버려진다).
private val TAB_ROOTS = listOf(Screen.Home, Screen.AssetList, Screen.Settings)

@Composable
private fun AppRoot(container: AppContainer, backStack: SnapshotStateList<Screen>, window: android.view.Window) {
    val current = backStack.last()
    val viewModelStoreOwners = remember { java.util.IdentityHashMap<Screen, ScreenViewModelStoreOwner>() }

    // 홈은 청록 헤더가 상태바 뒤까지 덮으므로 흰 상태바 아이콘이, 나머지 화면은 밝은 바탕이므로 어두운 아이콘이 보여야 한다.
    // targetSdk 36에서는 시스템이 상태바 바탕을 투명하게 두기 때문에 앱이 직접 아이콘 색을 정해줘야 한다.
    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = current != Screen.Home
    }

    fun push(screen: Screen) { backStack.add(screen) }
    fun pop() {
        if (backStack.size > 1) {
            val removed = backStack.removeAt(backStack.lastIndex)
            viewModelStoreOwners.remove(removed)?.viewModelStore?.clear()
        }
    }
    fun switchTab(root: Screen) {
        if (backStack.size == 1 && backStack.first() == root) return
        while (backStack.isNotEmpty()) {
            val removed = backStack.removeAt(backStack.lastIndex)
            viewModelStoreOwners.remove(removed)?.viewModelStore?.clear()
        }
        backStack.add(root)
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    Scaffold(
        bottomBar = {
            if (backStack.size == 1 && current in TAB_ROOTS) {
                NavigationBar {
                    NavigationBarItem(
                        selected = current == Screen.Home,
                        onClick = { switchTab(Screen.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("홈") },
                    )
                    NavigationBarItem(
                        selected = current == Screen.AssetList,
                        onClick = { switchTab(Screen.AssetList) },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("모아보기") },
                    )
                    NavigationBarItem(
                        selected = current == Screen.Settings,
                        onClick = { switchTab(Screen.Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("설정") },
                    )
                }
            }
        },
    ) { padding ->
    val owner = viewModelStoreOwners.getOrPut(current) { ScreenViewModelStoreOwner() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        // 홈은 위쪽 패딩(상태바 높이)을 빼고 HomeHeader가 직접 상태바 영역까지 칠한다.
        val contentPadding = if (current == Screen.Home) PaddingValues(bottom = padding.calculateBottomPadding()) else padding
        Box(modifier = Modifier.padding(contentPadding)) {
        when (val screen = current) {
            is Screen.Home -> {
                val vm = viewModel<HomeViewModel>(
                    factory = HomeViewModelFactory(
                        container.consumableItemRepository,
                        container.repairRecordRepository,
                        container.categoryRepository,
                        container.assetRepository,
                    ),
                )
                HomeScreen(
                    viewModel = vm,
                    onItemClick = { itemId -> push(Screen.ItemDetail(itemId)) },
                    onOneTimeTaskClick = { assetId -> push(Screen.AssetDetail(assetId)) },
                    onAddAsset = { push(Screen.AssetForm(null)) },
                    onAddRecurringItem = { assetId -> push(Screen.ItemForm(assetId, null)) },
                    onAddOneTimeTask = { assetId -> push(Screen.AssetDetail(assetId, openOneTimeForm = true)) },
                    onConvertToRecurring = { assetId, name -> push(Screen.ItemForm(assetId, null, name)) },
                )
            }

            is Screen.AssetList -> {
                val vm = viewModel<AssetListViewModel>(
                    factory = AssetListViewModelFactory(
                        container.assetRepository,
                        container.consumableItemRepository,
                        container.categoryRepository,
                    ),
                )
                AssetListScreen(
                    viewModel = vm,
                    onAddAsset = { push(Screen.AssetForm(null)) },
                    onAssetClick = { assetId -> push(Screen.AssetDetail(assetId)) },
                    onEditAsset = { assetId -> push(Screen.AssetForm(assetId)) },
                )
            }

            is Screen.Settings -> {
                val vm = viewModel<SettingsViewModel>(
                    factory = SettingsViewModelFactory(container.categoryRepository, container.assetRepository),
                )
                SettingsScreen(viewModel = vm)
            }

            is Screen.AssetForm -> {
                val vm = viewModel<AssetFormViewModel>(
                    factory = AssetFormViewModelFactory(container.assetRepository, container.categoryRepository, screen.assetId),
                    key = "asset-form-${System.identityHashCode(screen)}",
                )
                AssetFormScreen(viewModel = vm, isNew = screen.assetId == null, onSaved = { pop() }, onBack = { pop() })
            }

            is Screen.AssetDetail -> {
                val vm = viewModel<AssetDetailViewModel>(
                    factory = AssetDetailViewModelFactory(
                        container.assetRepository,
                        container.consumableItemRepository,
                        container.repairRecordRepository,
                        container.categoryRepository,
                        screen.assetId,
                    ),
                    key = "asset-detail-${screen.assetId}",
                )
                AssetDetailScreen(
                    viewModel = vm,
                    onAddItem = { push(Screen.ItemForm(screen.assetId, null)) },
                    onItemClick = { itemId -> push(Screen.ItemDetail(itemId)) },
                    onEditItem = { itemId -> push(Screen.ItemForm(assetId = null, itemId = itemId)) },
                    onEditAsset = { push(Screen.AssetForm(screen.assetId)) },
                    onConvertToRecurring = { name -> push(Screen.ItemForm(screen.assetId, null, name)) },
                    onBack = { pop() },
                    startWithOneTimeForm = screen.openOneTimeForm,
                )
            }

            is Screen.ItemForm -> {
                val vm = viewModel<ItemFormViewModel>(
                    factory = ItemFormViewModelFactory(
                        container.consumableItemRepository,
                        container.assetRepository,
                        container.categoryRepository,
                        screen.assetId,
                        screen.itemId,
                        screen.presetName,
                    ),
                    key = "item-form-${System.identityHashCode(screen)}",
                )
                ItemFormScreen(viewModel = vm, isNew = screen.itemId == null, onSaved = { pop() }, onBack = { pop() })
            }

            is Screen.ItemDetail -> {
                val vm = viewModel<ItemDetailViewModel>(
                    factory = ItemDetailViewModelFactory(
                        container.consumableItemRepository,
                        container.replacementRecordRepository,
                        container.photoStorage,
                        container.categoryRepository,
                        screen.itemId,
                    ),
                    key = "item-detail-${screen.itemId}",
                )
                ItemDetailScreen(
                    viewModel = vm,
                    onEditItem = { push(Screen.ItemForm(assetId = null, itemId = screen.itemId)) },
                    onBack = { pop() },
                )
            }
        }
        }
    }
    }
}
