package com.parental.control.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.AppCategory
import com.parental.control.core.model.AppRestriction
import com.parental.control.core.model.DistractionConstants
import com.parental.control.sync.TursoParentManager
import com.parental.control.ui.theme.AegisError
import com.parental.control.ui.theme.AegisPrimary
import com.parental.control.ui.theme.AegisSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(
    repository: ParentalRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val parentManager = remember { TursoParentManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val isParentMode = repository.settings.collectAsState().value.isParentMode

    val activeChildDevice by parentManager.activeChildDevice.collectAsState()
    val targetDeviceId = activeChildDevice?.deviceId ?: "child_tb330xu_ae58b6"

    val localRestrictions by repository.restrictions.collectAsState()
    val cloudRestrictions by parentManager.childAppRestrictions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var customPackage by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    // Lista combinada de apps
    val appList = remember(isParentMode, localRestrictions, cloudRestrictions, searchQuery) {
        val list = if (isParentMode) {
            val map = mutableMapOf<String, AppRestriction>()
            // Primero predeterminadas
            for (pkg in DistractionConstants.DEFAULT_BLOCKED_PACKAGES) {
                map[pkg] = AppRestriction(
                    packageName = pkg,
                    appName = DistractionConstants.getFriendlyAppName(pkg),
                    isBlocked = true
                )
            }
            // Sobrescribir con lo que venga de Turso Cloud
            for (cr in cloudRestrictions) {
                map[cr.packageName] = AppRestriction(
                    packageName = cr.packageName,
                    appName = cr.appName.ifEmpty { DistractionConstants.getFriendlyAppName(cr.packageName) },
                    isBlocked = cr.isBlocked
                )
            }
            map.values.toList()
        } else {
            localRestrictions.values.toList()
        }

        list.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }.sortedBy { it.appName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestión de Aplicaciones", fontWeight = FontWeight.Bold)
                        if (isParentMode) {
                            Text(
                                text = "Tablet: $targetDeviceId (Turso Cloud)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar App")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar aplicación o paquete...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appList, key = { it.packageName }) { app ->
                    AppRestrictionRow(
                        app = app,
                        onToggle = { isBlocked ->
                            if (isParentMode) {
                                scope.launch {
                                    parentManager.setAppBlocked(
                                        targetDeviceId,
                                        app.packageName,
                                        app.appName,
                                        isBlocked
                                    )
                                }
                            } else {
                                repository.toggleAppBlock(app.packageName, isBlocked)
                            }
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Bloquear Nueva Aplicación") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Nombre de la App (ej. Minecraft)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customPackage,
                            onValueChange = { customPackage = it },
                            label = { Text("Paquete (ej. com.mojang.minecraftpe)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pkg = customPackage.trim()
                            val name = customName.trim().ifEmpty { pkg }
                            if (pkg.isNotBlank()) {
                                if (isParentMode) {
                                    scope.launch {
                                        parentManager.setAppBlocked(targetDeviceId, pkg, name, true)
                                    }
                                } else {
                                    repository.toggleAppBlock(pkg, true)
                                }
                                customPackage = ""
                                customName = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("Bloquear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun AppRestrictionRow(
    app: AppRestriction,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            color = if (app.isBlocked) AegisError.copy(alpha = 0.12f) else AegisSuccess.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (app.isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (app.isBlocked) AegisError else AegisSuccess,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = app.isBlocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = AegisError)
            )
        }
    }
}
