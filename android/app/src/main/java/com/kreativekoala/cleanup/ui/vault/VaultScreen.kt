package com.kreativekoala.cleanup.ui.vault

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kreativekoala.cleanup.data.model.VaultFileType
import com.kreativekoala.cleanup.data.model.VaultItem
import com.kreativekoala.cleanup.util.ByteFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val items by viewModel.items.collectAsState()

    // PIN setup dialog
    if (uiState.showSetupPin) {
        SetupPinDialog(
            onPinSet = { pin -> viewModel.setPin(pin) }
        )
        return
    }

    if (uiState.isUnlocked) {
        VaultContentScreen(
            items = if (uiState.showFakeContent) emptyList() else items,
            uiState = uiState,
            onLock = { viewModel.lock() },
            onAddItems = { viewModel.showAddSheet() },
            onDismissAddSheet = { viewModel.dismissAddSheet() },
            onSelectItem = { viewModel.selectItem(it) },
            onClearSelectedItem = { viewModel.clearSelectedItem() },
            onAddToVault = { uri, name, type, size ->
                viewModel.addToVault(uri, name, type, size)
            },
            onShowDelete = { viewModel.showDeleteConfirmation() },
            onShowRestore = { viewModel.showRestoreConfirmation() },
            onDismissConfirmations = { viewModel.dismissConfirmations() },
            onDeleteItem = { viewModel.deleteSelectedItem() },
            onRestoreItem = { viewModel.restoreSelectedItem() }
        )
    } else {
        VaultLockScreen(
            enteredPin = uiState.enteredPin,
            pinError = uiState.pinError,
            canUseBiometrics = viewModel.canUseBiometrics(),
            onDigitEntered = { viewModel.onPinDigitEntered(it) },
            onBackspace = { viewModel.onPinBackspace() },
            onBiometricSuccess = { viewModel.onBiometricSuccess() },
            onNavigateBack = onNavigateBack
        )
    }
}

// MARK: - Lock Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultLockScreen(
    enteredPin: String,
    pinError: String?,
    canUseBiometrics: Boolean,
    onDigitEntered: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Auto-trigger biometric on appear
    LaunchedEffect(canUseBiometrics) {
        if (canUseBiometrics && activity != null) {
            showBiometricPrompt(activity, onBiometricSuccess)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Lock icon
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Secret Space",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter your PIN to access",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < enteredPin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Error message
            pinError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Number pad
            NumberPad(
                onDigit = onDigitEntered,
                onBackspace = onBackspace
            )

            Spacer(modifier = Modifier.weight(1f))

            // Biometric button
            if (canUseBiometrics) {
                TextButton(
                    onClick = {
                        if (activity != null) {
                            showBiometricPrompt(activity, onBiometricSuccess)
                        }
                    },
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Biometrics")
                }
            }
        }
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Access Secret Space")
        .setSubtitle("Use your fingerprint or face to unlock")
        .setNegativeButtonText("Use PIN")
        .build()

    prompt.authenticate(promptInfo)
}

// MARK: - Number Pad

@Composable
private fun NumberPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        numbers.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                row.forEach { key ->
                    NumberPadButton(
                        key = key,
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "" -> {}
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberPadButton(key: String, onClick: () -> Unit) {
    if (key.isEmpty()) {
        Spacer(modifier = Modifier.size(70.dp))
        return
    }

    Surface(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (key == "⌫") Color.Transparent
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (key == "⌫") {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = "Delete",
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    key,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// MARK: - Setup PIN Dialog

@Composable
private fun SetupPinDialog(onPinSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                if (isConfirming) "Confirm Your PIN" else "Create Your PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                if (isConfirming) "Enter the same PIN again"
                else "Choose a 4-digit PIN to protect your vault",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN dots
            val currentPin = if (isConfirming) confirmPin else pin
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < currentPin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            if (showError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "PINs don't match. Please try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            NumberPad(
                onDigit = { digit ->
                    if (isConfirming) {
                        if (confirmPin.length < 4) {
                            confirmPin += digit
                            if (confirmPin.length == 4) {
                                if (pin == confirmPin) {
                                    onPinSet(pin)
                                } else {
                                    showError = true
                                    pin = ""
                                    confirmPin = ""
                                    isConfirming = false
                                }
                            }
                        }
                    } else {
                        if (pin.length < 4) {
                            pin += digit
                            showError = false
                            if (pin.length == 4) {
                                isConfirming = true
                            }
                        }
                    }
                },
                onBackspace = {
                    if (isConfirming) {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    } else {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// MARK: - Vault Content Screen (Unlocked)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContentScreen(
    items: List<VaultItem>,
    uiState: VaultViewModel.UiState,
    onLock: () -> Unit,
    onAddItems: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onClearSelectedItem: () -> Unit,
    onAddToVault: (Uri, String, VaultFileType, Long) -> Unit,
    onShowDelete: () -> Unit,
    onShowRestore: () -> Unit,
    onDismissConfirmations: () -> Unit,
    onDeleteItem: () -> Unit,
    onRestoreItem: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secret Space") },
                navigationIcon = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock")
                    }
                },
                actions = {
                    IconButton(onClick = onAddItems) {
                        Icon(Icons.Default.Add, contentDescription = "Add items")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Your vault is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Add photos and videos to keep them private",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = onAddItems) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Items")
                    }
                }
            }
        } else {
            // Grid of vault items
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    VaultItemThumbnail(
                        item = item,
                        onClick = { onSelectItem(item) }
                    )
                }
            }
        }

        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Adding to vault...")
                    }
                }
            }
        }
    }

    // Add sheet
    if (uiState.showAddSheet) {
        AddToVaultSheet(
            onDismiss = onDismissAddSheet,
            onMediaSelected = { uri, name, type, size ->
                onAddToVault(uri, name, type, size)
            }
        )
    }

    // Item detail sheet
    uiState.selectedItem?.let { item ->
        ItemDetailSheet(
            item = item,
            onDismiss = onClearSelectedItem,
            onRestore = onShowRestore,
            onDelete = onShowDelete
        )
    }

    // Delete confirmation
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissConfirmations,
            title = { Text("Delete Item") },
            text = { Text("This will permanently delete the item from your vault.") },
            confirmButton = {
                TextButton(onClick = onDeleteItem) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmations) { Text("Cancel") }
            }
        )
    }

    // Restore confirmation
    if (uiState.showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissConfirmations,
            title = { Text("Restore Item") },
            text = { Text("This will restore the item to your Photos library.") },
            confirmButton = {
                TextButton(onClick = onRestoreItem) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmations) { Text("Cancel") }
            }
        )
    }
}

// MARK: - Vault Item Thumbnail

@Composable
private fun VaultItemThumbnail(item: VaultItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        if (item.thumbnailPath != null) {
            AsyncImage(
                model = File(item.thumbnailPath),
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (item.fileType) {
                        VaultFileType.PHOTO -> Icons.Default.Photo
                        VaultFileType.VIDEO -> Icons.Default.Videocam
                        VaultFileType.DOCUMENT -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Video indicator
        if (item.fileType == VaultFileType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(2.dp)
                    )
                }
            }
        }
    }
}

// MARK: - Add To Vault Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToVaultSheet(
    onDismiss: () -> Unit,
    onMediaSelected: (Uri, String, VaultFileType, Long) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        for (uri in uris) {
            // Get file info
            var fileName = "Unknown"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "Unknown"
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: ""
            val fileType = when {
                mimeType.startsWith("video/") -> VaultFileType.VIDEO
                mimeType.startsWith("image/") -> VaultFileType.PHOTO
                else -> VaultFileType.DOCUMENT
            }

            // Take persistent permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            onMediaSelected(uri, fileName, fileType, fileSize)
        }
        if (uris.isEmpty()) onDismiss()
    }

    // Photo/Video picker
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        for (uri in uris) {
            var fileName = "Unknown"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "Unknown"
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: ""
            val fileType = when {
                mimeType.startsWith("video/") -> VaultFileType.VIDEO
                else -> VaultFileType.PHOTO
            }

            onMediaSelected(uri, fileName, fileType, fileSize)
        }
        if (uris.isEmpty()) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "Add to Vault",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Select items to add to your vault",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Photos & Videos option
            Button(
                onClick = {
                    mediaLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select from Photos")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Files option
            OutlinedButton(
                onClick = {
                    launcher.launch(arrayOf("image/*", "video/*", "application/pdf"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select from Files")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// MARK: - Item Detail Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailSheet(
    item: VaultItem,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumbnailPath != null) {
                    AsyncImage(
                        model = File(item.thumbnailPath),
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        when (item.fileType) {
                            VaultFileType.PHOTO -> Icons.Default.Photo
                            VaultFileType.VIDEO -> Icons.Default.Videocam
                            VaultFileType.DOCUMENT -> Icons.Default.Description
                        },
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File info
            Text(
                item.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Added: ${dateFormat.format(Date(item.addedDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Size: ${ByteFormatter.format(item.fileSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
