package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutinApp(viewModel: RutinViewModel = viewModel()) {
    val context = LocalContext.current
    
    // Database flows
    val users by viewModel.allUsers.collectAsState()
    val merchants by viewModel.allMerchants.collectAsState()
    val campaigns by viewModel.allCampaigns.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val routes by viewModel.allRoutes.collectAsState()
    
    // Live Session/State managers
    val currentUser by viewModel.currentUser.collectAsState()
    val activeUserRoutes by viewModel.activeUserRoutes.collectAsState()
    val activeUserTransactions by viewModel.activeUserTransactions.collectAsState()
    
    // Local Session Gate
    var isLoggedIn by remember { mutableStateOf(false) }
    
    // Dialog and Feedback displays
    val scanResult = viewModel.scanResult
    val redeemResult = viewModel.redeemResult
    val toastMessage = viewModel.toastMessage

    // Navigation and Admin-Creation Views
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var selectedDrawerOption by remember { mutableStateOf("caminito") } // "caminito", "scan", "perfil", "cupones", "config"
    var showAddMerchantDialog by remember { mutableStateOf(false) }
    var showAddCampaignDialog by remember { mutableStateOf(false) }
    var showAddRouteDialog by remember { mutableStateOf(false) }
    
    // Trigger toast alerts
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            android.widget.Toast.makeText(context, toastMessage, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    // Reset selection option when user login state / role changes, and enforce Firebase Auth session validation
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                // No real Firebase Auth session found, force logout and redirect to WelcomeScreen
                viewModel.logout()
            } else {
                selectedDrawerOption = when (user.role) {
                    AppRole.VECINO -> "caminito"
                    AppRole.COMERCIO -> "comercio_dashboard"
                    AppRole.ADMIN -> "admin_dashboard"
                }
            }
        }
    }

    // Welcomer Gate
    if (currentUser == null) {
        WelcomeScreen(
            users = users,
            onUserSelect = { selectedUser ->
                viewModel.selectUser(selectedUser)
            },
            viewModel = viewModel
        )
    } else {
        val activeUser = currentUser!!
        
        val appContent = @Composable {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                                modifier = Modifier.testTag("hamburger_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Abrir Menú",
                                    tint = CandyPink
                                )
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            brush = Brush.verticalGradient(listOf(CandyPink, CandyOrange)),
                                            shape = CircleShape
                                        )
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "RutinLocal Logo",
                                        tint = CandyYellow,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "RutinLocal",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        color = CandyPink,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = when (selectedDrawerOption) {
                                            "caminito" -> "Caminito de Premios 🗺️"
                                            "scan" -> "Escáner QR Certificador 📸"
                                            "perfil" -> "Tu Perfil de Vecino 👤"
                                            "cupones" -> "Historial de Cupones 🎟️"
                                            "config" -> "Configuración ⚙️"
                                            "comercio_dashboard" -> "Panel de Comercio 🏪"
                                            "admin_dashboard" -> "Consola de Control 🏆"
                                            else -> "Panel de Control"
                                        },
                                        fontSize = 11.sp,
                                        color = CandyTextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        actions = {
                            if (activeUser.role != AppRole.VECINO) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CandyOrange.copy(alpha = 0.15f))
                                        .border(1.dp, CandyOrange, RoundedCornerShape(16.dp))
                                        .clickable { viewModel.logout() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Salir", tint = CandyOrange, modifier = Modifier.size(16.dp))
                                        Text("Salir", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CandyOrange)
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(CandyBgStart, CandyBgEnd)
                        )
                    )
                    .testTag("app_root")
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Animated sliding local push notification alert at the top
                    val alertMsg = viewModel.activePushNotification
                    AnimatedVisibility(
                        visible = alertMsg != null,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .zIndex(100f)
                    ) {
                        if (alertMsg != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CandyPurple),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.activePushNotification = null }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "RutinLocal • ¡Nueva Alerta! 🔔",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = alertMsg,
                                            color = Color.White.copy(alpha = 0.95f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.activePushNotification = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cerrar",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = CandyPink.copy(alpha = 0.04f),
                            radius = 200.dp.toPx(),
                            center = Offset(size.width * 0.2f, size.height * 0.3f)
                        )
                        drawCircle(
                            color = CandyPurple.copy(alpha = 0.04f),
                            radius = 160.dp.toPx(),
                            center = Offset(size.width * 0.8f, size.height * 0.7f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        when (activeUser.role) {
                            AppRole.VECINO -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        when (selectedDrawerOption) {
                                            "caminito" -> VecinoDashboard(
                                                user = activeUser,
                                                routes = routes,
                                                merchants = merchants,
                                                routeStatuses = activeUserRoutes,
                                                transactions = activeUserTransactions,
                                                viewModel = viewModel
                                            )
                                            "scan" -> VecinoScanScreen(
                                                user = activeUser,
                                                merchants = merchants,
                                                viewModel = viewModel
                                            )
                                            "perfil" -> VecinoPerfilScreen(
                                                user = activeUser,
                                                viewModel = viewModel
                                            )
                                            "cupones" -> VecinoCuponesScreen(
                                                user = activeUser,
                                                transactions = activeUserTransactions,
                                                viewModel = viewModel
                                            )
                                            "config" -> VecinoConfigScreen(
                                                user = activeUser,
                                                viewModel = viewModel
                                            )
                                        }
                                    }

                                    // Spacing for Advertising Banner with fixed height equivalent to 100px (approx 92dp)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(92.dp)
                                            .padding(bottom = 8.dp)
                                            .testTag("ad_banner_container"),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.2.dp, CandyPurple.copy(alpha = 0.25f))
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .background(CandyPink.copy(alpha = 0.15f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("📢", fontSize = 18.sp)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "ESPACIO PUBLICITARIO DISPONIBLE",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = CandyPurple,
                                                        letterSpacing = 0.3.sp
                                                    )
                                                    Text(
                                                        text = "Auspicia con tu negocio local aquí y llega directamente a los vecinos del barrio.",
                                                        fontSize = 9.sp,
                                                        color = CandyTextMuted,
                                                        lineHeight = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            AppRole.COMERCIO -> {
                                if (selectedDrawerOption == "config") {
                                    VecinoConfigScreen(
                                        user = activeUser,
                                        viewModel = viewModel
                                    )
                                } else {
                                    val matchingMerchant = merchants.find { it.name.contains("Marita") || it.name.contains("Café") }
                                    val merchantId = matchingMerchant?.id ?: 1
                                    ComercioDashboard(
                                        merchantId = merchantId,
                                        merchantName = matchingMerchant?.name ?: activeUser.name,
                                        merchants = merchants,
                                        campaigns = campaigns,
                                        transactions = transactions,
                                        users = users,
                                        onAddCampaign = { showAddCampaignDialog = true },
                                        viewModel = viewModel
                                    )
                                }
                            }
                            
                            AppRole.ADMIN -> {
                                if (selectedDrawerOption == "config") {
                                    VecinoConfigScreen(
                                        user = activeUser,
                                        viewModel = viewModel
                                    )
                                } else {
                                    SuperadminDashboard(
                                        merchants = merchants,
                                        users = users,
                                        transactions = transactions,
                                        routes = routes,
                                        onAddMerchant = { showAddMerchantDialog = true },
                                        onAddRoute = { showAddRouteDialog = true },
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFFFAF9F6),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Brush.horizontalGradient(listOf(CandyPink, CandyOrange)))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .background(Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Avatar",
                                            tint = CandyPink,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = activeUser.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = activeUser.email,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "📍 Vecino Activo",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val drawerItems = when (activeUser.role) {
                                AppRole.VECINO -> listOf(
                                    Triple("caminito", "🗺️ Caminito de Premios", "Tu mapa de progreso"),
                                    Triple("scan", "📸 Escanear Visita QR", "Registra tus sellos comunales"),
                                    Triple("perfil", "👤 Mi Perfil / Avatar", "Ver perfil y detalles"),
                                    Triple("cupones", "🎟️ Historial de Cupones", "Tus sellos y canjes"),
                                    Triple("config", "⚙️ Configuración", "Opciones del sistema")
                                )
                                AppRole.COMERCIO -> listOf(
                                    Triple("comercio_dashboard", "🏪 Panel del Comercio", "Control de ventas y canjes"),
                                    Triple("config", "⚙️ Configuración", "Opciones del sistema")
                                )
                                AppRole.ADMIN -> listOf(
                                    Triple("admin_dashboard", "🏆 Consola de Control", "Panel general del Intendente"),
                                    Triple("config", "⚙️ Configuración", "Opciones del sistema")
                                )
                            }

                            drawerItems.forEach { (id, label, desc) ->
                                val active = selectedDrawerOption == id
                                val itemColor = when (activeUser.role) {
                                    AppRole.VECINO -> CandyPink
                                    AppRole.COMERCIO -> CandyOrange
                                    AppRole.ADMIN -> CandyPurple
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (active) itemColor.copy(alpha = 0.12f) else Color.Transparent
                                        )
                                        .border(
                                            width = if (active) 1.5.dp else 0.dp,
                                            color = if (active) itemColor else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            selectedDrawerOption = id
                                            scope.launch { drawerState.close() }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (active) itemColor else CandyTextDark
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 10.sp,
                                            color = CandyTextMuted
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = if (active) itemColor else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Divider(color = Color.LightGray.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CandyOrange.copy(alpha = 0.1f))
                                    .border(1.dp, CandyOrange, RoundedCornerShape(16.dp))
                                    .clickable {
                                        scope.launch { drawerState.close() }
                                        viewModel.logout()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Salir",
                                    tint = CandyOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SALIR DE LA CUENTA 🚪",
                                    color = CandyOrange,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        ) {
            appContent()
        }
    }

    // dialog simulations
    if (showAddMerchantDialog) {
        AddMerchantDialog(
            onDismiss = { showAddMerchantDialog = false },
            onConfirm = { name, hash, lat, lng, cat, address ->
                viewModel.registerMerchant(name, hash, lat, lng, cat, address)
                showAddMerchantDialog = false
            }
        )
    }

    if (showAddCampaignDialog) {
        val matchingMerchant = merchants.find { it.name.contains("Marita") || it.name.contains("Café") }
        val merchantId = matchingMerchant?.id ?: 1
        AddCampaignDialog(
            merchantId = merchantId,
            onDismiss = { showAddCampaignDialog = false },
            onConfirm = { title, points, cat ->
                viewModel.createCampaign(merchantId, title, points, cat)
                showAddCampaignDialog = false
            }
        )
    }

    if (showAddRouteDialog) {
        AddRouteDialog(
            merchantsList = merchants,
            onDismiss = { showAddRouteDialog = false },
            onConfirm = { title, desc, cat, mIds ->
                viewModel.registerRoute(title, desc, cat, mIds)
                showAddRouteDialog = false
            }
        )
    }

    // SCAN RESULTS
    scanResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissScanResult() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                        tint = if (result.success) CandyTeal else CandyPink,
                        modifier = Modifier.size(28.dp),
                        contentDescription = "Scan status"
                    )
                    Text(
                        text = if (result.success) "¡Sello Reclamado! 🌟" else "¡Fuera de Rango! 🚨",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyTextDark
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = result.message, color = CandyTextDark, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (result.success) {
                        Surface(
                            color = CandyTeal.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = CandyYellow)
                                Text(
                                    text = "¡Sumaste +${result.pointsEarned} estrellas de felicidad! El caminito de tu mapa avanza.",
                                    fontSize = 13.sp,
                                    color = CandyTextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = CandyPink.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 Regla de Coexistencia RutinLocal:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CandyPink
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Para evitar fraudes de sellos desde casa, debes estar a menos de 50 metros del comercio. El sistema valida tu geolocalización real de GPS para certificar tu presencia física en el local.",
                                    fontSize = 11.sp,
                                    color = CandyTextDark
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                CandyButton(
                    onClick = { viewModel.dismissScanResult() },
                    text = "¡Excelente!",
                    color = if (result.success) CandyTeal else CandyPink,
                    modifier = Modifier.width(130.dp)
                )
            },
            containerColor = Color.White
        )
    }

    // REDEEM RESULTS OVERLAYS
    redeemResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRedeemResult() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                        tint = if (result.success) CandyTeal else CandyPink,
                        modifier = Modifier.size(28.dp),
                        contentDescription = "Redeem status"
                    )
                    Text(
                        text = if (result.success) "Cupón Canjeado 🎁" else "Faltan Estrellas 🪙",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyTextDark
                    )
                }
            },
            text = {
                Text(text = result.message, color = CandyTextDark, fontSize = 14.sp)
            },
            confirmButton = {
                CandyButton(
                    onClick = { viewModel.dismissRedeemResult() },
                    text = "De Acuerdo",
                    color = if (result.success) CandyTeal else CandyPink,
                    modifier = Modifier.width(130.dp)
                )
            },
            containerColor = Color.White
        )
    }

    // ACTIVE STAR MILESTONES OVERLAYS
    viewModel.activeStarMilestone?.let { activeStarMilestone ->
        AlertDialog(
            onDismissRequest = { /* Force action to prioritize the gamified decision! */ },
            confirmButton = {}, // Custom buttons inside content
            title = null,
            text = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("star_milestone_modal"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(3.dp, CandyYellow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "chest_glow")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "scale"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .scale(scale)
                                .background(
                                    brush = Brush.radialGradient(listOf(CandyYellow.copy(alpha = 0.4f), Color.Transparent)),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎁", fontSize = 64.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "¡ESTACIÓN ${activeStarMilestone.stationNumber} COMPLETADA! 🎁⭐",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = CandyPink,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Has destrabado un Cofre de Regalo Estrella del Barrio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CandyPurple,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(CandyBgStart.copy(alpha = 0.12f))
                                .border(2.dp, CandyPink.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = activeStarMilestone.couponName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CandyTextDark,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeStarMilestone.couponRewardDesc,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CandyTextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Text(
                            text = "¿Qué decides hacer, vecino? Puedes canjear tu premio de estación hoy, o guardarlo y arriesgar para ganar el premio gordo final de la semana.",
                            fontSize = 11.sp,
                            color = CandyTextDark.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CandyButton(
                                onClick = { viewModel.claimMilestoneNow(activeStarMilestone) },
                                text = "Canjear Regalo Ahora 🎁",
                                color = CandyTeal,
                                modifier = Modifier.fillMaxWidth().testTag("claim_star_now_btn")
                            )

                            CandyButton(
                                onClick = { viewModel.continueInRoute(activeStarMilestone) },
                                text = "Continuar en la Ruta 🚀",
                                color = CandyPink,
                                modifier = Modifier.fillMaxWidth().testTag("continue_route_btn")
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.Transparent
        )
    }
}

// --------------------------------------------------------------------------------------------------
// CANDY STYLE HELPER WIDGETS
// --------------------------------------------------------------------------------------------------
@Composable
fun CandyButton(
    onClick: () -> Unit,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.25f)) // Back drop-shadow
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.9f), color)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CandyCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.8f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7).copy(alpha = 0.94f)),
        border = BorderStroke(2.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// -------------------------------------------------------------------------------------------------// --------------------------------------------------------------------------------------------------
@Composable
fun WelcomeScreen(
    users: List<UserEntity>,
    onUserSelect: (UserEntity) -> Unit,
    viewModel: RutinViewModel
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("rutin_prefs", android.content.Context.MODE_PRIVATE) }
    
    // SharedPreferences values
    var savedEmail by remember { mutableStateOf(sharedPrefs.getString("saved_email", "salvatorealejandro233@gmail.com") ?: "salvatorealejandro233@gmail.com") }
    var savedPassword by remember { mutableStateOf(sharedPrefs.getString("saved_password", "123456") ?: "123456") }
    var biometricEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", true)) }

    // Navigation sub-states: "login" or "register" or "merchant"
    var currentView by remember { mutableStateOf("login") } 

    // Login inputs
    var loginEmail by remember { mutableStateOf(savedEmail) }
    var loginPassword by remember { mutableStateOf(savedPassword) }

    // Merchant login inputs
    var merchantEmail by remember { mutableStateOf("") }
    var merchantPassword by remember { mutableStateOf("") }

    // Manual registration fields
    var manualName by remember { mutableStateOf("") }
    var manualEmail by remember { mutableStateOf("") }
    var manualPassword by remember { mutableStateOf("") }
    var manualCity by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("🦊") }
    var selectedRole by remember { mutableStateOf(AppRole.VECINO) }

    // Biometric scanner dialogue
    var showBiometricDialog by remember { mutableStateOf(false) }
    var isBiometricScanning by remember { mutableStateOf(false) }

    // Visual Avatars
    val avatarsList = listOf("🦊", "🦁", "🐯", "🐼", "🐨", "🐸", "🐙", "🦄", "👤")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFDFBF7), Color(0xFFF4F1FA)) // Pastel cream & soft grey-lavender
                )
            )
            .testTag("welcome_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Candy sparkles floating in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(CandyYellow.copy(alpha = 0.25f), 12.dp.toPx(), Offset(size.width * 0.15f, size.height * 0.2f))
            drawCircle(CandyPink.copy(alpha = 0.18f), 18.dp.toPx(), Offset(size.width * 0.85f, size.height * 0.15f))
            drawCircle(CandyBlueAccent.copy(alpha = 0.2f), 10.dp.toPx(), Offset(size.width * 0.75f, size.height * 0.7f))
            drawCircle(CandyPurple.copy(alpha = 0.18f), 15.dp.toPx(), Offset(size.width * 0.2f, size.height * 0.85f))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            item {
                // Visual Logo candy bubble
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(Color.White, CandyBgEnd)),
                            shape = CircleShape
                        )
                        .border(3.dp, CandyPink, CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Star Logo",
                        tint = CandyYellow,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RutinLocal",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyPink,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Fidelización Colectiva del Vecindario",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyPurple,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                CandyCard(borderColor = CandyPink.copy(alpha = 0.15f)) {
                    when (currentView) {
                        "login" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "¡Qué bueno verte de vuelta! 👋",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CandyTextDark,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                Text(
                                    text = "Inicia sesión para certificar tus compras en el barrio y acumular estrellas.",
                                    fontSize = 11.sp,
                                    color = CandyTextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                OutlinedTextField(
                                    value = loginEmail,
                                    onValueChange = { loginEmail = it },
                                    label = { Text("Tu Email") },
                                    modifier = Modifier.fillMaxWidth().testTag("login_email_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                OutlinedTextField(
                                    value = loginPassword,
                                    onValueChange = { loginPassword = it },
                                    label = { Text("Tu Contraseña") },
                                    modifier = Modifier.fillMaxWidth().testTag("login_pass_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                 Spacer(modifier = Modifier.height(8.dp))

                                 // Visual 1: Large prominent button at the top of actions!
                                 Button(
                                     onClick = {
                                         if (loginEmail.isBlank() || loginPassword.isBlank()) {
                                             viewModel.toastMessage = "❌ Por favor completa tus credenciales."
                                         } else {
                                             // Save options
                                             sharedPrefs.edit()
                                                 .putString("saved_email", loginEmail)
                                                 .putString("saved_password", loginPassword)
                                                 .apply()
                                             
                                             // Login with Firebase Auth
                                             viewModel.loginManualFirebase(
                                                 email = loginEmail,
                                                 password = loginPassword
                                             )
                                         }
                                     },
                                     shape = RoundedCornerShape(24.dp),
                                     colors = ButtonDefaults.buttonColors(
                                         containerColor = CandyPink,
                                         contentColor = Color.White
                                     ),
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .height(54.dp)
                                         .testTag("iniciar_sesion_grande")
                                 ) {
                                     Row(
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(8.dp)
                                     ) {
                                         Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                                         Text(
                                             text = "Iniciar Sesión",
                                             fontSize = 15.sp,
                                             fontWeight = FontWeight.Black
                                         )
                                     }
                                 }
                            }
                        }
                        "register" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Registro de Usuario 🚀",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CandyPurple,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                OutlinedTextField(
                                    value = manualName,
                                    onValueChange = { manualName = it },
                                    label = { Text("Nombre Completo") },
                                    modifier = Modifier.fillMaxWidth().testTag("manual_name_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                OutlinedTextField(
                                    value = manualEmail,
                                    onValueChange = { manualEmail = it },
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth().testTag("manual_email_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                OutlinedTextField(
                                    value = manualPassword,
                                    onValueChange = { manualPassword = it },
                                    label = { Text("Contraseña") },
                                    modifier = Modifier.fillMaxWidth().testTag("manual_pass_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                OutlinedTextField(
                                    value = manualCity,
                                    onValueChange = { manualCity = it },
                                    label = { Text("Ciudad o Barrio de residencia") },
                                    modifier = Modifier.fillMaxWidth().testTag("manual_city_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                Column {
                                    Text(
                                        text = "Escoge tu Avatar de Jugador:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CandyTextDark,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(avatarsList) { av ->
                                            val isSelected = selectedAvatar == av
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) CandyPink.copy(alpha = 0.2f) else Color.White
                                                    )
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) CandyPink else Color.LightGray,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { selectedAvatar = av },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(av, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                CandyButton(
                                    onClick = {
                                        if (manualName.isBlank() || manualEmail.isBlank() || manualPassword.isBlank()) {
                                            viewModel.toastMessage = "❌ Completa todos los campos requeridos."
                                        } else {
                                            viewModel.registrarManualFirebase(
                                                email = manualEmail,
                                                password = manualPassword,
                                                nombre = manualName,
                                                rol = AppRole.VECINO
                                            )
                                        }
                                    },
                                    text = "Crear Cuenta de Usuario 🚀",
                                    color = CandyTeal,
                                    modifier = Modifier.fillMaxWidth().testTag("manual_signup_btn")
                                )
                            }
                        }
                        "merchant" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Acceso Exclusivo de Comercio 🏪",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CandyPink,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                Text(
                                    text = "Inicia sesión con las credenciales de tu comercio para acceder a la terminal de canje.",
                                    fontSize = 11.sp,
                                    color = CandyTextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                OutlinedTextField(
                                    value = merchantEmail,
                                    onValueChange = { merchantEmail = it },
                                    label = { Text("Email Comercio") },
                                    modifier = Modifier.fillMaxWidth().testTag("merchant_email_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                OutlinedTextField(
                                    value = merchantPassword,
                                    onValueChange = { merchantPassword = it },
                                    label = { Text("Contraseña") },
                                    modifier = Modifier.fillMaxWidth().testTag("merchant_pass_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CandyTextDark,
                                        unfocusedTextColor = CandyTextDark,
                                        focusedBorderColor = CandyPink,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = CandyPink,
                                        unfocusedLabelColor = CandyTextMuted
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        if (merchantEmail.isBlank() || merchantPassword.isBlank()) {
                                            viewModel.toastMessage = "❌ Por favor completa tus credenciales de comercio."
                                        } else {
                                            viewModel.loginManualFirebase(
                                                email = merchantEmail,
                                                password = merchantPassword
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CandyPink,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("comercio_sesion_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                                        Text(
                                            text = "Acceder al Panel",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Volver al Acceso Vecino",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CandyPink,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .clickable { currentView = "login" }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Visual 2: Subtle small links at the bottom of the card!
            item {
                when (currentView) {
                    "login" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "¿No tienes cuenta? Regístrate",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = CandyPink,
                                modifier = Modifier
                                    .clickable { currentView = "register" }
                                    .padding(vertical = 4.dp)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .clickable { currentView = "merchant" }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Acceso Comercio",
                                    tint = CandyPink,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Soy Comercio",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CandyPink
                                )
                            }
                        }
                    }
                    "register" -> {
                        Text(
                            text = "¿Ya tienes cuenta? Inicia Sesión",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = CandyPink,
                            modifier = Modifier
                                .clickable { currentView = "login" }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // BIOMETRIC SCANNING DIALOG (LocalAuthentication Simulation)
        if (showBiometricDialog) {
            AlertDialog(
                onDismissRequest = { showBiometricDialog = false },
                confirmButton = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                title = {},
                text = {
                    val infiniteTransition = rememberInfiniteTransition(label = "biometric")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "biometric_pulse"
                    )

                    // Sincronizar login ficticio tras 1.5 segundos
                    LaunchedEffect(Unit) {
                        isBiometricScanning = true
                        delay(1500)
                        isBiometricScanning = false
                        showBiometricDialog = false
                        
                        // Auto-login con el email y contraseña guardados
                        viewModel.loginManualFirebase(
                            email = loginEmail,
                            password = loginPassword
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .scale(pulseScale)
                                .background(CandyPurple.copy(alpha = 0.12f), CircleShape)
                                .border(2.5.dp, CandyPurple, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "FaceID / Scanner",
                                tint = CandyPurple,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Identificación Biométrica 🧬",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = CandyTextDark
                            )
                            Text(
                                text = "Autenticando con FaceID / Huella digital...",
                                fontSize = 11.sp,
                                color = CandyTextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        LinearProgressIndicator(
                            color = CandyPurple,
                            trackColor = CandyPurple.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        
                        Text(
                            text = "Llamando a módulos nativos (LocalAuthentication)...",
                            fontSize = 8.sp,
                            color = CandyTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

// --------------------------------------------------------------------------------------------------
// BARRIO VIVO EN SEGUNDO PLANO - CANDY STYLE FLOATING LANDMARKS
// --------------------------------------------------------------------------------------------------
@Composable
fun BarrioVivoBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "FondoVivo")
    
    val driftX by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX"
    )

    val driftY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY"
    )

    val softScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "softScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFDCFCE7).copy(alpha = 0.35f),
                radius = 160.dp.toPx(),
                center = Offset(size.width * 0.15f, size.height * 0.85f)
            )
            drawCircle(
                color = Color(0xFFBAE6FD).copy(alpha = 0.25f),
                radius = 130.dp.toPx(),
                center = Offset(size.width * 0.85f, size.height * 0.15f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp + driftX.dp, y = 50.dp + driftY.dp)
                .alpha(0.18f)
                .scale(softScale),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⛪", fontSize = 38.sp)
            Text("Parroquia", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CandyPurple)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp - driftX.dp, y = 140.dp + driftY.dp)
                .alpha(0.18f)
                .scale(softScale),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏪", fontSize = 36.sp)
            Text("Almacén", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CandyPurple)
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 10.dp + driftX.dp, y = 60.dp - driftY.dp)
                .alpha(0.15f)
                .scale(softScale),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏡", fontSize = 32.sp)
            Text("Casita", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CandyPurple)
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-12).dp - driftX.dp, y = (-20).dp + driftY.dp)
                .alpha(0.18f)
                .scale(softScale),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🥖", fontSize = 34.sp)
            Text("Panadería", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CandyPurple)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 30.dp + driftY.dp, y = (-50).dp + driftX.dp)
                .alpha(0.15f)
                .scale(softScale),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏪", fontSize = 32.sp)
            Text("Kiosco", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CandyPurple)
        }
    }
}

// --------------------------------------------------------------------------------------------------
// PLANO INTERACTIVO DE COEXISTENCIA DEL BARRIO (MOCK GOOGLE MAPS)
// --------------------------------------------------------------------------------------------------
@Composable
fun VecinoLocalGoogleMapCard(
    user: UserEntity,
    merchants: List<MerchantEntity>,
    viewModel: RutinViewModel
) {
    var selectedMerchant by remember { mutableStateOf<MerchantEntity?>(merchants.firstOrNull()) }
    
    CandyCard(borderColor = CandyPink.copy(alpha = 0.3f)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🗺️ PLANO DE TU BARRIO ACTIVO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyPurple,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Barranco / Lima - Coexistencia",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyTextDark
                    )
                }
                
                Badge(containerColor = CandyTeal, contentColor = Color.White) {
                    Text(
                        text = "GPS Online 🎯",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Text(
                text = "Toca cualquiera de los comercios adheridos abajo para previsualizarlo en el plano o viajar de forma interactiva.",
                fontSize = 10.sp,
                color = CandyTextMuted,
                lineHeight = 13.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(20.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color(0xFFE2F1E8)) 
                    
                    drawRect(
                        color = Color(0xFFF1F5F9),
                        topLeft = Offset(0f, size.height * 0.45f),
                        size = androidx.compose.ui.geometry.Size(size.width, 35.dp.toPx())
                    )
                    
                    drawRect(
                        color = Color(0xFFF1F5F9),
                        topLeft = Offset(size.width * 0.35f, 0f),
                        size = androidx.compose.ui.geometry.Size(28.dp.toPx(), size.height)
                    )

                    drawRect(
                        color = Color(0xFFF1F5F9),
                        topLeft = Offset(size.width * 0.75f, 0f),
                        size = androidx.compose.ui.geometry.Size(24.dp.toPx(), size.height)
                    )

                    drawRect(
                        color = Color(0xFFBAE6FD).copy(alpha = 0.7f),
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(35.dp.toPx(), size.height)
                    )

                    val minLat = -12.0520
                    val maxLat = -12.0450
                    val minLng = -77.0445
                    val maxLng = -77.0390

                    fun getProjectionOffset(lat: Double, lng: Double): Offset {
                        val x = ((lng - minLng) / (maxLng - minLng)).coerceIn(0.1, 0.9)
                        val y = (1.0 - (lat - minLat) / (maxLat - minLat)).coerceIn(0.1, 0.9)
                        return Offset(size.width * x.toFloat(), size.height * y.toFloat())
                    }

                    merchants.forEach { m ->
                        val pinPos = getProjectionOffset(m.lat, m.lng)
                        val isSelected = selectedMerchant?.id == m.id

                        if (isSelected) {
                            drawCircle(
                                color = CandyYellow.copy(alpha = 0.4f),
                                radius = 18.dp.toPx(),
                                center = pinPos
                            )
                        }

                        drawCircle(
                            color = if (isSelected) CandyPink else CandyPurple,
                            radius = 10.dp.toPx(),
                            center = pinPos
                        )

                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = pinPos
                        )
                    }

                    val userPos = getProjectionOffset(viewModel.simulatedLat, viewModel.simulatedLng)
                    
                    drawCircle(
                        color = Color(0xFF2563EB).copy(alpha = 0.25f),
                        radius = 24.dp.toPx(),
                        center = userPos
                    )
                    
                    drawCircle(
                        color = Color(0xFF2563EB),
                        radius = 8.dp.toPx(),
                        center = userPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = userPos
                    )
                }

                Text(
                    text = "Costa Verde 🌊",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = CandyPurple.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                )

                Text(
                    text = "Av. San Martín 🛣️",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = CandyTextMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 18.dp)
                )

                Text(
                    text = "Jr. Domeyer 📍",
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    color = CandyTextMuted,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(end = 45.dp, top = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF2563EB), CircleShape))
                        Text("Tú (GPS)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CandyTextDark)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                merchants.forEach { m ->
                    val isSelected = selectedMerchant?.id == m.id
                    val iconText = when {
                        m.name.contains("Marita") -> "☕"
                        m.name.contains("Antojos") -> "🍰"
                        else -> "🏺"
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CandyPink else Color.Black.copy(alpha = 0.04f))
                            .border(1.5.dp, if (isSelected) CandyPink else Color.LightGray, RoundedCornerShape(12.dp))
                            .clickable { selectedMerchant = m }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$iconText ${m.name.split(" ").lastOrNull() ?: m.name}",
                            color = if (isSelected) Color.White else CandyTextDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            selectedMerchant?.let { m ->
                val distance = viewModel.getDistanceToMerchant(m)
                val isNear = distance <= 50.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CandyBgStart.copy(alpha = 0.12f))
                        .border(1.5.dp, CandyPink.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = m.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = CandyTextDark
                        )
                        Text(
                            text = m.address,
                            fontSize = 10.sp,
                            color = CandyTextMuted
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isNear) CandyTeal else CandyPink, CircleShape)
                            )
                            Text(
                                text = if (isNear) {
                                    "¡Estás aquí! a ${distance.toInt()}m 🟢 (Dentro de rango)"
                                } else {
                                    "a ${distance.toInt()}m 🛑 (Fuera de rango)"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNear) CandyTeal else CandyPink
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        CandyButton(
                            onClick = {
                                viewModel.updateSimulatedGps(m.lat, m.lng)
                            },
                            text = "Llegar Aquí 🚲",
                            color = CandyPurple,
                            modifier = Modifier.width(110.dp).height(38.dp)
                        )

                        CandyButton(
                            onClick = {
                                viewModel.scanQrCode(m.codeHash)
                            },
                            text = "Escanear QR 📸",
                            color = CandyTeal,
                            modifier = Modifier.width(110.dp).height(38.dp),
                            enabled = isNear
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// ROLE 1: VECINO - GAME WORLD CAMINITO PROGRESS MAP (CON PREMIOS PROGRESO CARD)
// --------------------------------------------------------------------------------------------------
@Composable
fun PremiosProgresoCard(
    user: UserEntity,
    transactions: List<TransactionEntity>,
    viewModel: RutinViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }
    val points = user.points

    val thresholds = listOf(100, 180, 240, 280)
    val nextThreshold = when {
        points < 100 -> 100
        points < 180 -> 180
        points < 240 -> 240
        points < 280 -> 280
        else -> 280 + (((points - 280) / 40) + 1) * 40
    }
    val pointsNeeded = nextThreshold - points
    val progressFraction = (points.toFloat() / nextThreshold.toFloat()).coerceIn(0f, 1f)
    val reachedPrizes = points >= 100

    val highestUnlockedStationNum = when {
        points >= 280 -> 4
        points >= 240 -> 3
        points >= 180 -> 2
        points >= 100 -> 1
        else -> 0
    }

    val prizeLabel = when (highestUnlockedStationNum) {
        1 -> "Cupón Bronce (100 pts)"
        2 -> "Cupón Plata (180 pts)"
        3 -> "Cupón Oro (240 pts)"
        4 -> "Súper Cofre Semanal (280 pts)"
        else -> ""
    }

    CandyCard(
        borderColor = CandyPurple.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("premios_progreso_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isExpanded) "🏆 PANEL DE PREMIOS (Toca para Cerrar)" else "🏆 PREMIOS Y CUPONES (Toca para Abrir)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyPurple,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyPink
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (reachedPrizes) CandyTeal else Color.LightGray)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (reachedPrizes) "¡Reclama! 🎁" else "Acumulando 🐾",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Simple progress bar preview (always shown)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mis Estrellas: $points ✨",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CandyTextDark
                    )
                    Text(
                        text = "Próximo Hito: $nextThreshold pts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CandyPurple
                    )
                }

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(CandyTeal, CandyPink)))
                    )
                }
            }

            // Expanded details
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (pointsNeeded > 0) {
                            "Te faltan exactamente $pointsNeeded puntos para la próxima parada de premios."
                        } else {
                            "¡Has superado el hito semanal! Cada nuevo hito costará 40 pts adicionales."
                        },
                        fontSize = 11.sp,
                        color = CandyTextMuted
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray.copy(alpha = 0.5f)))

                    // Decision/Claim screen
                    if (reachedPrizes) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CandyPink.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                .border(1.5.dp, CandyPink.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🤔 DECISIÓN DE COEXISTENCIA VECINAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = CandyPink,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Tienes acumulados $points pts lo que te permite reclamar un $prizeLabel. ¿Prefieres canjearlo reiniciando tu camino u optar por arriesgar y seguir acumulando?",
                                fontSize = 11.sp,
                                color = CandyTextDark,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CandyButton(
                                        onClick = {
                                            val cupName = when (highestUnlockedStationNum) {
                                                1 -> "Cupón Bronce: 15% Desc 🎟️"
                                                2 -> "Cupón Plata: Bebida Gratis ☕"
                                                3 -> "Cupón Oro: Postre Completo 🍰"
                                                else -> "Super Cofre de Barrio Semanal 🎁"
                                            }
                                            val cupDesc = when (highestUnlockedStationNum) {
                                                1 -> "Disfruta de un 15% de descuento directo en tus consumos de panadería o almacén."
                                                2 -> "Reclama un fragante Espresso de Especialidad gratis en Cafecito Marita."
                                                3 -> "Disfruta de un delicioso queque o postre del día gratis en Almacén Don Lucho."
                                                else -> "¡El tesoro del gran caminante del barrio! Vale de compra de S/. 50 + Pase Dorado."
                                            }
                                            viewModel.claimMilestoneNow(
                                                StarMilestone(
                                                    highestUnlockedStationNum,
                                                    cupName,
                                                    cupDesc
                                                )
                                            )
                                        },
                                        text = "Canjear y Reiniciar 🔄",
                                        color = CandyTeal,
                                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("canjear_y_reiniciar_btn")
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    CandyButton(
                                        onClick = {
                                            viewModel.toastMessage = "🐾 ¡Camino de Coexistencia asegurado! Elegiste continuar acumulando para canjear un premio superior."
                                        },
                                        text = "Seguir acumulando 🚀",
                                        color = CandyPink,
                                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("seguir_acumulando_btn")
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.02f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔒 PRIMER PREMIO EN 100 PTS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(
                                text = "Llegar a la primera parada requiere 100 puntos. ¡Consume en tus comercios locales favoritos para comenzar a ganar!",
                                fontSize = 11.sp,
                                color = CandyTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray.copy(alpha = 0.5f)))

                    // WALLET INTEGRATION
                    val activeCoupons = remember(transactions, user.id) {
                        transactions.filter { it.userId == user.id && it.type == "COUPON_ACTIVO" }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("neighbor_coupons_section")
                    ) {
                        Text(
                            text = "🎟️ MIS CUPONES DEL BARRIO (${activeCoupons.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = CandyPurple,
                            letterSpacing = 0.5.sp
                        )

                        if (activeCoupons.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.02f))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tu cartera de beneficios comunales está vacía. ¡Sigue avanzando!",
                                    fontSize = 10.sp,
                                    color = CandyTextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            activeCoupons.forEach { coup ->
                                val couponTitle = when (coup.pointsChange) {
                                    1 -> "Cupón Bronce: 15% Desc 🎟️"
                                    2 -> "Cupón Plata: Bebida Gratis ☕"
                                    3 -> "Cupón Oro: Postre Completo 🍰"
                                    else -> "Super Cofre de Barrio Semanal 🎁"
                                }
                                val couponColor = when (coup.pointsChange) {
                                    1 -> Color(0xFFCD7F32)
                                    2 -> Color(0xFFC0C0C0)
                                    3 -> CandyYellow
                                    else -> CandyPurple
                                }

                                var showQrDialogForCoupon by remember { mutableStateOf(false) }

                                if (showQrDialogForCoupon) {
                                    AlertDialog(
                                        onDismissRequest = { showQrDialogForCoupon = false },
                                        confirmButton = {
                                            CandyButton(
                                                onClick = { showQrDialogForCoupon = false },
                                                text = "Ocultar Código",
                                                color = CandyPink,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        title = {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = couponTitle,
                                                    fontWeight = FontWeight.Black,
                                                    color = CandyTextDark,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "Tique Único de Canje",
                                                    fontSize = 10.sp,
                                                    color = CandyPurple,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        text = {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("coupon_qr_dialog_content"),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Presenta este tique digital en caja en cualquier local comercial para invalidar e instantáneamente canjear tu premio físico.",
                                                    fontSize = 10.sp,
                                                    color = CandyTextMuted,
                                                    textAlign = TextAlign.Center
                                                )

                                                CoexistenciaQrDisplay(
                                                    content = "rutinlocal://coupon/${coup.id}",
                                                    modifier = Modifier
                                                        .size(160.dp)
                                                        .testTag("coupon_qr_image_${coup.id}")
                                                )

                                                Text(
                                                    text = "rutinlocal://coupon/${coup.id}",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Black,
                                                    color = CandyTextDark,
                                                    modifier = Modifier
                                                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        },
                                        containerColor = Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .border(1.dp, couponColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                        .clickable { showQrDialogForCoupon = true }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🎟️", fontSize = 16.sp)
                                            Column {
                                                Text(couponTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CandyTextDark)
                                                Text("Toca para abrir QR 📱", fontSize = 9.sp, color = CandyPink, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CandyTeal.copy(alpha = 0.1f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ACTIVO", color = CandyTeal, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

              // --------------------------------------------------------------------------------------------------
// HELPERS & STATUS BAR
// --------------------------------------------------------------------------------------------------
@Composable
fun VecinoStatusBar(user: UserEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vecino_status_bar")
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.5.dp, CandyPink.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(CandyPink, CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 14.sp)
            }
            Column {
                Text(
                    text = user.name.split(" ").firstOrNull() ?: user.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = CandyTextDark
                )
                Text(
                    text = "Explorador de Barrio 🗺️",
                    fontSize = 9.sp,
                    color = CandyTextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CandyBgStart.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${user.points}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = CandyPink
            )
            Text(
                text = "ESTRELLAS ✨",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = CandyPurple
            )
        }
    }
}

@Composable
fun VecinoDashboard(
    user: UserEntity,
    routes: List<RouteEntity>,
    merchants: List<MerchantEntity>,
    routeStatuses: List<UserRouteStatusEntity>,
    transactions: List<TransactionEntity>,
    viewModel: RutinViewModel
) {
    var showNearbyMap by remember { mutableStateOf(false) }

    // Pulsation animation for node
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val currentPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "currentPulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vecino_dashboard")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp), // Clean spacing at the bottom, rewards panel is now completely removed
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Thin and elegant top status bar instead of the big "Puntos Acumulados" header
            VecinoStatusBar(user)

            // 2. THE SERPENT BOARD GAME MAP - occupies 80% of screen center
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.45f))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .border(2.dp, Color.White, RoundedCornerShape(28.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // Background landmarks (Style Candy Crush)
                BarrioVivoBackground()

                // Scrollable container for serpentine path
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(600.dp) // Generous height for winding scrolling path
                    ) {
                        // Sinuous connector path drawn on Canvas
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(vertical = 40.dp)
                        ) {
                            val strokeBg = Stroke(
                                width = 16.dp.toPx()
                            )
                            val strokeDashed = Stroke(
                                width = 6.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                            )
                            val w = size.width
                            val h = size.height

                            // Level 0 (center-left), Level 1 (center-right), Level 2 (left), Level 3 (center)
                            val p0 = Offset(w * 0.35f, h * 0.08f)
                            val p1 = Offset(w * 0.65f, h * 0.33f)
                            val p2 = Offset(w * 0.25f, h * 0.58f)
                            val p3 = Offset(w * 0.5f, h * 0.85f)

                            val pathObj = androidx.compose.ui.graphics.Path().apply {
                                moveTo(p0.x, p0.y)
                                cubicTo(
                                    x1 = w * 0.75f, y1 = h * 0.12f,
                                    x2 = w * 0.75f, y2 = h * 0.28f,
                                    x3 = p1.x, y3 = p1.y
                                )
                                cubicTo(
                                    x1 = w * 0.15f, y1 = h * 0.38f,
                                    x2 = w * 0.15f, y2 = h * 0.52f,
                                    x3 = p2.x, y3 = p2.y
                                )
                                cubicTo(
                                    x1 = w * 0.65f, y1 = h * 0.68f,
                                    x2 = w * 0.65f, y2 = h * 0.82f,
                                    x3 = p3.x, y3 = p3.y
                                )
                            }

                            // Draw background thick trail
                            drawPath(
                                path = pathObj,
                                brush = Brush.horizontalGradient(listOf(CandyPink, CandyPurple, CandyTeal)),
                                style = strokeBg,
                                alpha = 0.25f
                            )

                            // Draw dashed clear line of dots
                            drawPath(
                                path = pathObj,
                                color = Color.White,
                                style = strokeDashed
                            )
                        }

                        // Sinuous Nodes placement sequential offset (100% matched coordinate offsets with canvas)
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(50.dp)
                        ) {
                            val points = user.points

                            val stationsList = listOf(
                                Triple("Estación Bronce 🌟 (100 pts)", 100, 1),
                                Triple("Estación Plata 🌟 (180 pts)", 180, 2),
                                Triple("Estación Oro 🌟 (240 pts)", 240, 3)
                            )

                            stationsList.forEachIndexed { index, station ->
                                val targetPoints = station.second
                                val stationNum = station.third

                                val isCompleted = points >= targetPoints
                                val isCurrent = when (index) {
                                    0 -> points < 100
                                    1 -> points in 100..179
                                    2 -> points in 180..239
                                    else -> false
                                }

                                val nodeOffset = when (index) {
                                    0 -> (-50).dp  // Aligned left
                                    1 -> 50.dp     // Aligned right
                                    2 -> (-40).dp  // Slightly left
                                    else -> 0.dp
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .offset(x = nodeOffset),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable {
                                            if (isCompleted || isCurrent) {
                                                viewModel.triggerMilestone(stationNum)
                                            } else {
                                                viewModel.toastMessage = "¡Aún no llegas aquí! Consigue más puntos en locales del vecindario (necesitas $targetPoints pts)."
                                            }
                                        }
                                    ) {
                                        // Node Shape bubble (Enlarged)
                                        Box(
                                            modifier = Modifier
                                                .size(82.dp) // Large nodes
                                                .scale(if (isCurrent) currentPulseScale else 1f)
                                                .background(
                                                    brush = when {
                                                        isCompleted -> Brush.verticalGradient(listOf(CandyYellow, CandyOrange))
                                                        isCurrent -> Brush.verticalGradient(listOf(CandyPink, CandyPurple))
                                                        else -> Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
                                                    },
                                                    shape = CircleShape
                                                )
                                                .border(
                                                    width = if (isCurrent) 4.dp else 2.dp,
                                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.9f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Completed Milestone",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(42.dp) // Bright stars!
                                                )
                                            } else if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = "Pulsing Milestone Chest",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Locked Milestone",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Small clean node desc
                                        Text(
                                            text = station.first,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = CandyTextDark,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = when {
                                                isCompleted -> "¡Completado! ⭐ (Toca para ver)"
                                                isCurrent -> "🎯 ¡Estación de Destino! (Toca)"
                                                else -> "🔒 Bloqueado"
                                            },
                                            fontSize = 9.sp,
                                            color = if (isCurrent) CandyPink else CandyTextMuted,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // NODE 4: THE BIG TREASURE CHEST (COFRE DEL TESORITO)
                            val allCompleted = points >= 280

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clickable {
                                        if (allCompleted) {
                                            viewModel.triggerMilestone(4)
                                        } else {
                                            viewModel.toastMessage = "¡Falta poco! Necesitas acumular 280 puntos en el barrio para destrabar el Súper Cofre Semanal."
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp) // Huge chest bubble
                                        .background(
                                            brush = if (allCompleted) {
                                                Brush.verticalGradient(listOf(CandyYellow, CandyOrange))
                                            } else {
                                                Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8)))
                                            },
                                            shape = CircleShape
                                        )
                                        .border(3.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (allCompleted) Icons.Default.FavoriteBorder else Icons.Default.Lock,
                                        contentDescription = "Treasure chest",
                                        tint = Color.White,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "🏆 SUPER COFRE SEMANAL (280 pts)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = if (allCompleted) CandyOrange else CandyTextMuted
                                )
                                Text(
                                    text = if (allCompleted) "¡FELICIDADES! Toca para canjear premio gordo" else "Faltan ${280 - points} puntos para destrabar el Cofre Semanal",
                                    fontSize = 9.sp,
                                    color = CandyTextMuted,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Floating Action Button (FAB) for "Comercios Cercanos"
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp), // Styled elegantly floating at the bottom right
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtendedFloatingActionButton(
                onClick = { showNearbyMap = true },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White) },
                text = { Text("Comercios Cercanos 🏪", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp) },
                containerColor = CandyPink,
                contentColor = Color.White,
                modifier = Modifier.testTag("comercios_cercanos_fab")
            )
        }

        // 5. Google Maps Pop-up Dialog!
        if (showNearbyMap) {
            AlertDialog(
                onDismissRequest = { showNearbyMap = false },
                confirmButton = {
                    CandyButton(
                        onClick = { showNearbyMap = false },
                        text = "Terminar Visita ❌",
                        color = CandyPink,
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    )
                },
                title = {
                    Text(
                        text = "🏪 COMERCIOS CERCANOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = CandyPurple
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        VecinoLocalGoogleMapCard(user, merchants, viewModel)
                    }
                },
                containerColor = Color.White
            )
        }
    }
}

// --------------------------------------------------------------------------------------------------
// ROLE 1: ESCÁNER QR SIMULADOR
// --------------------------------------------------------------------------------------------------
@Composable
fun VecinoScanScreen(
    user: UserEntity,
    merchants: List<MerchantEntity>,
    viewModel: RutinViewModel
) {
    val context = LocalContext.current
    var manualHash by remember { mutableStateOf("") }
    
    // Laser bounce animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scan_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column {
                Text(
                    text = "📸 ESCÁNER QR CERTIFICADOR",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple
                )
                Text(
                    text = "El sistema utiliza la geolocalización real de tu dispositivo para certificar tu presencia en el local. Si estás a menos de 50m, escanea el QR correspondiente para acumular tus sellos.",
                    fontSize = 11.sp,
                    color = CandyTextMuted,
                    lineHeight = 14.sp
                )
            }
        }

        // Camera simulated box
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black)
                    .border(3.dp, CandyPink, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Diagonal overlay canvas lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (i in 0..size.width.toInt() step 60) {
                        drawLine(
                            color = Color(0xFFFF2E93).copy(alpha = 0.08f),
                            start = Offset(i.toFloat(), 0f),
                            end = Offset(i.toFloat() - 80f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // ViewFinder bracket
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .border(2.5.dp, Color.White, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Pulsing scanning red line
                    HorizontalDivider(
                        color = CandyPink,
                        thickness = 3.5.dp,
                        modifier = Modifier
                            .offset(y = laserY.dp)
                            .fillMaxWidth(0.9f)
                    )
                }

                Text(
                    text = "GPS LOCALIZADOR SATEUTAL ACTIVO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyTeal,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                )

                Text(
                    text = "COORDENADAS GPS REALES: (${String.format("%.5f", viewModel.simulatedLat)}, ${String.format("%.5f", viewModel.simulatedLng)})",
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(14.dp)
                )
            }
        }

        // Real Hardware GPS Sync Trigger
        item {
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineGranted || coarseGranted) {
                    viewModel.updateLocationFromGps(context)
                } else {
                    viewModel.toastMessage = "❌ Permisos de ubicación denegados."
                }
            }

            CandyButton(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                text = "Sincronizar Ubicación GPS Real 🛰️",
                color = CandyTeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sync_gps_real_btn"),
                icon = Icons.Default.LocationOn
            )
        }

        // Simula selectores de lectura
        item {
            CandyCard(borderColor = CandyPurple.copy(alpha = 0.2f)) {
                Text(
                    text = "🤖 ESCÁNER DE COMERCIOS (PILOTO)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "Selecciona un comercio para escanear de manera presencial. El sistema procesará el código QR estructurado rutinlocal://comercio/{id} verificando el GPS.",
                    fontSize = 10.sp,
                    color = CandyTextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                merchants.forEach { m ->
                    val dist = viewModel.getDistanceToMerchant(m)
                    val inRange = dist <= 50.0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (inRange) CandyTeal.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
                            )
                            .border(1.dp, if (inRange) CandyTeal else Color.LightGray, RoundedCornerShape(16.dp))
                            .clickable {
                                // Simulate scanning the static QR code string which contains rutinlocal://comercio/ID
                                viewModel.scanQrCode("rutinlocal://comercio/${m.id}")
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.name, color = CandyTextDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${m.address} • a ${dist.toInt()}m", color = CandyTextMuted, fontSize = 10.sp)
                            Text("QR: rutinlocal://comercio/${m.id}", color = CandyPurple, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (inRange) CandyTeal else CandyPink)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (inRange) "¡Escanear!" else "Fuera de Rango",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // MANUAL QR CODE ENTRY
                var manualInputQr by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⌨️ PRUEBA MANUAL: ESCRIBIR O PEGAR TEXTO DE QR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CandyPurple
                    )
                    OutlinedTextField(
                        value = manualInputQr,
                        onValueChange = { manualInputQr = it },
                        placeholder = { Text("rutinlocal://comercio/1", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("manual_qr_input_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CandyTextDark,
                            unfocusedTextColor = CandyTextDark,
                            focusedBorderColor = CandyPink,
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    CandyButton(
                        onClick = {
                            if (manualInputQr.isNotBlank()) {
                                viewModel.scanQrCode(manualInputQr)
                            }
                        },
                        text = "Simular Escaneo de Texto QR",
                        color = CandyPurple,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("manual_qr_submit_btn")
                    )
                }
            }
        }
    }
}

// VecinoGpsScreen removed for strict real device GPS geolocating validation version.

// --------------------------------------------------------------------------------------------------
// ROLE 2: COMERCIO DASHBOARD & CAMPAIGN CREATION
// --------------------------------------------------------------------------------------------------
@Composable
fun ComercioDashboard(
    merchantId: Int,
    merchantName: String,
    merchants: List<MerchantEntity>,
    campaigns: List<CampaignEntity>,
    transactions: List<TransactionEntity>,
    users: List<UserEntity>,
    onAddCampaign: () -> Unit,
    viewModel: RutinViewModel
) {
    val storeCampaigns = campaigns.filter { it.merchantId == merchantId }
    val storeStamps = transactions.filter { it.merchantId == merchantId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("comercio_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            CandyCard(borderColor = CandyOrange.copy(alpha = 0.3f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "ESTABLECIMIENTO SOCIO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CandyOrange,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = merchantName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CandyTextDark
                        )
                        Text(
                            text = "Ayudando a que los vecinos jueguen 🛍️",
                            fontSize = 11.sp,
                            color = CandyTextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(CandyOrange.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, CandyOrange, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = CandyOrange, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        // DYNAMIC MERCHANT QR CODE GENERATOR CARD (STATIC DISPLAY FOR PILOT SCANNING)
        item {
            CandyCard(borderColor = CandyPurple.copy(alpha = 0.35f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merchant_qr_card"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎫 CÓDIGO QR DE TU ESTABLECIMIENTO SOCIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CandyPurple,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    
                    Text(
                        text = "Este código QR estático identifica a tu comercio en la red de RutinLocal. Configurado para la simulación presencial o escaneos reales por tus vecinos clientes.",
                        fontSize = 11.sp,
                        color = CandyTextMuted,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    CoexistenciaQrDisplay(
                        content = "rutinlocal://comercio/$merchantId",
                        modifier = Modifier
                            .size(170.dp)
                            .testTag("merchant_qr_display")
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = "rutinlocal://comercio/$merchantId",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CandyTextDark,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Action panel to create campaigns
        item {
            CandyCard(borderColor = CandyTeal.copy(alpha = 0.2f)) {
                Text(
                    text = "⚙️ COMERCIANTE ACCIONES RÁPIDAS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CandyButton(
                        onClick = onAddCampaign,
                        text = "Crear Campaña 🎁",
                        color = CandyTeal,
                        modifier = Modifier.weight(1f).testTag("partner_add_campaign_btn")
                    )
                }
            }
        }

        // --- CASHIER COEXISTENCE RAPID COUPON SCANNER ---
        item {
            val merchantActiveCoupons = remember(transactions) {
                transactions.filter { it.type == "COUPON_ACTIVO" }
            }
            
            var manualCouponText by remember { mutableStateOf("") }
            
            CandyCard(borderColor = CandyTeal) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashier_coupon_scanner_card"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📸 ESCÁNER RÁPIDO DE CUPONES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CandyTeal,
                            letterSpacing = 0.5.sp
                        )
                        Badge(containerColor = CandyTeal) {
                            Text(
                                text = "Caja Activa 📟",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Valida y canjea cupones presentados por tus clientes vecinos escaneando su QR digital. Asegura que el stock de premios e incentivos esté respaldado.",
                        fontSize = 11.sp,
                        color = CandyTextMuted
                    )

                    // CAMERA SCANNING VIEWFINDER SIMULATION
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .border(2.dp, CandyTeal, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "cashier_scanner")
                        val scanLineY by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 100f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scanLine"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            HorizontalDivider(
                                color = CandyTeal,
                                thickness = 3.dp,
                                modifier = Modifier
                                    .offset(y = scanLineY.dp)
                                    .fillMaxWidth(0.9f)
                            )
                        }
                        
                        Text(
                            text = "APUNTA AL CÓDIGO QR EN PANTALLA DEL CLIENTE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                        )
                    }

                    // Display checkout scanning results
                    viewModel.activeCouponScanResult?.let { resText ->
                        val frameColor = if (viewModel.couponScanSuccess == true) CandyTeal else CandyPink
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(frameColor.copy(alpha = 0.08f))
                                .border(1.5.dp, frameColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "RESULTADO DE ESCANEO:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = frameColor
                                )
                                Text(
                                    text = resText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CandyTextDark,
                                    lineHeight = 14.sp
                                )
                                CandyButton(
                                    onClick = { 
                                        viewModel.activeCouponScanResult = null
                                        viewModel.couponScanSuccess = null
                                    },
                                    text = "Cerrar Notificación",
                                    color = frameColor.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // Simulated scans buttons list of currently active coupons in the region
                    val relevantActive = merchantActiveCoupons.filter { it.userId != 0 }
                    if (relevantActive.isNotEmpty()) {
                        Text(
                            text = "🎟️ CLIENTES EN COLA CON CUPONES ACTIVOS (PRESIONAR):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CandyPurple
                        )
                        
                        relevantActive.forEach { activeCoup ->
                            val sUser = users.find { it.id == activeCoup.userId }
                            val sName = sUser?.name ?: "Vecino del Barrio"
                            val cTitle = when (activeCoup.pointsChange) {
                                1 -> "Cupón Bronce: 15% Desc 🎟️"
                                2 -> "Cupón Plata: Bebida Gratis ☕"
                                3 -> "Cupón Oro: Postre Completo 🍰"
                                else -> "Super Cofre de Barrio Semanal 🎁"
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.6f))
                                    .border(1.dp, Color.LightGray.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        // Simulate camera scanning this coupon
                                        viewModel.scanCouponQrCode("rutinlocal://coupon/${activeCoup.id}")
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$cTitle (#${activeCoup.id})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CandyTextDark
                                    )
                                    Text(
                                        text = "Dueño: $sName",
                                        fontSize = 10.sp,
                                        color = CandyTextMuted
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CandyTeal)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("ESCANEAR", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No hay cupones activos pendientes en caja en este momento.",
                            fontSize = 11.sp,
                            color = CandyTextMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // MANUAL INPUT VALIDATOR ESCANER
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "⌨️ ESCANEO SECUENCIA QR MANUAL:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CandyPurple
                        )
                        OutlinedTextField(
                            value = manualCouponText,
                            onValueChange = { manualCouponText = it },
                            placeholder = { Text("rutinlocal://coupon/3", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("cashier_key_input_field"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CandyTextDark,
                                unfocusedTextColor = CandyTextDark,
                                focusedBorderColor = CandyTeal,
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        CandyButton(
                            onClick = {
                                if (manualCouponText.isNotBlank()) {
                                    viewModel.scanCouponQrCode(manualCouponText)
                                }
                            },
                            text = "Validar y Canjear Código de Caja",
                            color = CandyTeal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("cashier_manual_submit_btn")
                        )
                    }
                }
            }
        }

        // Active Campaigns list
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📢 TUS CAMPAÑAS DE PREMIOS ACTIVAS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple
                )

                if (storeCampaigns.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No has creado campañas. ¡Inicia una ahora!", color = CandyTextMuted, fontSize = 11.sp)
                    }
                } else {
                    storeCampaigns.forEach { c ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.8f))
                                .border(1.5.dp, CandyOrange.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .clickable {
                                    // Trigger quick redeem simulation in screen
                                    val client = users.find { it.role == AppRole.VECINO }
                                    if (client != null) {
                                        viewModel.redeemReward(client.id, c.id)
                                    }
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = CandyTextDark)
                                    Text("Tipo: ${c.category}", fontSize = 11.sp, color = CandyTextMuted)
                                    Text("Simulación: Toca para simular canje del Vecino", fontSize = 10.sp, color = CandyPink, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CandyOrange)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${c.costPoints} Estrellas",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Store activity history
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "⚡ HISTORIAL DE SELLOS CAPTURADOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple
                )

                if (storeStamps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ningún vecino ha escaneado sellos aún.", color = CandyTextMuted, fontSize = 11.sp)
                    }
                } else {
                    storeStamps.forEach { tx ->
                        val clientName = users.find { it.id == tx.userId }?.name ?: "Vecino"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.7f))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Face, contentDescription = null, tint = CandyPink, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(clientName, fontSize = 12.sp, fontWeight = FontWeight.Black, color = CandyTextDark)
                                        Text("Sello Certificado", fontSize = 10.sp, color = CandyTextMuted)
                                    }
                                }
                                Text("+${tx.pointsChange} Pts", fontSize = 12.sp, fontWeight = FontWeight.Black, color = CandyTeal)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// ROLE 3: SUPERADMINConsola (Intendente)
// --------------------------------------------------------------------------------------------------
@Composable
fun SuperadminDashboard(
    merchants: List<MerchantEntity>,
    users: List<UserEntity>,
    transactions: List<TransactionEntity>,
    routes: List<RouteEntity>,
    onAddMerchant: () -> Unit,
    onAddRoute: () -> Unit,
    viewModel: RutinViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("superadmin_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            CandyCard(borderColor = CandyPurple.copy(alpha = 0.3f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "CONSOLA DE CONTROL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CandyPurple,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Intendente RutinLocal",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CandyTextDark
                        )
                        Text(
                            text = "Fomentando el comercio del vecindario 🏙️",
                            fontSize = 11.sp,
                            color = CandyTextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(CandyPurple.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, CandyPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = CandyPurple, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        // Actions console
        item {
            CandyCard(borderColor = CandyPink.copy(alpha = 0.2f)) {
                Text(
                    text = "⚙️ ACCIONES ADMINISTRATIVAS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CandyButton(
                        onClick = onAddMerchant,
                        text = "Registrar Socio 🏪",
                        color = CandyPink,
                        modifier = Modifier.weight(1f).testTag("adm_add_merchant_btn")
                    )

                    CandyButton(
                        onClick = onAddRoute,
                        text = "Crear Ruta 🗺️",
                        color = CandyPurple,
                        modifier = Modifier.weight(1f).testTag("adm_add_route_btn")
                    )
                }
            }
        }

        // Stats summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    Pair("Vecinos", users.filter { it.role == AppRole.VECINO }.size.toString()),
                    Pair("Socios", merchants.size.toString()),
                    Pair("Rutas", routes.size.toString()),
                    Pair("Sellos", transactions.filter { it.type == "STAMP" }.size.toString())
                ).forEach { (lbl, valStr) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.8f))
                            .border(1.dp, Color.White, RoundedCornerShape(18.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(valStr, fontWeight = FontWeight.Black, fontSize = 16.sp, color = CandyPink)
                            Text(lbl, fontSize = 9.sp, color = CandyTextMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------------------------------
        // CONTROL DE AUDITORÍA (MÓDULO ANTI-FRAUDE)
        // ------------------------------------------------------------------------------------------
        item {
            val suspiciousUsers = remember(transactions) {
                viewModel.getSuspiciousUsers(transactions)
            }
            
            CandyCard(borderColor = if (suspiciousUsers.isNotEmpty()) CandyPink else CandyTeal.copy(alpha = 0.5f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🛡️ CONTROL DE AUDITORÍA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CandyPurple,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Filtro Anti-Fraude de Coexistencia",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = CandyTextDark
                            )
                        }
                        
                        Badge(
                            containerColor = if (suspiciousUsers.isNotEmpty()) CandyPink else CandyTeal,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (suspiciousUsers.isNotEmpty()) "¡Riesgo de Abuso! 🚨" else "Seguro 🟢",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "El algoritmo audita si un vecino ficha reiteradamente (más de 5 días seguidos) en un único comercio sin registrar interacción en otros locales del mapa activo.",
                        fontSize = 11.sp,
                        color = CandyTextMuted,
                        lineHeight = 14.sp
                    )

                    if (suspiciousUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CandyTeal.copy(alpha = 0.08f))
                                .border(1.5.dp, CandyTeal.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🛡️", fontSize = 24.sp)
                                Column {
                                    Text(
                                        text = "Auditoría Comunal Limpia",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CandyTextDark
                                    )
                                    Text(
                                        text = "Todos los patrones de escaneo cumplen las directivas de coexistencia comunitaria.",
                                        fontSize = 10.sp,
                                        color = CandyTextMuted
                                    )
                                }
                            }
                        }

                        // SIMULATOR INJECTOR
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.02f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🧪 PRUEBA DE ESTRES / SIMULACIÓN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CandyPurple
                            )
                            Text(
                                text = "Forzar un patrón sospechoso de autoconsumo repetitivo para auditar el desvío de costo al socio:",
                                fontSize = 11.sp,
                                color = CandyTextDark,
                                lineHeight = 14.sp
                            )

                            val demoUser = users.firstOrNull { it.role == AppRole.VECINO }
                            val demoMerchant = merchants.firstOrNull()

                            if (demoUser != null && demoMerchant != null) {
                                CandyButton(
                                    onClick = {
                                        viewModel.simulateConsecutiveFraud(demoUser.id, demoMerchant.id)
                                    },
                                    text = "Simular Fraude ${demoUser.name} en ${demoMerchant.name.split(" ").lastOrNull()} 🚨",
                                    color = CandyPurple,
                                    modifier = Modifier.fillMaxWidth().height(38.dp).testTag("simulate_fraud_btn")
                                )
                            }
                        }
                    } else {
                        // SUSPICIOUS ENTRIES DETECTED
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "LISTADO DE SOCIOS Y VECINOS RESTRINGIDOS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CandyPink
                            )

                            suspiciousUsers.forEach { (userId, merchantId) ->
                                val suspectUser = users.find { it.id == userId }
                                val suspectMerchant = merchants.find { it.id == merchantId }

                                if (suspectUser != null && suspectMerchant != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(CandyPink.copy(alpha = 0.08f))
                                            .border(1.5.dp, CandyPink.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("🛑", fontSize = 16.sp)
                                                Text(
                                                    text = suspectUser.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = CandyTextDark
                                                )
                                            }
                                            Text(
                                                text = "TRÁFICO SOSPECHOSO",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CandyPink
                                            )
                                        }

                                        Text(
                                            text = "Fichados consecutivos en '${suspectMerchant.name}' por más de 5 días seguidos con exclusión de toda otra zona.",
                                            fontSize = 11.sp,
                                            color = CandyTextDark,
                                            lineHeight = 14.sp
                                        )

                                        // DIRECT INVOICING / PENALIZATION BANNER
                                        Surface(
                                            color = CandyYellow.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "🎯 PROTECCIÓN DE FONDO COMÚN:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = CandyOrange
                                                )
                                                Text(
                                                    text = "Cualquier canje o premio reclamado por ${suspectUser.name} ha sido redirigido y se deducirá directamente del stock interno/presupuesto propio del socio comercial '${suspectMerchant.name}' por sospecha de autoconsumo.",
                                                    fontSize = 10.sp,
                                                    color = CandyTextDark,
                                                    lineHeight = 13.sp
                                                )
                                            }
                                        }

                                        // Display claimed coupons by suspect
                                        val suspectClaims = transactions.filter { it.userId == suspectUser.id && it.type == "CLAIM" }
                                        if (suspectClaims.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Reclamos Facturados al Comercio (Autoconsumo):",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CandyPurple
                                            )
                                            suspectClaims.forEach { claim ->
                                                val absPoints = if (claim.pointsChange < 0) -claim.pointsChange else claim.pointsChange
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                        .padding(6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "🎁 Premio Canjeado (Costo: $absPoints pts)",
                                                        fontSize = 10.sp,
                                                        color = CandyTextDark
                                                    )
                                                    Text(
                                                        text = "Facturado a ${suspectMerchant.name.split(" ").lastOrNull()}",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = CandyPink
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "Historial: Ningún premio cobrado aún. El fondo común está seguro.",
                                                fontSize = 10.sp,
                                                color = CandyTextMuted,
                                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Partnerships list
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🏪 LISTADO DE SOCIOS REGISTRADOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = CandyPurple
                )

                merchants.forEach { m ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.7f))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(m.name, fontSize = 12.sp, fontWeight = FontWeight.Black, color = CandyTextDark)
                                Text(m.address, fontSize = 10.sp, color = CandyTextMuted)
                            }
                            Text(m.category, fontSize = 10.sp, color = CandyPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// DIALOGS
// --------------------------------------------------------------------------------------------------
@Composable
fun AddMerchantDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, hash: String, lat: Double, lng: Double, cat: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("-12.046374") }
    var lng by remember { mutableStateOf("-77.042793") }
    var category by remember { mutableStateOf("Hostelería") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Nuevo Comercio", fontWeight = FontWeight.Black, color = CandyTextDark, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CandyTextDark,
                    unfocusedTextColor = CandyTextDark,
                    focusedBorderColor = CandyPink,
                    unfocusedBorderColor = Color.LightGray,
                    focusedLabelColor = CandyPink,
                    unfocusedLabelColor = CandyTextMuted
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre socio") }, colors = textFieldColors)
                OutlinedTextField(value = hash, onValueChange = { hash = it }, label = { Text("Código QR Hash") }, colors = textFieldColors)
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitud GPS") }, colors = textFieldColors)
                OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Longitud GPS") }, colors = textFieldColors)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoría") }, colors = textFieldColors)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Dirección") }, colors = textFieldColors)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, hash, lat.toDoubleOrNull() ?: 0.0, lng.toDoubleOrNull() ?: 0.0, category, address)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CandyPink)
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = CandyTextMuted) }
        },
        containerColor = Color.White
    )
}

@Composable
fun AddCampaignDialog(
    merchantId: Int,
    onDismiss: () -> Unit,
    onConfirm: (title: String, points: Int, cat: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("25") }
    var category by remember { mutableStateOf("Café") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Campaña de Premios", fontWeight = FontWeight.Black, color = CandyTextDark, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CandyTextDark,
                    unfocusedTextColor = CandyTextDark,
                    focusedBorderColor = CandyTeal,
                    unfocusedBorderColor = Color.LightGray,
                    focusedLabelColor = CandyTeal,
                    unfocusedLabelColor = CandyTextMuted
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título de Premio (ej: Café Gratis)") }, colors = textFieldColors)
                OutlinedTextField(value = points, onValueChange = { points = it }, label = { Text("Estrellas requeridas") }, colors = textFieldColors)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoría") }, colors = textFieldColors)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(title, points.toIntOrNull() ?: 10, category)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CandyTeal)
            ) {
                Text("Crear Campaña")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = CandyTextMuted) }
        },
        containerColor = Color.White
    )
}

@Composable
fun AddRouteDialog(
    merchantsList: List<MerchantEntity>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, category: String, merchantIds: List<Int>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Café") }
    val selectedIds = remember { mutableStateListOf<Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Ruta Temática", fontWeight = FontWeight.Black, color = CandyTextDark, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CandyTextDark,
                    unfocusedTextColor = CandyTextDark,
                    focusedBorderColor = CandyPurple,
                    unfocusedBorderColor = Color.LightGray,
                    focusedLabelColor = CandyPurple,
                    unfocusedLabelColor = CandyTextMuted
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título de Ruta") }, colors = textFieldColors)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descripción") }, colors = textFieldColors)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoría") }, colors = textFieldColors)
                
                Text("Seleccionar Comercios Participantes:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CandyTextDark)
                Box(modifier = Modifier.height(110.dp)) {
                    LazyColumn {
                        items(merchantsList) { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedIds.contains(m.id)) selectedIds.remove(m.id)
                                        else selectedIds.add(m.id)
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(m.name, fontSize = 11.sp, color = CandyTextDark)
                                Checkbox(
                                    checked = selectedIds.contains(m.id),
                                    onCheckedChange = {
                                        if (selectedIds.contains(m.id)) selectedIds.remove(m.id)
                                        else selectedIds.add(m.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(title, desc, category, selectedIds.toList())
                },
                colors = ButtonDefaults.buttonColors(containerColor = CandyPurple)
            ) {
                Text("Crear Ruta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = CandyTextMuted) }
        },
        containerColor = Color.White
    )
}

// --------------------------------------------------------------------------------------------------
// HIGH-FIDELITY VECTOR/PIXEL QR CODE GENERATOR & DISPLAY
// --------------------------------------------------------------------------------------------------
@Composable
fun CoexistenciaQrDisplay(content: String, modifier: Modifier = Modifier) {
    val eyeColor = CandyPurple
    val dotColor = Color(0xFF1E2022)
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(3.dp, CandyPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSize = 17
            val cellSizeWidth = size.width / gridSize
            val cellSizeHeight = size.height / gridSize
            
            val drawEye: (Float, Float) -> Unit = { left, top ->
                // Outer ring
                drawRect(
                    color = eyeColor,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(cellSizeWidth * 5, cellSizeHeight * 5)
                )
                // White space inside
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left + cellSizeWidth, top + cellSizeHeight),
                    size = androidx.compose.ui.geometry.Size(cellSizeWidth * 3, cellSizeHeight * 3)
                )
                // Solid center
                drawRect(
                    color = eyeColor,
                    topLeft = Offset(left + cellSizeWidth * 1.5f, top + cellSizeHeight * 1.5f),
                    size = androidx.compose.ui.geometry.Size(cellSizeWidth * 2, cellSizeHeight * 2)
                )
            }
            
            // Draw standard QR alignment corners
            // Top-left corner
            drawEye(0f, 0f)
            // Top-right corner
            drawEye(cellSizeWidth * (gridSize - 5), 0f)
            // Bottom-left corner
            drawEye(0f, cellSizeHeight * (gridSize - 5))
            
            // Draw random/deterministic micro noise using string hash
            val hash = content.hashCode()
            val random = java.util.Random(hash.toLong())
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val isTopLeftEye = row < 6 && col < 6
                    val isTopRightEye = row < 6 && col >= gridSize - 6
                    val isBottomLeftEye = row >= gridSize - 6 && col < 6
                    
                    if (!isTopLeftEye && !isTopRightEye && !isBottomLeftEye) {
                        if (random.nextBoolean()) {
                            drawRect(
                                color = dotColor,
                                topLeft = Offset(col * cellSizeWidth, row * cellSizeHeight),
                                size = androidx.compose.ui.geometry.Size(cellSizeWidth, cellSizeHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// NEW OPTIMIZED DECORATIVE VIEWS FOR THE DRAWER NAVIGATION
// --------------------------------------------------------------------------------------------------

@Composable
fun VecinoPerfilScreen(user: UserEntity, viewModel: RutinViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "👤 MI PERFIL DE VECINO",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = CandyPurple
            )
            Text(
                text = "Detalles de tu cuenta de RutinLocal registrada comunalmente.",
                fontSize = 11.sp,
                color = CandyTextMuted
            )
        }

        item {
            CandyCard(borderColor = CandyPink.copy(alpha = 0.2f)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(CandyPink.copy(alpha = 0.1f), CircleShape)
                            .border(2.dp, CandyPink, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = CandyPink,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Text(
                        text = user.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = CandyTextDark
                    )

                    Text(
                        text = user.email,
                        fontSize = 12.sp,
                        color = CandyTextMuted
                    )

                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Puntos", fontSize = 10.sp, color = CandyTextMuted, fontWeight = FontWeight.Bold)
                            Text("${user.points} pts", fontSize = 16.sp, fontWeight = FontWeight.Black, color = CandyOrange)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ciudad", fontSize = 10.sp, color = CandyTextMuted, fontWeight = FontWeight.Bold)
                            Text("Lima", fontSize = 14.sp, fontWeight = FontWeight.Black, color = CandyPurple)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nivel", fontSize = 10.sp, color = CandyTextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = when {
                                    user.points >= 240 -> "Oro 🏆"
                                    user.points >= 180 -> "Plata 🥈"
                                    else -> "Bronce 🥉"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = CandyTeal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VecinoCuponesScreen(
    user: UserEntity,
    transactions: List<TransactionEntity>,
    viewModel: RutinViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("coupons_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "🎟️ HISTORIAL DE CUPONES Y SELLOS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = CandyPurple
            )
            Text(
                text = "Revisa los sellos acumulados y tu historial de transacciones de premios.",
                fontSize = 11.sp,
                color = CandyTextMuted
            )
        }

        val myTx = transactions.filter { it.userId == user.id }.sortedByDescending { it.timestamp }

        if (myTx.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Text("Aún no tienes movimientos registrados.", fontSize = 12.sp, color = CandyTextMuted)
                    }
                }
            }
        } else {
            items(myTx) { tx ->
                val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val dateStr = formatter.format(Date(tx.timestamp))
                
                CandyCard(borderColor = if (tx.type == "STAMP") CandyTeal.copy(alpha = 0.15f) else CandyPink.copy(alpha = 0.15f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (tx.type == "STAMP") "Sello Comunal Obtenido 🎯" else "Recompensa Canjeada 🚀",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = CandyTextDark
                            )
                            Text(
                                text = "Transacción ID: #${tx.id} • $dateStr",
                                fontSize = 9.sp,
                                color = CandyTextMuted
                            )
                            if (tx.verifiedGps) {
                                Text(
                                    text = "✓ GPS Certificado",
                                    fontSize = 8.sp,
                                    color = CandyTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = if (tx.pointsChange >= 0) "+${tx.pointsChange} pts" else "${tx.pointsChange} pts",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = if (tx.pointsChange >= 0) CandyTeal else CandyPink
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VecinoConfigScreen(user: UserEntity, viewModel: RutinViewModel) {
    var pushNotif by remember { mutableStateOf(true) }
    var highPrecisionGps by remember { mutableStateOf(true) }
    var gameSounds by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("config_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "⚙️ CONFIGURACIÓN",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = CandyPurple
            )
            Text(
                text = "Ajusta las opciones del sistema y optimiza el consumo de batería.",
                fontSize = 11.sp,
                color = CandyTextMuted
            )
        }

        item {
            CandyCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notificaciones de cercanía", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CandyTextDark)
                            Text("Alerta al pasar cerca de comercios afiliados.", fontSize = 10.sp, color = CandyTextMuted)
                        }
                        Switch(
                            checked = pushNotif,
                            onCheckedChange = { pushNotif = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CandyPink, checkedTrackColor = CandyPink.copy(alpha = 0.3f))
                        )
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPS Alta Precisión Satelital", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CandyTextDark)
                            Text("Filtra el rango de 50m con máxima precisión de antena.", fontSize = 10.sp, color = CandyTextMuted)
                        }
                        Switch(
                            checked = highPrecisionGps,
                            onCheckedChange = { highPrecisionGps = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CandyTeal, checkedTrackColor = CandyTeal.copy(alpha = 0.3f))
                        )
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Efectos de sonido comunal", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CandyTextDark)
                            Text("Activa alertas al escanear y reclamar.", fontSize = 10.sp, color = CandyTextMuted)
                        }
                        Switch(
                            checked = gameSounds,
                            onCheckedChange = { gameSounds = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CandyOrange, checkedTrackColor = CandyOrange.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RutinLocal v2.4.0 Final Communa Build\nLima, Perú",
                    fontSize = 10.sp,
                    color = CandyTextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

