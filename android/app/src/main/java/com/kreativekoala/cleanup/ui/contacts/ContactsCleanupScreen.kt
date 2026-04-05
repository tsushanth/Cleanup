package com.kreativekoala.cleanup.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.R
import com.kreativekoala.cleanup.data.model.ContactItem
import com.kreativekoala.cleanup.data.model.DuplicateContactGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsCleanupScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    viewModel: ContactsCleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.contacts_tab_duplicates),
        stringResource(R.string.contacts_tab_incomplete)
    )

    // Permission handling
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val contactPermissions = remember {
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) viewModel.scan()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.scan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (!hasPermission) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(Icons.Default.Contacts, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.contacts_access_required), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.contacts_access_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { permissionLauncher.launch(contactPermissions) }, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.contacts_grant_access))
                    }
                }
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> DuplicatesTab(
                        groups = uiState.duplicateGroups,
                        isScanning = uiState.isScanning,
                        viewModel = viewModel
                    )
                    1 -> IncompleteTab(
                        contacts = uiState.incompleteContacts,
                        isScanning = uiState.isScanning,
                        viewModel = viewModel
                    )
                }

                if (uiState.isScanning) {
                    ScanningOverlay()
                }
            }
        }
    }

    // Merge sheet
    if (uiState.showMergeSheet && uiState.mergeGroup != null) {
        MergeContactsSheet(
            group = uiState.mergeGroup!!,
            onMerge = { index ->
                viewModel.mergeContacts(uiState.mergeGroup!!, index)
            },
            onDismiss = { viewModel.dismissMergeSheet() }
        )
    }

    // Paywall dialog
    if (uiState.showPaywall) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPaywall() },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
            title = { Text(stringResource(R.string.upgrade_title), textAlign = TextAlign.Center) },
            text = {
                Text(
                    stringResource(R.string.upgrade_message),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPaywall()
                    onNavigateToPaywall()
                }) {
                    Text(stringResource(R.string.upgrade_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPaywall() }) {
                    Text(stringResource(R.string.upgrade_not_now))
                }
            }
        )
    }
}

// MARK: - Duplicates Tab

@Composable
private fun DuplicatesTab(
    groups: List<DuplicateContactGroup>,
    isScanning: Boolean,
    viewModel: ContactsCleanupViewModel
) {
    if (groups.isEmpty() && !isScanning) {
        EmptyState(title = stringResource(R.string.contacts_no_duplicates_title), subtitle = stringResource(R.string.contacts_no_duplicates_subtitle))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                DuplicateGroupCard(group = group, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateContactGroup,
    viewModel: ContactsCleanupViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.contacts_n_duplicates, group.contacts.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.15f)
                ) {
                    Text(
                        group.matchReason.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contact rows
            group.contacts.forEach { contact ->
                ContactRow(contact = contact)
                if (contact != group.contacts.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.showMergeSheet(group) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.contacts_merge_all))
                }
                OutlinedButton(
                    onClick = { viewModel.deleteDuplicatesInGroup(group) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.contacts_delete_dupes))
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                contact.initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.displayName.ifEmpty { stringResource(R.string.contacts_no_name) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (contact.phoneNumbers.isNotEmpty()) {
                Text(
                    contact.phoneNumbers.first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (contact.emailAddresses.isNotEmpty()) {
                Text(
                    contact.emailAddresses.first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// MARK: - Incomplete Tab

@Composable
private fun IncompleteTab(
    contacts: List<ContactItem>,
    isScanning: Boolean,
    viewModel: ContactsCleanupViewModel
) {
    if (contacts.isEmpty() && !isScanning) {
        EmptyState(title = stringResource(R.string.contacts_no_incomplete_title), subtitle = stringResource(R.string.contacts_no_incomplete_subtitle))
    } else {
        var selectAll by remember { mutableStateOf(false) }

        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.contacts_n_incomplete, contacts.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    selectAll = !selectAll
                    viewModel.selectAllIncomplete(selectAll)
                }) {
                    Text(if (selectAll) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.contactId }) { contact ->
                    IncompleteContactRow(
                        contact = contact,
                        isSelected = viewModel.isIncompleteSelected(contact),
                        onClick = { viewModel.toggleIncompleteSelection(contact) }
                    )
                }
            }

            // Delete button
            AnimatedVisibility(visible = viewModel.selectedIncompleteCount > 0) {
                Button(
                    onClick = { viewModel.deleteSelectedIncomplete() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.contacts_delete_selected, viewModel.selectedIncompleteCount))
                }
            }
        }
    }
}

@Composable
private fun IncompleteContactRow(
    contact: ContactItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactRow(contact = contact)

            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Merge Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeContactsSheet(
    group: DuplicateContactGroup,
    onMerge: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.contacts_merge_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.contacts_merge_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            group.contacts.forEachIndexed { index, contact ->
                val isSelected = selectedIndex == index
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedIndex = index },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactRow(contact = contact)
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onMerge(selectedIndex) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.contacts_merge_button))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// MARK: - Scanning Overlay

@Composable
private fun ScanningOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.contacts_scanning), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// MARK: - Empty State

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
