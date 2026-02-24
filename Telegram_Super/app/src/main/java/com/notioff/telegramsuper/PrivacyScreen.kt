package com.notioff.telegramsuper

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    var allowSensitiveContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TelegramClient.getOptionBoolean("ignore_sensitive_content_restrictions") { value ->
            allowSensitiveContent = value
        }
    }
    
    val ageVerificationBot by TelegramClient.ageVerificationBotUsername.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        text = "Show 18+ Content",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Turn on to show sensitive media in your chats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = allowSensitiveContent,
                    onCheckedChange = { isChecked ->
                        allowSensitiveContent = isChecked
                        TelegramClient.setOptionBoolean("ignore_sensitive_content_restrictions", isChecked)
                    }
                )
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ignore Content Restrictions",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Bypass 'This channel can't be displayed' errors for restricted channels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = allowSensitiveContent,
                    onCheckedChange = { isChecked ->
                        allowSensitiveContent = isChecked
                        TelegramClient.setOptionBoolean("ignore_sensitive_content_restrictions", isChecked)
                    }
                )
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            
            val targetBot = ageVerificationBot ?: "VerifyBot"
            
            Surface(
                onClick = {
                    TelegramClient.resolveBotAndOpenChat(targetBot) { chatId ->
                        if (chatId != null) {
                            onOpenChat(chatId)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
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
                            text = "Age Verification",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Verify your age with @$targetBot to unlock restricted content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }
    }
}
