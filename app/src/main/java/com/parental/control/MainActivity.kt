package com.parental.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parental.control.core.data.ParentalRepository
import com.parental.control.ui.child.ChildSetupWizardScreen
import com.parental.control.ui.navigation.Screen
import com.parental.control.ui.onboarding.RoleSelectionScreen
import com.parental.control.ui.parent.AppManagementScreen
import com.parental.control.ui.parent.ParentDashboardScreen
import com.parental.control.ui.parent.SchedulesScreen
import com.parental.control.ui.theme.AegisParentalTheme
import com.parental.control.ui.theme.AegisPrimary
import com.parental.control.ui.theme.AegisSuccess

class MainActivity : ComponentActivity() {

    private lateinit var repository: ParentalRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ParentalRepository.getInstance(applicationContext)

        setContent {
            AegisParentalTheme {
                val navController = rememberNavController()
                val settings by repository.settings.collectAsState()

                val startDestination = when {
                    settings.isParentMode -> Screen.ParentDashboard.route
                    settings.isChildMode && settings.isConfigured -> Screen.ChildStatus.route
                    settings.isChildMode && !settings.isConfigured -> Screen.ChildSetup.route
                    settings.isConfigured -> Screen.ChildStatus.route
                    else -> Screen.RoleSelection.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(Screen.RoleSelection.route) {
                        RoleSelectionScreen(
                            onSelectChildMode = {
                                repository.setDeviceRole("CHILD")
                                navController.navigate(Screen.ChildSetup.route)
                            },
                            onSelectParentMode = {
                                repository.setDeviceRole("PARENT")
                                navController.navigate(Screen.ParentDashboard.route) {
                                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.ChildSetup.route) {
                        ChildSetupWizardScreen(
                            repository = repository,
                            onSetupComplete = {
                                navController.navigate(Screen.ChildStatus.route) {
                                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.ChildStatus.route) {
                        ChildProtectedStatusScreen(
                            repository = repository,
                            onParentAccessGranted = {
                                repository.setDeviceRole("PARENT")
                                navController.navigate(Screen.ParentDashboard.route)
                            }
                        )
                    }

                    composable(Screen.ParentDashboard.route) {
                        ParentDashboardScreen(
                            repository = repository,
                            onNavigateToApps = {
                                navController.navigate(Screen.ParentAppManagement.route)
                            },
                            onNavigateToSchedules = {
                                navController.navigate(Screen.ParentSchedules.route)
                            },
                            onBack = {
                                repository.setDeviceRole("UNSET")
                                navController.navigate(Screen.RoleSelection.route) {
                                    popUpTo(Screen.ParentDashboard.route) { inclusive = true }
                                }
                            }
                        )
                    }


                    composable(Screen.ParentAppManagement.route) {
                        AppManagementScreen(
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.ParentSchedules.route) {
                        SchedulesScreen(
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChildProtectedStatusScreen(
    repository: ParentalRepository,
    onParentAccessGranted: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var showRevokePinDialog by remember { mutableStateOf(false) }
    var revokePinText by remember { mutableStateOf("") }
    var revokePinError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(AegisSuccess.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AegisSuccess,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Dispositivo Protegido",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Aegis Control Parental está activo y supervisando este dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            val context = LocalContext.current
            var isAccessibilityActive by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isAccessibilityServiceEnabled(context)) }
            var isDeviceAdminActive by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isDeviceAdminActive(context)) }
            var isOverlayActive by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.canDrawOverlays(context)) }
            var isMiuiPopupActive by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isMiuiBackgroundPopupGranted(context)) }

            // Actualizar al volver de Ajustes
            DisposableEffect(Unit) {
                val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isAccessibilityActive = com.parental.control.core.utils.PermissionHelper.isAccessibilityServiceEnabled(context)
                        isDeviceAdminActive = com.parental.control.core.utils.PermissionHelper.isDeviceAdminActive(context)
                        isOverlayActive = com.parental.control.core.utils.PermissionHelper.canDrawOverlays(context)
                        isMiuiPopupActive = com.parental.control.core.utils.PermissionHelper.isMiuiBackgroundPopupGranted(context)
                    }
                }
                val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
                lifecycle?.addObserver(listener)
                onDispose { lifecycle?.removeObserver(listener) }
            }

            if (!isAccessibilityActive) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.parental.control.ui.theme.AegisError.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = com.parental.control.ui.theme.AegisError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACCESIBILIDAD DESACTIVADA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = com.parental.control.ui.theme.AegisError
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Para que el bloqueo de TikTok funcione, debes activar 'Aegis Control Parental' en los Ajustes de Accesibilidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.parental.control.ui.theme.AegisError)
                        ) {
                            Text("Activar Accesibilidad Ahora")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (com.parental.control.core.utils.PermissionHelper.isXiaomiDevice() && !isMiuiPopupActive) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.parental.control.ui.theme.AegisWarning.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = com.parental.control.ui.theme.AegisWarning)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PERMISO XIAOMI PENDIENTE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = com.parental.control.ui.theme.AegisWarning
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "En Xiaomi (HyperOS / MIUI), debes conceder 'Mostrar ventanas emergentes en segundo plano' para que la pantalla de bloqueo se sobreponga inmediatamente a TikTok.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                com.parental.control.core.utils.PermissionHelper.openMiuiPermissionSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.parental.control.ui.theme.AegisWarning)
                        ) {
                            Text("Conceder Permiso Xiaomi")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "• Centinela en Vivo (Accesibilidad): ${if (isAccessibilityActive) "ACTIVO ✅" else "DESACTIVADO ❌"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAccessibilityActive) com.parental.control.ui.theme.AegisSuccess else com.parental.control.ui.theme.AegisError
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Protección Anti-Desinstalación: ${if (isDeviceAdminActive) "ACTIVO ✅" else "INACTIVO ❌"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDeviceAdminActive) com.parental.control.ui.theme.AegisSuccess else com.parental.control.ui.theme.AegisError
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Pantalla Flotante (Overlay): ${if (isOverlayActive) "ACTIVO ✅" else "INACTIVO ❌"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOverlayActive) com.parental.control.ui.theme.AegisSuccess else com.parental.control.ui.theme.AegisError
                    )
                }
            }

            if (!isOverlayActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.parental.control.ui.theme.AegisWarning.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = com.parental.control.ui.theme.AegisWarning)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PANTALLA FLOTANTE INACTIVA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = com.parental.control.ui.theme.AegisWarning
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Para bloquear TikTok visualmente en pantalla, toca el botón y activa 'Permitir mostrar sobre otras aplicaciones'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.parental.control.ui.theme.AegisPrimary)
                        ) {
                            Text("Activar Pantalla Flotante Ahora")
                        }
                    }
                }
            }

            val currentSettings by repository.settings.collectAsState()
            if (currentSettings.isTemporarilyUnlocked) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = com.parental.control.ui.theme.AegisWarning.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = com.parental.control.ui.theme.AegisWarning,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏳ Pausa de Uso Permitida",
                                fontWeight = FontWeight.Bold,
                                color = com.parental.control.ui.theme.AegisWarning
                            )
                            val mins = ((currentSettings.temporaryUnlockUntil - System.currentTimeMillis()) / 60000).coerceAtLeast(1)
                            Text(
                                text = "Tus padres han otorgado $mins min de uso permitido.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showRevokePinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = com.parental.control.ui.theme.AegisError),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Bloquear")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showPinDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AegisPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Acceso a Consola de Padres")
            }
        }

        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPinDialog = false
                    pinText = ""
                    pinError = false
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                ),
                title = { Text("Ingresar PIN de Administrador") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = pinText,
                            onValueChange = {
                                if (it.length <= 6) {
                                    pinText = it
                                    pinError = false
                                }
                            },
                            label = { Text("PIN Maestro") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = pinError,
                            supportingText = if (pinError) {
                                { Text("PIN incorrecto", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (repository.verifyPin(pinText)) {
                                showPinDialog = false
                                onParentAccessGranted()
                            } else {
                                pinError = true
                            }
                        }
                    ) {
                        Text("Acceder")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showRevokePinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRevokePinDialog = false
                    revokePinText = ""
                    revokePinError = false
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                ),
                title = { Text("Cancelar Pausa y Bloquear") },
                text = {
                    Column {
                        Text("Ingresa el PIN Maestro para reactivar el bloqueo inmediato:")
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = revokePinText,
                            onValueChange = {
                                if (it.length <= 6) {
                                    revokePinText = it
                                    revokePinError = false
                                }
                            },
                            label = { Text("PIN Maestro") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = revokePinError,
                            supportingText = if (revokePinError) {
                                { Text("PIN incorrecto", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (repository.verifyPin(revokePinText)) {
                                repository.clearTemporaryUnlock()
                                repository.toggleAppBlock(com.parental.control.core.model.DistractionConstants.PKG_TIKTOK, true)
                                showRevokePinDialog = false
                                revokePinText = ""
                            } else {
                                revokePinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.parental.control.ui.theme.AegisError)
                    ) {
                        Text("Bloquear Ahora")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRevokePinDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
