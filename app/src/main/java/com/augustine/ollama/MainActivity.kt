package com.augustine.ollama

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.TimeUnit

// --- DATA MODELS ---

class Message(val role: String, initialContent: String, var base64Image: String? = null) {
    var content by mutableStateOf(initialContent)
}

data class ChatRequestMessage(val role: String, val content: String, val images: List<String>? = null)
data class ChatRequest(val model: String, val messages: List<ChatRequestMessage>, val stream: Boolean = true)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val messages: SnapshotStateList<Message> = mutableStateListOf<Message>()
)

class MessageAdapter : JsonSerializer<Message>, JsonDeserializer<Message> {
    override fun serialize(src: Message, t: Type, c: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("role", src.role)
        obj.addProperty("content", src.content)
        if (src.base64Image != null) obj.addProperty("image", src.base64Image)
        return obj
    }
    override fun deserialize(j: JsonElement, t: Type, c: JsonDeserializationContext): Message {
        val obj = j.asJsonObject
        val img = if (obj.has("image")) obj.get("image").asString else null
        return Message(obj.get("role").asString, obj.get("content").asString, img)
    }
}

data class ChatResponseChunk(val message: MessageChunk?, val done: Boolean)
data class MessageChunk(val role: String?, val content: String?)
data class ModelList(val models: List<OllamaModel>?)
data class OllamaModel(val name: String)

interface OllamaApi {
    @POST("api/chat") @Streaming suspend fun chatStream(@Body r: ChatRequest): ResponseBody
    @GET("api/tags") suspend fun getModels(): ModelList
}

// --- VIEWMODEL ---

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("ollama_pro_final_prefs", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().registerTypeAdapter(Message::class.java, MessageAdapter()).create()

    var allSessions = mutableStateListOf<ChatSession>()
    var currentSessionId by mutableStateOf("")
    var models = mutableStateListOf<String>()
    var serverIp by mutableStateOf(prefs.getString("saved_ip", "") ?: "")
    var selectedModel by mutableStateOf(prefs.getString("saved_model", "") ?: "")
    var systemPrompt by mutableStateOf(prefs.getString("system_prompt", "You are a helpful assistant.") ?: "")

    var isGenerating by mutableStateOf(false)
    var selectedImageBase64 by mutableStateOf<String?>(null)
    private var currentJob: Job? = null
    private var tts: TextToSpeech? = null
    private var api: OllamaApi? = null

    val currentSession: ChatSession? get() = allSessions.find { it.id == currentSessionId }

    init {
        loadChats()
        if (serverIp.isNotEmpty()) { updateIp(serverIp) }
        tts = TextToSpeech(application) { status -> if (status != TextToSpeech.ERROR) tts?.language = Locale.US }
    }

    private fun loadChats() {
        val json = prefs.getString("all_chats", null)
        if (!json.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<ChatSession>>() {}.type
                val saved: List<ChatSession> = gson.fromJson(json, listType)
                allSessions.addAll(saved)
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (allSessions.isEmpty()) startNewChat() else currentSessionId = allSessions[0].id
    }

    fun startNewChat() {
        val newChat = ChatSession()
        allSessions.add(0, newChat); currentSessionId = newChat.id; saveAll()
    }

    fun updateIp(ip: String) {
        serverIp = ip.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        prefs.edit().putString("saved_ip", serverIp).apply()
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        api = Retrofit.Builder().baseUrl("http://$serverIp:11434/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(OllamaApi::class.java)
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = api?.getModels()
                launch(Dispatchers.Main) {
                    models.clear()
                    res?.models?.forEach { models.add(it.name) }
                    if (selectedModel.isEmpty() && models.isNotEmpty()) selectedModel = models[0]
                    saveAll()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun send(text: String) {
        val session = currentSession ?: return
        if ((text.isBlank() && selectedImageBase64 == null) || isGenerating) return
        if (session.messages.isEmpty()) session.title = if(text.length > 20) text.take(20) else if(text.isNotBlank()) text else "Image Query"

        val userMsg = Message("user", text, selectedImageBase64)
        session.messages.add(userMsg)
        val assistantMsg = Message("assistant", "")
        session.messages.add(assistantMsg)

        selectedImageBase64 = null
        isGenerating = true

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = mutableListOf<ChatRequestMessage>()
                history.add(ChatRequestMessage("system", systemPrompt))
                history.addAll(session.messages.dropLast(1).map {
                    ChatRequestMessage(it.role, it.content, if (it.base64Image != null) listOf(it.base64Image!!) else null)
                })

                val response = api?.chatStream(ChatRequest(selectedModel, history))
                response?.byteStream()?.bufferedReader()?.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            val chunk = gson.fromJson(line, ChatResponseChunk::class.java)
                            launch(Dispatchers.Main) { assistantMsg.content += (chunk.message?.content ?: "") }
                        }
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { assistantMsg.content = "Error connecting to $serverIp" }
            } finally {
                launch(Dispatchers.Main) { isGenerating = false; saveAll(); tts?.speak(assistantMsg.content, TextToSpeech.QUEUE_FLUSH, null, null) }
            }
        }
    }

    fun stopGeneration() { currentJob?.cancel(); isGenerating = false }
    fun deleteChat(s: ChatSession) { allSessions.remove(s); if (currentSessionId == s.id) currentSessionId = if (allSessions.isNotEmpty()) allSessions[0].id else ""; if (currentSessionId == "") startNewChat(); saveAll() }
    private fun saveAll() {
        prefs.edit().putString("all_chats", gson.toJson(allSessions.toList())).apply()
        prefs.edit().putString("saved_model", selectedModel).apply()
        prefs.edit().putString("system_prompt", systemPrompt).apply()
    }
}

// --- UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ChatViewModel = viewModel()) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // VOICE RECOGNITION LAUNCHER
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            data?.get(0)?.let { spokenText ->
                inputText = spokenText
                vm.send(spokenText)
                inputText = ""
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                bytes?.let { b -> vm.selectedImageBase64 = Base64.encodeToString(b, Base64.NO_WRAP) }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(300.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ollama AI", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { vm.startNewChat(); scope.launch { drawerState.close() } }) {
                        Icon(Icons.Default.Add, "New Chat")
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(vm.allSessions) { s ->
                        NavigationDrawerItem(
                            label = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = vm.currentSessionId == s.id,
                            onClick = { vm.currentSessionId = s.id; scope.launch { drawerState.close() } },
                            badge = { IconButton(onClick = { vm.deleteChat(s) }) { Icon(Icons.Default.Delete, null, Modifier.size(20.dp)) } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
                NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { showSettings = true; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Settings, null) }, modifier = Modifier.padding(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(vm.selectedModel.uppercase().ifEmpty { "OLLAMA" }, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    actions = {
                        IconButton(onClick = { vm.refreshModels() }) { Icon(Icons.Default.Refresh, null) }
                        if (vm.isGenerating) IconButton(onClick = { vm.stopGeneration() }) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 8.dp) {
                    Column(Modifier.padding(8.dp).navigationBarsPadding().imePadding()) {
                        vm.selectedImageBase64?.let {
                            Box(Modifier.size(70.dp).padding(start = 55.dp, bottom = 8.dp)) {
                                val bytes = Base64.decode(it, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                IconButton(onClick = { vm.selectedImageBase64 = null }, Modifier.size(18.dp).align(Alignment.TopEnd).background(Color.Black, RoundedCornerShape(9.dp))) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { if (vm.models.isEmpty()) vm.refreshModels(); showModels = true }) {
                                    Text(vm.selectedModel.take(8).ifEmpty { "PICK" }.uppercase(), fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = showModels, onDismissRequest = { showModels = false }) {
                                    vm.models.forEach { m ->
                                        DropdownMenuItem(text = { Text(m) }, onClick = { vm.selectedModel = m; showModels = false })
                                    }
                                }
                            }
                            TextField(
                                value = inputText, onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp)),
                                placeholder = { Text("Message...") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { vm.send(inputText); inputText = "" }),
                                leadingIcon = { IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary) } },
                                trailingIcon = {
                                    Row {
                                        // VOICE BUTTON
                                        IconButton(onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                                            }
                                            speechLauncher.launch(intent)
                                        }) { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.secondary) }

                                        // SEND BUTTON
                                        IconButton(onClick = { vm.send(inputText); inputText = "" }) { Icon(Icons.Default.Send, null) }
                                    }
                                },
                                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val listState = rememberLazyListState()
            val msgs = vm.currentSession?.messages ?: emptyList()
            LaunchedEffect(msgs.size, if(msgs.isNotEmpty()) msgs.last().content.length else 0) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(msgs) { msg -> ChatBubble(msg) }
            }
        }
    }

    if (showSettings) {
        var tempIp by remember { mutableStateOf(vm.serverIp) }
        var tempPrompt by remember { mutableStateOf(vm.systemPrompt) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configuration") },
            text = {
                Column {
                    OutlinedTextField(value = tempIp, onValueChange = { tempIp = it }, label = { Text("Server IP") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = tempPrompt, onValueChange = { tempPrompt = it }, label = { Text("System Instruction") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = { Button(onClick = { vm.updateIp(tempIp); vm.systemPrompt = tempPrompt; showSettings = false }) { Text("Save") } }
        )
    }
}

@Composable
fun ChatBubble(msg: Message) {
    val isUser = msg.role == "user"
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if(isUser) 16.dp else 2.dp, bottomEnd = if(isUser) 2.dp else 16.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                msg.base64Image?.let {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(8.dp))
                }
                Text(msg.content, style = if (msg.content.contains("```")) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg.content) }
                        context.startActivity(Intent.createChooser(intent, "Share Message"))
                    }, Modifier.size(24.dp)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline) }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { MainScreen() } } }
    }
}