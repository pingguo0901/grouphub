package com.stellarelite.grouphub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import grouphub.composeapp.generated.resources.Res
import grouphub.composeapp.generated.resources.app_icon

// ============ 应用入口（登录态管理） ============
@Composable
fun App(
    onCheckUpdate: (suspend () -> VersionInfo?)? = null,
    onRequestUpdate: ((VersionInfo) -> Unit)? = null,
) {
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }

    LaunchedEffect(Unit) {
        onCheckUpdate?.let { checkFn ->
            checkFn()?.let {
                updateInfo = it
                showUpdateDialog = true
            }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF1B5E20),
        secondary = Color(0xFFF9A825),
    )) {
        var token by remember { mutableStateOf<String?>(null) }
        if (token == null) {
            LoginScreen(onLoggedIn = { token = it })
        } else {
            MainScaffold(token = token!!)
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本 v${updateInfo!!.versionName}") },
            text = { Text(updateInfo!!.changelog.replace("- ", "• ")) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onRequestUpdate?.invoke(updateInfo!!)
                }) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("稍后") }
            }
        )
    }
}

// ============ 登录界面 ============
@Composable
fun LoginScreen(onLoggedIn: (String) -> Unit) {
    var email by remember { mutableStateOf("pingguo0901@gmail.com") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = "App Icon",
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("星域集团 · 中心枢纽", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("董事长全域驾驶舱", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Color.Red, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    SupabaseApi.signIn(email, password).onSuccess { tk ->
                        onLoggedIn(tk)
                    }.onFailure { e ->
                        error = e.message
                    }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text(if (loading) "登录中..." else "登录")
        }
    }
}

// ============ 主界面（底部导航） ============
@Composable
fun MainScaffold(token: String) {
    var current by remember { mutableStateOf(0) }
    var sub by remember { mutableStateOf<String?>(null) }

    if (sub != null) {
        SubScreen(sub!!, token, onBack = { sub = null })
        return
    }

    val tabs = listOf(
        TabItem("首页", Icons.Filled.Dashboard) { DashboardScreen(token, onNavigate = { sub = it }) },
        TabItem("炙巷财务", Icons.Filled.Restaurant) { ZhixiangFinanceScreen(token) },
        TabItem("星域财务", Icons.Filled.Flight) { XyzlFinanceScreen(token) },
        TabItem("薪资", Icons.Filled.Groups) { PayrollScreen(token) },
        TabItem("税务", Icons.Filled.Receipt) { TaxScreen(token) },
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = current == i,
                        onClick = { current = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 11.sp) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            tabs[current].content()
        }
    }
}

@Composable
fun SubScreen(name: String, token: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        when (name) {
            "合规预警" -> ComplianceScreen(token)
            "官方档案" -> ArchiveScreen(token)
            "数据同步" -> SyncScreen(token)
            "消息归集" -> MessageScreen(token)
            "AI 助手" -> AiChatScreen(token)
            "FB 中控" -> FbScreen(token)
            "审批" -> ApprovalScreen(token)
            else -> {}
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector, val content: @Composable () -> Unit)

// ============ 1. 首页总驾驶舱（接真实数据） ============
@Composable
fun DashboardScreen(token: String, onNavigate: (String) -> Unit = {}) {
    var officialMode by remember { mutableStateOf(true) }
    var entities by remember { mutableStateOf<List<BusinessEntity>>(emptyList()) }
    var revenue by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var cost by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var drawing by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var showOwnerDrawing by remember { mutableStateOf(false) }
    var showCashflow by remember { mutableStateOf(false) }
    var entryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(token, refreshKey) {
        val ents = SupabaseApi.fetchEntities(token)
        entities = ents
        val rev = mutableMapOf<String, Double>()
        val cst = mutableMapOf<String, Double>()
        val drw = mutableMapOf<String, Double>()
        for (e in ents) {
            rev[e.id] = SupabaseApi.fetchRevenue(token, e.id)
            cst[e.id] = SupabaseApi.fetchCost(token, e.id)
            drw[e.id] = SupabaseApi.fetchOwnerDrawing(token, e.id)
        }
        revenue = rev
        cost = cst
        drawing = drw
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("星域集团 · 总驾驶舱", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box {
                FilledTonalButton(onClick = { entryMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "录入")
                    Spacer(Modifier.width(4.dp))
                    Text("录入")
                }
                DropdownMenu(expanded = entryMenu, onDismissRequest = { entryMenu = false }) {
                    DropdownMenuItem(text = { Text("老板提款") }, onClick = { entryMenu = false; showOwnerDrawing = true })
                    DropdownMenuItem(text = { Text("手动收支") }, onClick = { entryMenu = false; showCashflow = true })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 双账本切换
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账本视图", fontSize = 13.sp, color = Color.Gray)
                    Text(
                        if (officialMode) "官方报税账本（上交政府）" else "真实经营账本（内部）",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }
                Switch(checked = officialMode, onCheckedChange = { officialMode = it })
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("双企业数据总览", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (loading) {
            Text("加载中...", color = Color.Gray)
        } else {
            for (e in entities) {
                val rev = revenue[e.id] ?: 0.0
                val cst = cost[e.id] ?: 0.0
                val drw = drawing[e.id] ?: 0.0
                val industry = if (e.name.contains("星域")) {
                    if (officialMode) "电子零件商贸" else "跨境包车经营"
                } else "F&B 餐饮"
                val profit = rev - cst - if (officialMode) 0.0 else drw
                BusinessCard(
                    name = e.name,
                    industry = industry,
                    mode = if (officialMode) "报税账" else "真实账",
                    revenue = rev, cost = cst, profit = profit
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("快捷入口", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickEntry("合规预警", Icons.Filled.Warning, Modifier.weight(1f)) { onNavigate("合规预警") }
            QuickEntry("官方档案", Icons.Filled.Folder, Modifier.weight(1f)) { onNavigate("官方档案") }
            QuickEntry("数据同步", Icons.Filled.Sync, Modifier.weight(1f)) { onNavigate("数据同步") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickEntry("消息归集", Icons.Filled.Email, Modifier.weight(1f)) { onNavigate("消息归集") }
            QuickEntry("AI 助手", Icons.Filled.SmartToy, Modifier.weight(1f)) { onNavigate("AI 助手") }
            QuickEntry("FB 中控", Icons.Filled.ThumbUp, Modifier.weight(1f)) { onNavigate("FB 中控") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickEntry("审批", Icons.Filled.FactCheck, Modifier.weight(1f)) { onNavigate("审批") }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }

    if (showOwnerDrawing) {
        OwnerDrawingDialog(token, entities, onDone = { showOwnerDrawing = false; refreshKey = refreshKey + 1 }, onDismiss = { showOwnerDrawing = false })
    }
    if (showCashflow) {
        CashflowDialog(token, entities, onDone = { showCashflow = false; refreshKey = refreshKey + 1 }, onDismiss = { showCashflow = false })
    }
}

@Composable
fun BusinessCard(name: String, industry: String, mode: String, revenue: Double, cost: Double, profit: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(mode, fontSize = 12.sp, color = Color(0xFF1B5E20))
            }
            Text(industry, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("营收 RM %.2f".format(revenue), fontSize = 14.sp)
            Text("成本 RM %.2f".format(cost), fontSize = 14.sp)
            Text("剩余周转金 RM %.2f".format(profit), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickEntry(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}


