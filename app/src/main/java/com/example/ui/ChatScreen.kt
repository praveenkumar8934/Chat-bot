package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.ChatMessage
import com.example.data.db.ChatSession
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom of discussion list when new message is added or typing starts
    LaunchedEffect(state.messages.size, state.isTyping) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Material 3 Gradient Theme
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep navy
            Color(0xFF04060E)  // Dark carbon midnight
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Ambient Mesh Background Orbs
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // Top-left glowing indigo orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4F46E5).copy(alpha = 0.22f), // Indigo
                        Color(0xFF4F46E5).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(x = size.width * 0.1f, y = size.height * 0.15f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = androidx.compose.ui.geometry.Offset(x = size.width * 0.1f, y = size.height * 0.15f)
            )

            // Bottom-right glowing fuchsia/pink orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC026D3).copy(alpha = 0.15f), // Fuchsia
                        Color(0xFFC026D3).copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(x = size.width * 0.9f, y = size.height * 0.85f),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = androidx.compose.ui.geometry.Offset(x = size.width * 0.9f, y = size.height * 0.85f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // 1. Top Bar Header (Frosted glass container panel)
            HeaderBlock(
                currentSession = state.sessions.find { it.id == state.currentSessionId },
                onSettingsClicked = { viewModel.toggleSettingsDialog(true) },
                onClearHistory = { viewModel.clearHistory() },
                hasMessages = state.messages.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Chat Sessions Carousel Picker
            SessionPickerCarousel(
                sessions = state.sessions,
                currentSessionId = state.currentSessionId,
                onSessionSelected = { viewModel.selectSession(it) },
                onNewSessionRequested = { viewModel.startNewSession() },
                onDeleteSession = { viewModel.deleteSession(it) }
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                thickness = 1.dp
            )

            // 3. Error Banner Notification
            state.errorNotification?.let { errorMsg ->
                ErrorBanner(
                    message = errorMsg,
                    onDismiss = { viewModel.clearErrorNotification() }
                )
            }

            // 4. Main Body: Messaging space / Greeting panel
            Box(
                modifier = Modifier
                    .weight(1.getFloat())
                    .fillMaxWidth()
            ) {
                if (state.messages.isEmpty() && !state.isTyping) {
                    GreetingEmptyState(
                        customApiKey = state.customApiKey,
                        onSuggestedQueryClicked = { 
                            viewModel.updateInputText(it) 
                        },
                        onConfigureKeyRequired = { viewModel.toggleSettingsDialog(true) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.messages) { message ->
                            MessageBubbleItem(message = message)
                        }

                        if (state.isTyping) {
                            item {
                                AIThinkingIndicator()
                            }
                        }
                    }
                }
            }

            // 5. Input Text Row block
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                thickness = 1.dp
            )
            
            InputControlRow(
                inputText = state.inputText,
                onTextUpdated = { viewModel.updateInputText(it) },
                onSendTriggered = {
                    keyboardController?.hide()
                    viewModel.sendMessage()
                },
                isSendEnabled = state.inputText.trim().isNotEmpty() && !state.isTyping
            )
        }

        // 6. Settings Popup Drawer Modal
        if (state.showSettingsDialog) {
            SettingsDialog(
                savedKey = state.customApiKey,
                onDismiss = { viewModel.toggleSettingsDialog(false) },
                onSave = { 
                    viewModel.updateCustomApiKey(it)
                    viewModel.toggleSettingsDialog(false)
                }
            )
        }
    }
}

// Float helper for Compose weight safety
private fun Int.getFloat() = this.toFloat()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderBlock(
    currentSession: ChatSession?,
    onSettingsClicked: () -> Unit,
    onClearHistory: () -> Unit,
    hasMessages: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Frosted chatbot robot icon avatar block
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1).copy(alpha = 0.22f))
                    .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "Aura AI",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)) // Active Green
                    )
                    Text(
                        text = "Real-time active",
                        color = Color(0xFFA5B4FC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (hasMessages) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B)) // Slate 800
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear Chat Messages", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onClearHistory()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    )
                }
            }

            IconButton(
                onClick = onSettingsClicked,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .size(38.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings menu",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SessionPickerCarousel(
    sessions: List<ChatSession>,
    currentSessionId: Int?,
    onSessionSelected: (Int) -> Unit,
    onNewSessionRequested: () -> Unit,
    onDeleteSession: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            // New Session Creator Button (Frosted style)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable { onNewSessionRequested() }
                    .testTag("new_chat_chip"),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Start new chat",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "New Chat",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(sessions) { session ->
            val isSelected = session.id == currentSessionId
            val chipBorderColor = if (isSelected) Color(0xFFC084FC).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
            val chipBgColor = if (isSelected) Color(0xFF818CF8).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f)

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, chipBorderColor, RoundedCornerShape(12.dp))
                    .clickable { onSessionSelected(session.id) }
                    .testTag("session_chip_${session.id}"),
                color = chipBgColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = session.title,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 120.dp),
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Simple Delete click trigger
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete chat selection",
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .clickable { onDeleteSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.6f)), // Frosted red blend
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Color(0xFFFECACA).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert Error",
                tint = Color(0xFFFCA5A5)
            )
            Text(
                text = message,
                color = Color(0xFFFEE2E2),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss warning",
                    tint = Color(0xFFFEE2E2),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun GreetingEmptyState(
    customApiKey: String,
    onSuggestedQueryClicked: (String) -> Unit,
    onConfigureKeyRequired: () -> Unit
) {
    val suggestions = listOf(
        "Write a travel itinerary for 3 days in Tokyo",
        "Solve a riddle: What has keys but opens no locks?",
        "Write a short epic sci-fi story set in 3026"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Decorative AI Brand Circle (Frosted orb design)
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF818CF8).copy(alpha = 0.32f),
                            Color(0xFFC084FC).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "AI Sparkle",
                tint = Color(0xFFA5B4FC),
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Welcome to Aura AI",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Interactive, real-time intelligence at your fingertips. Ask anything, brainstorm, or write creative code.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (customApiKey.trim().isEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF78350F).copy(alpha = 0.25f)) // Amber warning flag bg
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable { onConfigureKeyRequired() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Using workspace key. Click to set custom API key.",
                    color = Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Suggested Queries List
        Text(
            text = "Try these examples:",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        suggestions.forEach { query ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .clickable { onSuggestedQueryClicked(query) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = query,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubbleItem(message: ChatMessage) {
    val isUser = message.isUser

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        Color(0xFF4F46E5) // Indigo-600
    } else {
        Color.White.copy(alpha = 0.10f) // Frosted white/10
    }
    
    val bubbleBorderColor = if (isUser) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.12f) // Frosted borders
    }
    
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .border(1.dp, bubbleBorderColor, bubbleShape)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Sender label
                Text(
                    text = if (isUser) "You" else "Aura AI",
                    color = if (isUser) Color.White.copy(alpha = 0.85f) else Color(0xFFA5B4FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                // Text prompt
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun AIThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_loader")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA78BFA).copy(alpha = alphaAnim))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA78BFA).copy(alpha = (alphaAnim + 0.3f).coerceIn(0.2f, 1.0f)))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA78BFA).copy(alpha = (alphaAnim + 0.6f).coerceIn(0.2f, 1.0f)))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Aura AI is thinking...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun InputControlRow(
    inputText: String,
    onTextUpdated: (String) -> Unit,
    onSendTriggered: () -> Unit,
    isSendEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = inputText,
            onValueChange = onTextUpdated,
            placeholder = { Text("Ask anything...", color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 120.dp)
                .testTag("message_input_field"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { 
                    if (isSendEnabled) onSendTriggered() 
                }
            ),
            maxLines = 4
        )

        IconButton(
            onClick = onSendTriggered,
            enabled = isSendEnabled,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSendEnabled) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))
                        )
                    }
                )
                .testTag("send_button")
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send message",
                tint = if (isSendEnabled) Color.White else Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SettingsDialog(
    savedKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyDraft by remember { mutableStateOf(savedKey) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)), // Dark Gray 900
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(36.dp)
                )

                Text(
                    text = "Configure API Credentials",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "If no custom key is specified, the application automatically tries using the Google AI Studio project secret runtime variable.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("Gemini API Key Override") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_api_key_field"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFFC084FC),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onSave(keyDraft.trim()) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_settings_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Text("Save Credentials")
                    }
                }
            }
        }
    }
}
