package com.durrr.first

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.durrr.first.data.repo.SettingsRepository
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Icon
import com.durrr.first.features.cart.presentation.OrderBuilderScreen
import com.durrr.first.features.cashclosing.presentation.CashClosingScreen
import com.durrr.first.features.cashflow.presentation.CashFlowScreen
import com.durrr.first.features.orders.presentation.OrdersScreen
import com.durrr.first.features.product.presentation.ModifierGroupScreen
import com.durrr.first.features.product.presentation.ProductScreen
import com.durrr.first.features.product.presentation.ProductCategoryScreen
import com.durrr.first.features.product.presentation.ProductEditorScreen
import com.durrr.first.features.recommendation.presentation.RecommendationScreen
import com.durrr.first.features.recap.presentation.RecapScreen
import com.durrr.first.features.settings.presentation.SettingsScreen
import com.durrr.first.features.stock.presentation.StockScreen
import com.durrr.first.features.transaction.presentation.OrderCheckoutScreen
import com.durrr.first.features.transaction.presentation.ReceiptPreviewScreen
import com.durrr.first.features.transaction.presentation.TransactionHistoryScreen
import com.durrr.first.ui.AppDependencies
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.notification.AppNotification
import com.durrr.first.ui.notification.AppNotificationLevel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class MainRoute(val route: String, val title: String, val iconRes: Int) {
    RECAP("recap", "Dashboard", R.drawable.nav_dashboard),
    ORDERS("orders", "Orders", R.drawable.nav_orders),
    MENU("menu", "Produk", R.drawable.nav_produk),
    SETTINGS("settings", "Pengaturan", R.drawable.nav_settings),
}

private const val CASHFLOW_ROUTE = "cashflow"
private const val STOCK_ROUTE = "stock"
private const val CASH_CLOSING_ROUTE = "cashClosing"
private const val ORDER_BUILDER_ROUTE = "orderBuilder"
private const val ORDER_CHECKOUT_ROUTE = "orderCheckout/{draftId}"
private const val RECEIPT_PREVIEW_ROUTE = "receiptPreview/{transaksiId}"
private const val PRODUCT_EDITOR_ROUTE = "productEditor/{itemId}"
private const val PRODUCT_CATEGORY_ROUTE = "productCategories"
private const val MODIFIER_GROUP_ROUTE = "modifierGroups"
private const val RECOMMENDATION_ROUTE = "recommendation"
private const val TRANSACTION_HISTORY_ROUTE = "transactionHistory"

private val SidebarBlue = Color(0xFF5262BE)
private val SidebarActive = Color(0xFF21A5DF)
private val NotificationWarning = Color(0xFFB7791F)
private val NotificationError = Color(0xFFC53030)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAppScaffold(
    dependencies: AppDependencies,
    viewModel: MainViewModel,
    onRequireLocalSetup: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentDestinationRoute = currentDestination?.route
    var notificationPanelOpen by remember { mutableStateOf(false) }

    val currentRoute = MainRoute.values().firstOrNull { it.route == currentDestinationRoute }
    val isMainRoute = currentRoute != null
    val activeSession = dependencies.settingsRepository.getActiveUserSession()
    val isOwner = activeSession?.role == SettingsRepository.ROLE_OWNER
    val currentEditorItemId = navBackStackEntry?.arguments?.getString("itemId")
    val topBarTitle = when {
        currentDestinationRoute == CASHFLOW_ROUTE -> "Arus Kas"
        currentDestinationRoute == STOCK_ROUTE -> "Kasir"
        currentDestinationRoute == CASH_CLOSING_ROUTE -> "Cash Closing"
        currentDestinationRoute == ORDER_BUILDER_ROUTE -> "Kasir"
        currentDestinationRoute?.startsWith("orderCheckout/") == true -> "Pembayaran"
        currentDestinationRoute?.contains("receiptPreview") == true -> "Receipt Preview"
        currentDestinationRoute == PRODUCT_CATEGORY_ROUTE -> "Kategori Produk"
        currentDestinationRoute == MODIFIER_GROUP_ROUTE -> "Modifier Group"
        currentDestinationRoute == PRODUCT_EDITOR_ROUTE -> if (currentEditorItemId == "new") "Tambah Barang" else "Edit Barang"
        currentDestinationRoute == RECOMMENDATION_ROUTE -> "Rekomendasi"
        currentDestinationRoute == TRANSACTION_HISTORY_ROUTE -> "Riwayat Transaksi"
        else -> currentRoute?.title ?: "SuCash"
    }

    val navigateToMainRoute: (MainRoute) -> Unit = { route ->
        if (currentDestination?.hierarchy?.any { it.route == route.route } != true) {
            navController.navigate(route.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    val drawerContent: @Composable () -> Unit = {
        SidebarContent(
            currentDestination = currentDestination,
            currentDestinationRoute = currentDestinationRoute,
            onNavigateMain = { route ->
                navigateToMainRoute(route)
                scope.launch { drawerState.close() }
            },
            onNavigateExtra = { route ->
                navController.navigate(route)
                scope.launch { drawerState.close() }
            },
            isOwner = isOwner,
        )
    }

    val appBody: @Composable () -> Unit = {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("\u2630", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        if (!isMainRoute) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Text("<", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                val density = LocalDensity.current
                val fabSizePx = with(density) { 46.dp.toPx() }
                val edgePaddingPx = with(density) { 12.dp.toPx() }
                val panelGapPx = with(density) { 10.dp.toPx() }
                val panelWidthEstimatePx = with(density) { 320.dp.toPx() }
                val panelHeightEstimatePx = with(density) { 360.dp.toPx() }
                val contentWidthPx = constraints.maxWidth.toFloat()
                val contentHeightPx = constraints.maxHeight.toFloat()
                val minFabX = edgePaddingPx
                val minFabY = edgePaddingPx
                val maxFabX = (contentWidthPx - fabSizePx - edgePaddingPx).coerceAtLeast(minFabX)
                val maxFabY = (contentHeightPx - fabSizePx - edgePaddingPx).coerceAtLeast(minFabY)
                var notificationFabOffset by remember { mutableStateOf(Offset.Zero) }
                var notificationFabInitialized by remember { mutableStateOf(false) }

                LaunchedEffect(maxFabX, maxFabY, minFabX, minFabY) {
                    if (!notificationFabInitialized) {
                        notificationFabOffset = Offset(maxFabX, minFabY)
                        notificationFabInitialized = true
                    } else {
                        notificationFabOffset = Offset(
                            x = notificationFabOffset.x.coerceIn(minFabX, maxFabX),
                            y = notificationFabOffset.y.coerceIn(minFabY, maxFabY),
                        )
                    }
                }

                val panelX = (
                    notificationFabOffset.x + fabSizePx - panelWidthEstimatePx
                    ).coerceIn(
                    edgePaddingPx,
                    (contentWidthPx - panelWidthEstimatePx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
                )
                val panelYBelowFab = notificationFabOffset.y + fabSizePx + panelGapPx
                val panelY = if (panelYBelowFab + panelHeightEstimatePx > contentHeightPx) {
                    (notificationFabOffset.y - panelHeightEstimatePx - panelGapPx).coerceAtLeast(edgePaddingPx)
                } else {
                    panelYBelowFab
                }

                NavHost(
                    navController = navController,
                    startDestination = MainRoute.ORDERS.route,
                ) {
                    composable(MainRoute.ORDERS.route) {
                        OrdersScreen(
                            orderRepository = dependencies.orderCacheRepository,
                            orderSyncRepository = dependencies.orderSyncRepository,
                            settingsRepository = dependencies.settingsRepository,
                            onNotify = { title, message, level ->
                                viewModel.pushNotification(title, message, level)
                            },
                            onCreateWalkInOrder = { navController.navigate(ORDER_BUILDER_ROUTE) },
                        )
                    }
                    composable(ORDER_BUILDER_ROUTE) {
                        OrderBuilderScreen(
                            menuRepository = dependencies.menuRepository,
                            menuSyncRepository = dependencies.menuSyncRepository,
                            settingsRepository = dependencies.settingsRepository,
                            launchScanner = dependencies.launchScanner,
                            scannedToken = viewModel.lastScannedToken,
                            onScannedTokenConsumed = viewModel::clearScannedToken,
                            onProceedToCheckout = { draftId -> navController.navigate("orderCheckout/$draftId") },
                        )
                    }
                    composable(
                        route = ORDER_CHECKOUT_ROUTE,
                        arguments = listOf(navArgument("draftId") { type = NavType.StringType }),
                    ) { entry ->
                        val draftId = entry.arguments?.getString("draftId").orEmpty()
                        OrderCheckoutScreen(
                            draftId = draftId,
                            menuRepository = dependencies.menuRepository,
                            transaksiRepository = dependencies.transaksiRepository,
                            settingsRepository = dependencies.settingsRepository,
                            transaksiSyncRepository = dependencies.transaksiSyncRepository,
                            nowIso = dependencies.nowIso,
                            onBackToOrders = { navController.popBackStack(MainRoute.ORDERS.route, false) },
                            onPreviewReceipt = { transaksiId -> navController.navigate("receiptPreview/$transaksiId") },
                        )
                    }
                    composable(MainRoute.MENU.route) {
                        ProductScreen(
                            repo = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                            menuSyncRepository = dependencies.menuSyncRepository,
                            canManageCatalog = isOwner,
                            onAddProduct = { navController.navigate("productEditor/new") },
                            onManageCategories = { navController.navigate(PRODUCT_CATEGORY_ROUTE) },
                            onManageModifiers = { navController.navigate(MODIFIER_GROUP_ROUTE) },
                            onEditProduct = { itemId -> navController.navigate("productEditor/$itemId") },
                        )
                    }
                    composable(
                        route = PRODUCT_EDITOR_ROUTE,
                        arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                    ) { entry ->
                        val rawItemId = entry.arguments?.getString("itemId")
                        if (isOwner) {
                            ProductEditorScreen(
                                repo = dependencies.menuRepository,
                                settingsRepository = dependencies.settingsRepository,
                                menuSyncRepository = dependencies.menuSyncRepository,
                                pickImage = dependencies.pickImage,
                                uploadMenuImage = dependencies.uploadMenuImage,
                                itemId = rawItemId?.takeUnless { it == "new" },
                                onManageModifiers = { navController.navigate(MODIFIER_GROUP_ROUTE) },
                                onSaved = { navController.popBackStack() },
                            )
                        } else {
                            AccessDeniedScreen(
                                title = "Owner Only",
                                message = "Hanya owner yang bisa mengubah katalog produk.",
                            )
                        }
                    }
                    composable(PRODUCT_CATEGORY_ROUTE) {
                        if (isOwner) {
                            ProductCategoryScreen(
                                repo = dependencies.menuRepository,
                                settingsRepository = dependencies.settingsRepository,
                            )
                        } else {
                            AccessDeniedScreen(
                                title = "Owner Only",
                                message = "Hanya owner yang bisa mengubah kategori produk.",
                            )
                        }
                    }
                    composable(MODIFIER_GROUP_ROUTE) {
                        if (isOwner) {
                            ModifierGroupScreen(
                                repo = dependencies.menuRepository,
                                settingsRepository = dependencies.settingsRepository,
                            )
                        } else {
                            AccessDeniedScreen(
                                title = "Owner Only",
                                message = "Hanya owner yang bisa mengubah modifier group.",
                            )
                        }
                    }
                    composable(MainRoute.RECAP.route) {
                        RecapScreen(
                            recapRepository = dependencies.recapRepository,
                            cashFlowRepository = dependencies.cashFlowRepository,
                            recapSyncRepository = dependencies.recapSyncRepository,
                            settingsRepository = dependencies.settingsRepository,
                            todayDate = dependencies.todayDate,
                            onNotify = { title, message, level ->
                                viewModel.pushNotification(title, message, level)
                            },
                            onOpenCashFlow = { navController.navigate(CASHFLOW_ROUTE) },
//                            onOpenStock = { navController.navigate(STOCK_ROUTE) },
                            onOpenCashClosing = { navController.navigate(CASH_CLOSING_ROUTE) },
                            onOpenTransactionHistory = { navController.navigate(TRANSACTION_HISTORY_ROUTE) },
                        )
                    }
                    composable(MainRoute.SETTINGS.route) {
                        SettingsScreen(
                            settingsRepository = dependencies.settingsRepository,
                            pickImage = dependencies.pickImage,
                            menuRepository = dependencies.menuRepository,
                            uploadMenuImage = dependencies.uploadMenuImage,
                            menuSyncRepository = dependencies.menuSyncRepository,
                            orderSyncRepository = dependencies.orderSyncRepository,
                            transaksiSyncRepository = dependencies.transaksiSyncRepository,
                            serverAuthRepository = dependencies.serverAuthRepository,
                            isOwnerSession = isOwner,
                            onNotify = { title, message, level ->
                                viewModel.pushNotification(title, message, level)
                            },
                            onRequireLocalSetup = onRequireLocalSetup,
                            onLogout = onLogout,
                            onOpenCashFlow = { navController.navigate(CASHFLOW_ROUTE) },
                            // onOpenStock = { navController.navigate(STOCK_ROUTE) },
                            onOpenCashClosing = { navController.navigate(CASH_CLOSING_ROUTE) },
                        )
                    }
                    composable(CASHFLOW_ROUTE) {
                        CashFlowScreen(
                            repository = dependencies.cashFlowRepository,
                            cashSessionRepository = dependencies.cashSessionRepository,
                            settingsRepository = dependencies.settingsRepository,
                            todayDate = dependencies.todayDate,
                            nowIso = dependencies.nowIso,
                            onOpenDashboard = { navController.navigate(MainRoute.RECAP.route) },
                        )
                    }
                    composable(STOCK_ROUTE) {
                        StockScreen(
                            stockRepository = dependencies.stockRepository,
                            menuRepository = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                            nowIso = dependencies.nowIso,
                        )
                    }
                    composable(CASH_CLOSING_ROUTE) {
                        CashClosingScreen(
                            cashSessionRepository = dependencies.cashSessionRepository,
                            settingsRepository = dependencies.settingsRepository,
                            nowIso = dependencies.nowIso,
                        )
                    }
                    composable(TRANSACTION_HISTORY_ROUTE) {
                        TransactionHistoryScreen(
                            transaksiRepository = dependencies.transaksiRepository,
                            settingsRepository = dependencies.settingsRepository,
                            onOpenReceipt = { transaksiId ->
                                navController.navigate("receiptPreview/$transaksiId")
                            },
                        )
                    }
                    composable(
                        route = RECEIPT_PREVIEW_ROUTE,
                        arguments = listOf(navArgument("transaksiId") { type = NavType.StringType }),
                    ) { entry ->
                        val transaksiId = entry.arguments?.getString("transaksiId").orEmpty()
                        ReceiptPreviewScreen(
                            transaksiId = transaksiId,
                            receiptRepository = dependencies.receiptRepository,
                            settingsRepository = dependencies.settingsRepository,
                            onBack = { navController.popBackStack() },
                            onShareReceipt = dependencies.shareText,
                            onPrintReceipt = dependencies.printHtml,
                        )
                    }
                    composable(RECOMMENDATION_ROUTE) {
                        if (isOwner) {
                            RecommendationScreen(
                                menuRepository = dependencies.menuRepository,
                                settingsRepository = dependencies.settingsRepository,
                                menuSyncRepository = dependencies.menuSyncRepository,
                                pickDate = dependencies.pickDate,
                                pickImage = dependencies.pickImage,
                            )
                        } else {
                            AccessDeniedScreen(
                                title = "Owner Only",
                                message = "Rekomendasi bundle/promo hanya untuk owner.",
                            )
                        }
                    }
                }

                NotificationFloatingButton(
                    unreadCount = viewModel.unreadNotificationCount(),
                    onClick = {
                        notificationPanelOpen = !notificationPanelOpen
                        if (notificationPanelOpen) {
                            viewModel.markAllNotificationsRead()
                        }
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = notificationFabOffset.x.roundToInt(),
                                y = notificationFabOffset.y.roundToInt(),
                            )
                        }
                        .pointerInput(minFabX, maxFabX, minFabY, maxFabY) {
                            detectDragGestures { change, dragAmount ->
                                change.consumeAllChanges()
                                notificationFabOffset = Offset(
                                    x = (notificationFabOffset.x + dragAmount.x).coerceIn(minFabX, maxFabX),
                                    y = (notificationFabOffset.y + dragAmount.y).coerceIn(minFabY, maxFabY),
                                )
                            }
                        },
                )

                if (notificationPanelOpen) {
                    NotificationPanel(
                        notifications = viewModel.notifications,
                        onClear = { viewModel.clearNotifications() },
                        onClose = { notificationPanelOpen = false },
                        modifier = Modifier
                            .offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) },
                    )
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(196.dp),
                drawerContainerColor = Color.Transparent,
            ) {
                drawerContent()
            }
        },
        content = { appBody() },
    )
}

@Composable
private fun NotificationFloatingButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.size(46.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications_material),
                contentDescription = "Notifikasi",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            if (unreadCount > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPanel(
    notifications: List<AppNotification>,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .widthIn(min = 260.dp, max = 360.dp)
            .fillMaxWidth(0.9f),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Notifikasi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clickable(onClick = onClear),
                    )
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clickable(onClick = onClose),
                    )
                }
            }

            if (notifications.isEmpty()) {
                Text(
                    text = "Belum ada notifikasi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    notifications.forEach { notification ->
                        NotificationItem(notification = notification)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(notification: AppNotification) {
    val levelColor = when (notification.level) {
        AppNotificationLevel.INFO -> MaterialTheme.colorScheme.primary
        AppNotificationLevel.WARNING -> NotificationWarning
        AppNotificationLevel.ERROR -> NotificationError
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notification.title,
                fontWeight = FontWeight.SemiBold,
                color = levelColor,
            )
            Text(
                text = notificationTimeLabel(notification.createdAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = notification.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun notificationTimeLabel(millis: Long): String {
    return runCatching {
        val formatter = SimpleDateFormat("dd-MM-yy HH:mm", Locale.US)
        formatter.format(Date(millis))
    }.getOrElse { "-" }
}

@Composable
private fun SidebarContent(
    currentDestination: androidx.navigation.NavDestination?,
    currentDestinationRoute: String?,
    onNavigateMain: (MainRoute) -> Unit,
    onNavigateExtra: (String) -> Unit,
    isOwner: Boolean,
    modifier: Modifier = Modifier,
) {
    val isProductDestination =
        currentDestination?.hierarchy?.any { it.route == MainRoute.MENU.route } == true ||
            currentDestinationRoute == PRODUCT_EDITOR_ROUTE ||
            currentDestinationRoute == PRODUCT_CATEGORY_ROUTE ||
            currentDestinationRoute == MODIFIER_GROUP_ROUTE

    Column(
        modifier = modifier
            .width(188.dp)
            .fillMaxHeight()
            .background(SidebarBlue)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.92f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.nav_kasir),
                    contentDescription = "SuCash",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SuCash",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Quick Navigation",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SidebarEntry(
                label = "Dashboard",
                iconRes = R.drawable.nav_dashboard,
                selected = currentDestination?.hierarchy?.any { it.route == MainRoute.RECAP.route } == true,
                onClick = { onNavigateMain(MainRoute.RECAP) },
            )
            SidebarEntry(
                label = "Kasir",
                iconRes = R.drawable.nav_kasir,
                selected = currentDestinationRoute == ORDER_BUILDER_ROUTE || currentDestinationRoute?.startsWith("orderCheckout/") == true,
                onClick = { onNavigateExtra(ORDER_BUILDER_ROUTE) },
            )
            SidebarEntry(
                label = "Orders",
                iconRes = R.drawable.nav_orders,
                selected = currentDestination?.hierarchy?.any { it.route == MainRoute.ORDERS.route } == true,
                onClick = { onNavigateMain(MainRoute.ORDERS) },
            )
            SidebarEntry(
                label = "Riwayat",
                iconRes = R.drawable.nav_orders,
                selected = currentDestinationRoute == TRANSACTION_HISTORY_ROUTE ||
                    currentDestinationRoute?.startsWith("receiptPreview/") == true,
                onClick = { onNavigateExtra(TRANSACTION_HISTORY_ROUTE) },
            )
            SidebarEntry(
                label = "Produk",
                iconRes = R.drawable.nav_produk,
                selected = isProductDestination,
                onClick = { onNavigateMain(MainRoute.MENU) },
            )
            SidebarEntry(
                label = "Arus Kas",
                iconRes = R.drawable.nav_arus_kas,
                selected = currentDestinationRoute == CASHFLOW_ROUTE,
                onClick = { onNavigateExtra(CASHFLOW_ROUTE) },
            )
            if (isOwner) {
                SidebarEntry(
                    label = "Rekomendasi",
                    iconRes = R.drawable.nav_rekomendasi,
                    selected = currentDestinationRoute == RECOMMENDATION_ROUTE,
                    onClick = { onNavigateExtra(RECOMMENDATION_ROUTE) },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        SidebarEntry(
            label = "Pengaturan",
            iconRes = MainRoute.SETTINGS.iconRes,
            selected = currentDestination?.hierarchy?.any { it.route == MainRoute.SETTINGS.route } == true,
            onClick = { onNavigateMain(MainRoute.SETTINGS) },
        )
    }
}

@Composable
private fun SidebarEntry(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) SidebarActive else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.pagePadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            Image(
                painter = painterResource(R.drawable.nav_rekomendasi),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessDeniedScreen(
    title: String,
    message: String,
) {
    PlaceholderScreen(
        title = title,
        message = "$message Login sebagai Owner untuk melanjutkan.",
    )
}
