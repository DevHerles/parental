package com.parental.control.ui.parent

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.DistractionConstants
import com.parental.control.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    repository: ParentalRepository,
    onNavigateToApps: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onBack: () -> Unit
) {
    val settings by repository.settings.collectAsState()
    val restrictions by repository.restrictions.collectAsState()
    val telemetry by repository.telemetry.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Aegis Consola Parental",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dispositivo de la Hija (11 años)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Salir")
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
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta de Estado del Dispositivo en Vivo
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AegisPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(AegisSuccess, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESTADO: PROTEGIDO",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${telemetry.batteryPercentage}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Protección Total Activa",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Anti-desinstalación bloqueada • Centinela 24/7 activo",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (settings.isTemporarilyUnlocked) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AegisWarning.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏳ Pausa Temporal Activa",
                                fontWeight = FontWeight.Bold,
                                color = AegisWarning
                            )
                            val mins = ((settings.temporaryUnlockUntil - System.currentTimeMillis()) / 60000).coerceAtLeast(1)
                            Text(
                                text = "Restan $mins min de uso permitido",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { repository.clearTemporaryUnlock() },
                            colors = ButtonDefaults.buttonColors(containerColor = AegisError),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Bloquear Ahora")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de Bloqueo Instantáneo
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.isInstantLockActive) AegisError.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    color = if (settings.isInstantLockActive) AegisError else Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = if (settings.isInstantLockActive) Color.White else AegisError
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
                                text = if (settings.isInstantLockActive) "El teléfono está congelado ahora" else "Pausar todo (Cena / Dormir)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = settings.isInstantLockActive,
                        onCheckedChange = { repository.setInstantLock(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AegisError)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Switch de Protección Anti-Desinstalación
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    color = if (settings.isAntiUninstallActive) AegisPrimary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (settings.isAntiUninstallActive) AegisPrimary else Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Protección Anti-Desinstalación",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings.isAntiUninstallActive) "Activa (Bloquea borrar app o Ajustes)" else "Inactiva (Permite entrar a Ajustes)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = settings.isAntiUninstallActive,
                        onCheckedChange = { repository.setAntiUninstallActive(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AegisPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Control Rápido de Distracciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Switches Rápidos para TikTok, YouTube, Facebook, Instagram
            QuickAppToggleCard(
                title = "TikTok",
                subtitle = "Videos cortos adictivos",
                icon = Icons.Default.MusicVideo,
                isBlocked = restrictions[DistractionConstants.PKG_TIKTOK]?.isBlocked ?: true,
                onToggle = { repository.toggleAppBlock(DistractionConstants.PKG_TIKTOK, it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickAppToggleCard(
                title = "YouTube y YouTube Music",
                subtitle = "Transmisión de video continua",
                icon = Icons.Default.PlayCircle,
                isBlocked = restrictions[DistractionConstants.PKG_YOUTUBE]?.isBlocked ?: true,
                onToggle = { repository.toggleAppBlock(DistractionConstants.PKG_YOUTUBE, it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickAppToggleCard(
                title = "Facebook e Instagram",
                subtitle = "Redes sociales y feeds",
                icon = Icons.Default.Share,
                isBlocked = restrictions[DistractionConstants.PKG_FACEBOOK]?.isBlocked ?: true,
                onToggle = {
                    repository.toggleAppBlock(DistractionConstants.PKG_FACEBOOK, it)
                    repository.toggleAppBlock(DistractionConstants.PKG_INSTAGRAM, it)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickAppToggleCard(
                title = "Roblox y Juegos",
                subtitle = "Juegos en línea y compras",
                icon = Icons.Default.SportsEsports,
                isBlocked = restrictions[DistractionConstants.PKG_ROBLOX]?.isBlocked ?: true,
                onToggle = { repository.toggleAppBlock(DistractionConstants.PKG_ROBLOX, it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Herramientas de Configuración",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Botón a Gestión Completa de Apps
            NavigationActionCard(
                title = "Gestión Avanzada de Apps",
                subtitle = "Bloquear o permitir cualquier app instalada",
                icon = Icons.Default.Apps,
                onClick = onNavigateToApps
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Botón a Horarios y Toque de Queda
            NavigationActionCard(
                title = "Horarios y Toque de Queda",
                subtitle = "Horarios de estudio y descanso programados",
                icon = Icons.Default.Schedule,
                onClick = onNavigateToSchedules
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tarjeta de Registro Anti-Manipulación
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AegisPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Seguridad del Dispositivo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Intentos de entrar a Ajustes bloqueados: ${telemetry.tamperingAttemptsCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (telemetry.lastTamperDetail.isNotEmpty()) {
                        Text(
                            text = "• Último incidente: ${telemetry.lastTamperDetail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AegisError
                        )
                    }

                    Text(
                        text = "• Filtro Web en navegadores: ${if (settings.isWebFilterActive) "ACTIVO" else "DESACTIVADO"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun QuickAppToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = isBlocked,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = AegisError)
                )
                Text(
                    text = if (isBlocked) "Bloqueado" else "Permitido",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBlocked) AegisError else AegisSuccess,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NavigationActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
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
                        .background(
                            color = AegisPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AegisPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
