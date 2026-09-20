package com.parental.control.ui.parent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.DistractionConstants
import com.parental.control.sync.TursoParentManager
import com.parental.control.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    repository: ParentalRepository,
    onNavigateToApps: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val parentManager = remember { TursoParentManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Sondeo de Turso Cloud mientras la pantalla está activa
    DisposableEffect(Unit) {
        parentManager.startPolling()
        onDispose {
            parentManager.stopPolling()
        }
    }

    val childDevice by parentManager.activeChildDevice.collectAsState()
    val childSettings by parentManager.childSettings.collectAsState()
    val childApps by parentManager.childAppRestrictions.collectAsState()
    val tamperLogs by parentManager.recentTamperLogs.collectAsState()
    val lastPingRtt by parentManager.lastPingRtt.collectAsState()
    val isPinging by parentManager.isPinging.collectAsState()
    val isOperating by parentManager.isOperating.collectAsState()
    val statusMessage by parentManager.statusMessage.collectAsState()

    val targetDeviceId = childDevice?.deviceId ?: "child_tb330xu_ae58b6"
    val deviceName = childDevice?.deviceName?.ifEmpty { childDevice?.model } ?: "Lenovo Tab M11"

    // Reloj para cálculo dinámico de cuenta regresiva
    var currentClockTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentClockTime = System.currentTimeMillis()
        }
    }

    val lastSeen = childDevice?.lastSeenAt ?: 0L
    val diffSec = if (lastSeen > 0L) ((currentClockTime - lastSeen) / 1000).coerceAtLeast(0) else 999L
    val isOnline = diffSec <= 25L

    var showCustomUnlockDialog by remember { mutableStateOf(false) }
    var customUnlockMinutesText by remember { mutableStateOf("45") }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Aegis Consola Parental",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isOnline) AegisSuccess.copy(alpha = 0.2f) else AegisError.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isOnline) "ONLINE" else "OFFLINE",
                                    color = if (isOnline) AegisSuccess else AegisError,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "$deviceName ($targetDeviceId)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { parentManager.refreshAllData() } },
                        enabled = !isOperating
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar Turso")
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cambiar Rol")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Banner de Estado o Feedback de Operaciones
            AnimatedVisibility(visible = !statusMessage.isNullOrEmpty()) {
                Surface(
                    color = AegisPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = AegisPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = AegisPrimary
                        )
                    }
                }
            }

            // 1. Tarjeta de Estado del Dispositivo en Vivo (Telemetría Turso Cloud)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AegisPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isOnline) AegisSuccess else Color.Red, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "🟢 CONECTADO (hace ${diffSec}s)" else "🔴 DESCONECTADO",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (childDevice?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${childDevice?.batteryPercent ?: 100}%${if (childDevice?.isCharging == true) " ⚡" else ""}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val isInstantLocked = childSettings?.isInstantLockActive == true
                    val unlockUntil = childSettings?.temporaryUnlockUntil ?: 0L
                    val isTempUnlocked = unlockUntil > currentClockTime && !isInstantLocked

                    if (isInstantLocked) {
                        Text(
                            text = "🚨 Bloqueo Total Activo",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "La tablet está congelada. Ninguna app puede ejecutarse.",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (isTempUnlocked) {
                        val remSec = ((unlockUntil - currentClockTime) / 1000).coerceAtLeast(0)
                        val remM = remSec / 60
                        val remS = remSec % 60
                        Text(
                            text = "⏱️ Pausa Activa: ${remM}m ${remS}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Uso libre temporal concedido. La pantalla de bloqueo está abierta.",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "🛡️ Modo Protegido Estándar",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Centinela activo • Anti-desinstalación blindada 24/7",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // App actual en pantalla
                    val currentApp = childDevice?.currentForegroundApp?.takeIf { it.isNotEmpty() } ?: "Launcher / Inicio"
                    val isCurrentAppBlocked = childApps.find { it.packageName == currentApp }?.isBlocked == true

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App activa en pantalla:",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = currentApp,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentApp != "Launcher / Inicio" && !currentApp.contains("launcher") && !currentApp.contains("aegis")) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val newBlocked = !isCurrentAppBlocked
                                        val appName = DistractionConstants.getFriendlyAppName(currentApp)
                                        parentManager.setAppBlocked(targetDeviceId, currentApp, appName, newBlocked)
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isCurrentAppBlocked) AegisSuccess else AegisError,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                enabled = !isOperating
                            ) {
                                Text(
                                    text = if (isCurrentAppBlocked) "Permitir" else "Bloquear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Control de Desbloqueo Temporal / Reanudar Bloqueo
            val isInstantLocked = childSettings?.isInstantLockActive == true
            val unlockUntil = childSettings?.temporaryUnlockUntil ?: 0L
            val isTempUnlocked = unlockUntil > currentClockTime && !isInstantLocked

            if (isTempUnlocked) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AegisWarning.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "⏳ Recreo Temporal en Curso",
                                    fontWeight = FontWeight.Bold,
                                    color = AegisWarning
                                )
                                val mins = ((unlockUntil - currentClockTime) / 60000).coerceAtLeast(1)
                                Text(
                                    text = "Restan aprox. $mins min autorizados",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch { parentManager.resumeProtection(targetDeviceId) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AegisError),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isOperating
                            ) {
                                Text("Bloquear Ahora")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                // Selector Rápido de Autorización Temporal
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = AegisPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Conceder Tiempo de Recreo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 30, 60).forEach { mins ->
                                Button(
                                    onClick = {
                                        scope.launch { parentManager.unlockDevice(targetDeviceId, mins) }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AegisPrimary.copy(alpha = 0.9f)),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    enabled = !isOperating
                                ) {
                                    Text("+$mins min", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { showCustomUnlockDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                enabled = !isOperating
                            ) {
                                Text("Más...", fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. Tarjeta de Bloqueo Instantáneo
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInstantLocked) AegisError.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    color = if (isInstantLocked) AegisError else AegisError.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isInstantLocked) Color.White else AegisError
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Bloqueo Instantáneo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isInstantLocked) "Tablet congelada ahora" else "Pausar todo (Cena / Dormir)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isInstantLocked,
                        onCheckedChange = { lock ->
                            scope.launch {
                                if (lock) {
                                    parentManager.lockDevice(targetDeviceId)
                                } else {
                                    parentManager.resumeProtection(targetDeviceId)
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AegisError),
                        enabled = !isOperating
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Diagnóstico de Conexión en Vivo (Ping a Turso Cloud)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(AegisSecondary.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NetworkPing, contentDescription = null, tint = AegisSecondary)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Latencia Remota (Ping)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (lastPingRtt != null) "🏓 RTT: ${lastPingRtt}ms" else "Sin medir recientemente",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (lastPingRtt != null) AegisSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (lastPingRtt != null) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Button(
                        onClick = { scope.launch { parentManager.pingDevice(targetDeviceId) } },
                        enabled = !isPinging && !isOperating,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AegisSecondary)
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Probar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Control Rápido de Apps Distractoras Populares (Turso Cloud)
            Text(
                text = "Control Rápido de Apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            val appList = listOf(
                Triple("TikTok", DistractionConstants.PKG_TIKTOK, Icons.Default.MusicVideo),
                Triple("YouTube", DistractionConstants.PKG_YOUTUBE, Icons.Default.PlayCircle),
                Triple("Roblox / Juegos", DistractionConstants.PKG_ROBLOX, Icons.Default.SportsEsports),
                Triple("Facebook / Instagram", DistractionConstants.PKG_FACEBOOK, Icons.Default.Share)
            )

            appList.forEach { (name, pkg, icon) ->
                val restriction = childApps.find { it.packageName == pkg }
                val isBlocked = restriction?.isBlocked ?: true

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isBlocked) AegisError.copy(alpha = 0.1f) else AegisSuccess.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isBlocked) AegisError else AegisSuccess,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isBlocked) "🚫 Bloqueada" else "✅ Permitida",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isBlocked) AegisError else AegisSuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Switch(
                            checked = isBlocked,
                            onCheckedChange = { blocked ->
                                scope.launch {
                                    parentManager.setAppBlocked(targetDeviceId, pkg, name, blocked)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AegisError),
                            enabled = !isOperating
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Navegación a Módulos Avanzados
            Text(
                text = "Herramientas Avanzadas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                onClick = onNavigateToApps,
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(AegisPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = AegisPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = "Gestión de Todas las Apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(text = "Bloquear o permitir cualquier aplicación de la tablet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                onClick = onNavigateToSchedules,
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(AegisPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = AegisPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = "Horarios y Toque de Queda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(text = "Toque de queda y horas de estudio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Registro de Evasión y Anti-Tampering (Turso Cloud)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AegisPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Seguridad del Dispositivo (Turso Cloud)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (tamperLogs.isEmpty()) {
                        Text(
                            text = "✅ Cero violaciones recientes. El dispositivo está seguro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AegisSuccess
                        )
                    } else {
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        tamperLogs.take(4).forEach { log ->
                            val timeStr = sdf.format(Date(log.timestamp))
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "[$timeStr] ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "🛡️ ${log.eventType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AegisError,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = log.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Diálogo para desbloqueo temporal personalizado
        if (showCustomUnlockDialog) {
            AlertDialog(
                onDismissRequest = { showCustomUnlockDialog = false },
                title = { Text("Tiempo de Recreo Personalizado") },
                text = {
                    Column {
                        Text(
                            text = "Ingresa los minutos de uso permitidos para la tablet:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customUnlockMinutesText,
                            onValueChange = { customUnlockMinutesText = it.filter { c -> c.isDigit() } },
                            label = { Text("Minutos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val mins = customUnlockMinutesText.toIntOrNull() ?: 15
                            showCustomUnlockDialog = false
                            scope.launch { parentManager.unlockDevice(targetDeviceId, mins) }
                        }
                    ) {
                        Text("Autorizar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomUnlockDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
