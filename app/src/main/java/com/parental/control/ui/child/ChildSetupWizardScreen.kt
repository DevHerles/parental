package com.parental.control.ui.child

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.security.ParentalDeviceAdminReceiver
import com.parental.control.ui.theme.AegisPrimary
import com.parental.control.ui.theme.AegisSuccess

@Composable
fun ChildSetupWizardScreen(
    repository: ParentalRepository,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var recoveryCode by remember { mutableStateOf<String?>(null) }

    // Estados de permisos en tiempo real
    var isDeviceAdminActive by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isDeviceAdminActive(context)) }
    var isAccessibilityActive by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isOverlayPermissionGranted by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.canDrawOverlays(context)) }
    var isUsageStatsGranted by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isUsageStatsGranted(context)) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(com.parental.control.core.utils.PermissionHelper.isBatteryOptimizationIgnored(context)) }

    DisposableEffect(Unit) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDeviceAdminActive = com.parental.control.core.utils.PermissionHelper.isDeviceAdminActive(context)
                isAccessibilityActive = com.parental.control.core.utils.PermissionHelper.isAccessibilityServiceEnabled(context)
                isOverlayPermissionGranted = com.parental.control.core.utils.PermissionHelper.canDrawOverlays(context)
                isUsageStatsGranted = com.parental.control.core.utils.PermissionHelper.isUsageStatsGranted(context)
                isBatteryOptimizationIgnored = com.parental.control.core.utils.PermissionHelper.isBatteryOptimizationIgnored(context)
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(listener)
        onDispose { lifecycle?.removeObserver(listener) }
    }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Configuración del Dispositivo Infantil",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Completa estos pasos para blindar el teléfono contra desinstalaciones y bloquear distracciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PASO 1: Establecer PIN Maestro
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Password, contentDescription = null, tint = AegisPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. PIN Maestro de los Padres",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Este PIN será solicitado para cambiar reglas o desbloquear apps en persona.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it },
                        label = { Text("PIN (4 a 6 dígitos)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6) confirmPin = it },
                        label = { Text("Confirmar PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )

                    if (recoveryCode != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AegisSuccess.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = "Código de Rescate: $recoveryCode\n(Guarda este código en caso de olvidar tu PIN)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AegisSuccess,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PASO 2: Administrador de Dispositivo (Anti-desinstalación)
            PermissionStepCard(
                stepNumber = "2",
                title = "Anti-Desinstalación (Device Admin)",
                description = "Evita que la aplicación pueda ser desinstalada normalmente desde los ajustes o el launcher.",
                icon = Icons.Default.AdminPanelSettings,
                isGranted = isDeviceAdminActive,
                actionLabel = "Activar Administrador",
                onAction = {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ParentalDeviceAdminReceiver.getComponentName(context)
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Protección requerida para evitar la desinstalación de Aegis Control Parental."
                        )
                    }
                    context.startActivity(intent)
                    isDeviceAdminActive = checkDeviceAdmin(context)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PASO 3: Servicio de Accesibilidad (Centinela)
            PermissionStepCard(
                stepNumber = "3",
                title = "Centinela en Vivo (Accesibilidad)",
                description = "Requerido para detectar cuando se abre TikTok, YouTube o los Ajustes para bloquearlos.",
                icon = Icons.Default.Visibility,
                isGranted = isAccessibilityActive,
                actionLabel = if (isAccessibilityActive) "Activo ✅" else "Activar Servicio",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            if (!isAccessibilityActive) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ℹ️ ¿Dice 'Ajuste restringido' en tu teléfono?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AegisPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "En Android 13+: Ve a Ajustes del teléfono > Aplicaciones > Aegis Control Parental > toca los 3 puntos (⋮) arriba a la derecha > 'Permitir ajustes restringidos'. Luego regresa a Accesibilidad y actívalo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PASO 4: Dibujar sobre otras apps
            PermissionStepCard(
                stepNumber = "4",
                title = "Pantalla de Bloqueo Flotante",
                description = "Permite mostrar la pantalla de pausa/estudio sobre las aplicaciones restringidas.",
                icon = Icons.Default.Layers,
                isGranted = isOverlayPermissionGranted,
                actionLabel = "Permitir Overlay",
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                    isOverlayPermissionGranted = Settings.canDrawOverlays(context)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PASO 5: Batería sin restricciones
            PermissionStepCard(
                stepNumber = "5",
                title = "Protección Continua (Batería)",
                description = "Impide que el sistema cierre la protección en segundo plano para ahorrar batería.",
                icon = Icons.Default.BatteryChargingFull,
                isGranted = isBatteryOptimizationIgnored,
                actionLabel = "Optimizar Batería",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                    isBatteryOptimizationIgnored = checkBatteryOptimization(context)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de Finalizar y Activar
            Button(
                onClick = {
                    if (pin.length < 4) {
                        pinError = "El PIN debe tener al menos 4 dígitos"
                        return@Button
                    }
                    if (pin != confirmPin) {
                        pinError = "Los PINs no coinciden"
                        return@Button
                    }
                    pinError = null
                    recoveryCode = repository.savePin(pin)

                    onSetupComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AegisPrimary)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Activar Protección Aegis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = AegisPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$stepNumber. $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Concedido",
                        tint = AegisSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun checkDeviceAdmin(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(ParentalDeviceAdminReceiver.getComponentName(context))
}

private fun checkBatteryOptimization(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}
