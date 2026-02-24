package com.notioff.telegramsuper

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.drinkless.tdlib.TdApi

@Composable
fun AuthScreen(viewModel: AuthViewModel = viewModel()) {
    val authState by viewModel.authState.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        lastError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        when (authState) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                var phone by remember { mutableStateOf("") }
                Text("Enter Phone Number:")
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") }
                )
                Button(onClick = { viewModel.sendPhoneNumber(phone) }, modifier = Modifier.padding(top=8.dp)) {
                    Text("Send")
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                var code by remember { mutableStateOf("") }
                Text("Enter Auth Code:")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code") }
                )
                Button(onClick = { viewModel.sendAuthCode(code) }, modifier = Modifier.padding(top=8.dp)) {
                    Text("Verify")
                }
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                var password by remember { mutableStateOf("") }
                Text("Enter 2FA Password:")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") }
                )
                Button(onClick = { viewModel.sendPassword(password) }, modifier = Modifier.padding(top=8.dp)) {
                    Text("Verify")
                }
            }
            is TdApi.AuthorizationStateReady -> {
                Text("Logged in Successfully!")
            }
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                var apiId by remember { mutableStateOf("") }
                var apiHash by remember { mutableStateOf("") }
                
                Text("Enter API Credentials:")
                OutlinedTextField(
                    value = apiId,
                    onValueChange = { apiId = it },
                    label = { Text("API ID") }
                )
                OutlinedTextField(
                    value = apiHash,
                    onValueChange = { apiHash = it },
                    label = { Text("API Hash") }
                )
                Button(onClick = { viewModel.setApiCredentials(apiId, apiHash) }, modifier = Modifier.padding(top=8.dp)) {
                    Text("Save & Continue")
                }
            }
            else -> {
                Text("Status: ${authState.javaClass.simpleName}")
            }
        }
    }
}
