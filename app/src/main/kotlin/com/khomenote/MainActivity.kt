package com.khomenote

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khomenote.di.AppContainer
import com.khomenote.notification.consumableItemIdFromIntent
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.getInstance(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* 거부해도 앱은 그대로 쓸 수 있다 — 이후 알림만 조용히 안 뜬다(NotificationHelper가 권한 체크 후 스킵) */ }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        consumableItemIdFromIntent(intent)?.let { itemId ->
                            backStack.add(Screen.ItemDetail(itemId))
                        }
                    }

                    AppRoot(container = container, backStack = backStack)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun AppRoot(container: AppContainer, backStack: SnapshotStateList<Screen>) {
    val current = backStack.last()

    fun push(screen: Screen) { backStack.add(screen) }
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (val screen = current) {
        is Screen.Home -> {
            val vm = viewModel<HomeViewModel>(
                factory = HomeViewModelFactory(
                    container.consumableItemRepository,
                    container.replacementRecordRepository,
                    container.photoStorage,
                ),
            )
            HomeScreen(
                viewModel = vm,
                onManageAssets = { push(Screen.AssetList) },
                onItemClick = { itemId -> push(Screen.ItemDetail(itemId)) },
            )
        }

        is Screen.AssetList -> {
            val vm = viewModel<AssetListViewModel>(
                factory = AssetListViewModelFactory(container.assetRepository),
            )
            AssetListScreen(
                viewModel = vm,
                onAddAsset = { push(Screen.AssetForm(null)) },
                onAssetClick = { assetId -> push(Screen.AssetDetail(assetId)) },
                onBack = { pop() },
            )
        }

        is Screen.AssetForm -> {
            val vm = viewModel<AssetFormViewModel>(
                factory = AssetFormViewModelFactory(container.assetRepository, screen.assetId),
                key = "asset-form-${System.identityHashCode(screen)}",
            )
            AssetFormScreen(viewModel = vm, isNew = screen.assetId == null, onSaved = { pop() }, onBack = { pop() })
        }

        is Screen.AssetDetail -> {
            val vm = viewModel<AssetDetailViewModel>(
                factory = AssetDetailViewModelFactory(
                    container.assetRepository,
                    container.consumableItemRepository,
                    screen.assetId,
                ),
            )
            AssetDetailScreen(
                viewModel = vm,
                onEditAsset = { push(Screen.AssetForm(screen.assetId)) },
                onAddItem = { push(Screen.ItemForm(screen.assetId, null)) },
                onItemClick = { itemId -> push(Screen.ItemDetail(itemId)) },
                onBack = { pop() },
            )
        }

        is Screen.ItemForm -> {
            val vm = viewModel<ItemFormViewModel>(
                factory = ItemFormViewModelFactory(container.consumableItemRepository, screen.assetId, screen.itemId),
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
                    screen.itemId,
                ),
            )
            ItemDetailScreen(
                viewModel = vm,
                onEditItem = { push(Screen.ItemForm(assetId = null, itemId = screen.itemId)) },
                onDeleted = { pop() },
                onBack = { pop() },
            )
        }
    }
}
