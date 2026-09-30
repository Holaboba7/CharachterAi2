package com.example.characterai

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen {
    data object Home : Screen()
    data class Edit(val id: Long?) : Screen()
    data class Chat(val id: Long) : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { App() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(vm: AppVM = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(screen != Screen.Home) { screen = Screen.Home }
    when (val s = screen) {
        Screen.Home -> HomeScreen(vm) { screen = it }
        is Screen.Edit -> EditScreen(vm, s.id) { screen = Screen.Home }
        is Screen.Chat -> ChatScreen(vm, s.id) { screen = Screen.Home }
        Screen.Settings -> SettingsScreen(vm) { screen = Screen.Home }
    }
}

@Composable
fun Avatar(name: String) = Box(
    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center
) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppVM, go: (Screen) -> Unit) {
    val list by vm.characters.collectAsState()
    var del by remember { mutableStateOf<CharacterEntity?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Personaggi") }, actions = {
                IconButton({ go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Impostazioni") }
            })
        },
        floatingActionButton = { FloatingActionButton({ go(Screen.Edit(null)) }) { Icon(Icons.Default.Add, "Nuovo personaggio") } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { go(Screen.Chat(c.id)) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(c.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ go(Screen.Edit(c.id)) }) { Icon(Icons.Default.Edit, "Modifica") }
                        IconButton({ del = c }) { Icon(Icons.Default.Delete, "Elimina") }
                    }
                }
            }
        }
    }
    del?.let { c ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Eliminare ${c.name}?") },
            text = { Text("Verrà cancellata anche la chat.") },
            confirmButton = { TextButton({ vm.delete(c); del = null }) { Text("Elimina") } },
            dismissButton = { TextButton({ del = null }) { Text("Annulla") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: AppVM, id: Long?, back: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var greet by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    LaunchedEffect(id) {
        id?.let { vm.find(it) }?.let { name = it.name; desc = it.description; greet = it.greeting; prompt = it.systemPrompt }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (id == null) "Nuovo personaggio" else "Modifica personaggio") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("Descrizione breve") })
            OutlinedTextField(greet, { greet = it }, Modifier.fillMaxWidth(), label = { Text("Primo messaggio (opzionale)") })
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                label = { Text("Personalità / istruzioni (prompt di sistema)") })
            Button(
                onClick = { vm.save(CharacterEntity(id ?: 0, name.trim(), desc.trim(), greet.trim(), prompt.trim())); back() },
                enabled = name.isNotBlank() && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()
            ) { Text("Salva") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppVM, back: () -> Unit) {
    var url by remember { mutableStateOf(vm.settings.baseUrl) }
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Impostazioni") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Usa la tua chiave API di qualsiasi servizio compatibile con OpenAI. " +
                "La chiave resta cifrata sul dispositivo.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL base API") }, singleLine = true,
                supportingText = { Text("OpenAI: https://api.openai.com/v1 · OpenRouter: https://openrouter.ai/api/v1 · Ollama: http://IP:11434/v1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Chiave API") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Modello") }, singleLine = true)
            Button({ vm.settings.baseUrl = url; vm.settings.apiKey = key; vm.settings.model = model; back() },
                Modifier.fillMaxWidth()) { Text("Salva") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppVM, id: Long, back: () -> Unit) {
    val msgs by remember(id) { vm.messages(id) }.collectAsState(emptyList())
    val streaming by vm.streaming.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var ch by remember { mutableStateOf<CharacterEntity?>(null) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(id) { ch = vm.find(id) }
    LaunchedEffect(msgs.size, streaming) {
        val n = msgs.size + if (streaming.isNotEmpty()) 1 else 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(ch?.name ?: "") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = { IconButton({ vm.clearChat(id) }) { Icon(Icons.Default.Delete, "Cancella chat") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = listState) {
                items(msgs, key = { it.id }) { Bubble(it.role == "user", it.content) }
                if (streaming.isNotEmpty()) item { Bubble(false, streaming) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Scrivi un messaggio…") }, maxLines = 4)
                if (busy) IconButton({ vm.stop() }) { Icon(Icons.Default.Close, "Interrompi") }
                else IconButton({ vm.send(id, input); input = "" }, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Invia")
                }
            }
        }
    }
}

@Composable
fun Bubble(isUser: Boolean, text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) { Text(text, Modifier.padding(12.dp).widthIn(max = 300.dp)) }
    }
}
