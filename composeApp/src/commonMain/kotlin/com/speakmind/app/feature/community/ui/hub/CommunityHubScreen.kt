package com.speakmind.app.feature.community.ui.hub

import com.speakmind.app.feature.community.ui.components.FunAvatars
import androidx.compose.foundation.border

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.ui.auth.rememberGoogleSignIn
import com.speakmind.app.feature.community.ui.components.ChatSheet
import com.speakmind.app.feature.community.ui.components.CommunitySearchField
import com.speakmind.app.feature.community.ui.components.CommunityToast
import com.speakmind.app.feature.community.ui.components.ConfirmSheet
import com.speakmind.app.feature.community.ui.components.GoogleSignInPill
import com.speakmind.app.feature.community.ui.components.OnlineGreen
import com.speakmind.app.feature.community.ui.components.PersonCard
import com.speakmind.app.feature.community.ui.components.PremiumPill
import com.speakmind.app.feature.community.ui.components.SectionTitle
import com.speakmind.app.feature.community.ui.components.SheetButton
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.formatListTime
import com.speakmind.app.feature.community.ui.components.rememberImagePickerLauncher
import com.speakmind.app.navigation.CommunityHubDestination
import com.speakmind.app.premium.Premium
import com.speakmind.app.ui.components.BottomBarClearance
import com.speakmind.app.ui.components.MainTab
import com.speakmind.app.ui.components.SpeakyBottomBar
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.onBrandFill
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.communityHubScreen() {
    animatedComposable<CommunityHubDestination> {
        val viewModel = koinViewModel<CommunityHubViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleStartEffect(viewModel) {
            viewModel.onVisibilityChanged(true)
            onStopOrDispose { viewModel.onVisibilityChanged(false) }
        }
        LifecycleResumeEffect(viewModel) {
            viewModel.onResume()
            onPauseOrDispose { }
        }
        val googlePicker = rememberGoogleSignIn(AppConfig.googleWebClientId)
        InboxContent(uiState = uiState, viewModel = viewModel, onSignIn = { viewModel.onGoogleSignIn(googlePicker) })
    }
}

/** The brand gradient of the Speaky Hub card (fixed in Violet and Dark; the Premium themes use their own brand). */
private val HubGradient = Brush.linearGradient(
    0f to Color(0xFF7A61FF),
    0.6f to Color(0xFF5B3DF5),
    1f to Color(0xFF4129D6),
)

@Composable
private fun InboxContent(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel, onSignIn: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    var query by rememberSaveable { mutableStateOf("") }
    // A chat whose other person deleted their account has no row to show.
    val visibleChats = remember(uiState.chats, query) {
        val q = query.trim()
        uiState.chats.filter { chat ->
            val other = chat.otherUser ?: return@filter false
            q.isEmpty() || other.nickname.contains(q, ignoreCase = true) || chat.lastMessagePreview.contains(q, ignoreCase = true)
        }
    }
    val noChats = !uiState.isGuest && !uiState.isLoadingChats && uiState.chats.none { it.otherUser != null }
    val loading = !uiState.isGuest && uiState.isLoadingChats && uiState.chats.isEmpty()

    Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = BottomBarClearance + 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item(key = "top") {
                Column(
                    Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Chats", Modifier.weight(1f), style = displayStyle(32.sp), color = colors.textPrimary)
                        SpeakyIconButton(SpeakyIcons.AddPerson, "Find people", viewModel::onPeopleClicked)
                    }
                    if (!uiState.isGuest && !loading) {
                        CommunitySearchField(
                            value = query,
                            placeholder = "Search",
                            onChange = { query = it },
                            height = 44.dp,
                            radius = 14.dp,
                            background = colors.surfaceVariant,
                        )
                    }
                    when {
                        loading -> Skeleton(Modifier.fillMaxWidth().height(74.dp), 24)
                        noChats -> HubRow(uiState.onlineUserIds.size, viewModel::openSpeakyHub)
                        else -> SpeakyHubCard(uiState.hubRoom, uiState.onlineUserIds.size, viewModel::openSpeakyHub)
                    }
                }
            }
            when {
                uiState.isGuest -> item(key = "signin") {
                    SignInCard(onSignIn = onSignIn, loading = uiState.isSigningIn, modifier = Modifier.padding(20.dp))
                }
                loading -> item(key = "loading") { ChatListSkeleton() }
                noChats -> item(key = "empty") { NoChats(onFindPeople = viewModel::onPeopleClicked) }
                else -> {
                    item(key = "private_title") {
                        SectionTitle("Private messages", Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp))
                    }
                    if (visibleChats.isEmpty() && query.isNotBlank()) {
                        item(key = "no_match") {
                            Text(
                                "No chats match “${query.trim()}”",
                                color = colors.textMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                    itemsIndexed(visibleChats, key = { _, chat -> chat.id }) { index, chat ->
                        // Next page when the last few chats come into view.
                        if (query.isBlank() && index >= visibleChats.size - 5) {
                            LaunchedEffect(uiState.chats.size) { viewModel.loadMoreChats() }
                        }
                        PrivateChatRow(chat, uiState, viewModel, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    }
                    if (uiState.isLoadingMoreChats) item(key = "more_chats") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.neonCyan, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            }
        }

        val gamesWaiting by com.speakmind.app.feature.games.data.GamesBadge.waiting.collectAsStateWithLifecycle()
        SpeakyBottomBar(
            selected = MainTab.Chat,
            onSelect = viewModel::onTabSelected,
            gamesWaiting = gamesWaiting,
            onGames = viewModel::onGamesClicked,
            chatBadge = uiState.unreadTotal,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        uiState.error?.let { error ->
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3_500)
                viewModel.onErrorDismissed()
            }
            CommunityToast(error, isError = true, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp))
        }
        if (uiState.isDeletingAccount) {
            Box(Modifier.fillMaxSize().background(Color(0x7317142B)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    CommunityHubDialogs(uiState, viewModel)
}

@Composable
private fun PrivateChatRow(chat: ChatRoom, uiState: CommunityHubUiState, viewModel: CommunityHubViewModel, modifier: Modifier) {
    val other = chat.otherUser ?: return
    PersonCard(
        user = other,
        online = other.uid in uiState.onlineUserIds,
        subtitle = (if (chat.muted) "🔕 " else "") + chat.lastMessagePreview.ifEmpty { "Say hello 👋" },
        trailingTime = formatListTime(chat.lastMessageAtMillis),
        unread = chat.unreadCount,
        onClick = { viewModel.onPrivateChatClicked(chat) },
        modifier = modifier,
    )
}

/** The pinned general chat: the violet gradient card with the newest message and who's online. */
@Composable
private fun SpeakyHubCard(hub: ChatRoom?, onlineCount: Int, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(24.dp)
    val onCard = colors.onBrandFill
    val gradient = if (colors.isGold) Brush.linearGradient(listOf(colors.neonCyan, colors.neonCyanDark)) else HubGradient
    val shadow = if (colors.isGold) colors.neonCyan.copy(alpha = 0.28f) else Color(0x475B3DF5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = shadow, spotColor = shadow)
            .clip(shape)
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(onCard.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpeakyIcons.Globe, contentDescription = null, tint = onCard, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Speaky Hub", style = displayStyle(18.sp), color = onCard)
            Text(
                hub?.lastMessagePreview?.takeIf { it.isNotEmpty() } ?: "Say hi to learners worldwide 👋",
                color = if (colors.isGold) onCard.copy(alpha = 0.8f) else Color(0xFFE4DEFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onlineCount > 0) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(onCard.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                LiveDot(Color(0xFF3EE0AE))
                Text(onlineCount.toString(), color = onCard, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Gently pulsing online dot. */
@Composable
private fun LiveDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "live")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "liveScale",
    )
    Box(Modifier.size(7.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(color, CircleShape))
}

/** Plain Hub row used above the empty state (the design drops the big card there). */
@Composable
private fun HubRow(onlineCount: Int, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(colors.neonCyan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpeakyIcons.Globe, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speaky Hub", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (onlineCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(7.dp).background(OnlineGreen, CircleShape))
                        Text(onlineCount.toString(), color = colors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Say hi to learners worldwide", color = colors.textMuted, fontSize = 14.sp)
        }
    }
}

/** No private chats yet: white panel with the two-bubble drawing and a Find people button. */
@Composable
private fun NoChats(onFindPeople: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .heightIn(min = 460.dp)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(colors.surface)
            .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Messages", color = colors.faint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        BubblesDrawing(Modifier.padding(top = 30.dp).size(width = 150.dp, height = 120.dp))
        Text("No private chats yet", style = displayStyle(24.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            "Say hi to a learner in Speaky Hub, or find someone at your level to practise with.",
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.padding(top = 6.dp).height(52.dp).clip(RoundedCornerShape(999.dp)).background(colors.neonCyan)
                .clickable(role = Role.Button, onClick = onFindPeople).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(SpeakyIcons.AddPerson, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            Text("Find people", color = colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** The empty-state illustration from the design (150×120): a violet and a coral speech bubble. */
@Composable
private fun BubblesDrawing(modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    val violetSoft = colors.primarySoft
    val violet = colors.neonCyan
    val coralSoft = colors.coralSoft
    val coral = colors.coral
    Canvas(modifier) {
        val u = size.width / 150f
        drawRoundRect(violetSoft, Offset(14 * u, 20 * u), Size(84 * u, 58 * u), CornerRadius(20 * u))
        drawPath(Path().apply { moveTo(30 * u, 78 * u); lineTo(24 * u, 94 * u); lineTo(46 * u, 78 * u); close() }, violetSoft)
        listOf(40f, 56f, 72f).forEach { drawCircle(violet, 5 * u, Offset(it * u, 49 * u)) }
        drawRoundRect(coralSoft, Offset(62 * u, 52 * u), Size(74 * u, 50 * u), CornerRadius(18 * u))
        drawPath(Path().apply { moveTo(120 * u, 102 * u); lineTo(126 * u, 114 * u); lineTo(108 * u, 102 * u); close() }, coralSoft)
        drawPath(
            Path().apply { moveTo(86 * u, 77 * u); cubicTo(90 * u, 82 * u, 104 * u, 82 * u, 108 * u, 77 * u) },
            coral,
            style = Stroke(4 * u, cap = StrokeCap.Round),
        )
    }
}

/** Placeholder rows while the first page of chats loads. */
@Composable
private fun ChatListSkeleton() {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(120 to 200, 90 to 160, 140 to 220, 100 to 180, 130 to 150, 80 to 210).forEach { (a, b) ->
            Row(
                Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(22.dp)).background(colors.surface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Skeleton(Modifier.size(50.dp), 25)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.width(a.dp).height(12.dp), 6)
                    Skeleton(Modifier.width(b.dp).height(10.dp), 6)
                }
                Skeleton(Modifier.width(34.dp).height(10.dp), 6)
            }
        }
    }
}

@Composable
private fun Skeleton(modifier: Modifier, radius: Int) {
    val colors = LocalSpeakMindColors.current
    val shimmer = rememberInfiniteTransition(label = "skeleton")
    val alpha by shimmer.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.graphicsLayer { this.alpha = alpha }.clip(RoundedCornerShape(radius.dp)).background(colors.track))
}

@Composable
private fun SignInCard(onSignIn: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Private messages", style = displayStyle(20.sp), color = colors.textPrimary)
        Text(
            "You're reading as a visitor. Sign in with Google to chat in Speaky Hub and message learners privately.",
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(6.dp))
        GoogleSignInPill("Sign in with Google", loading = loading, onClick = onSignIn)
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(LocalSpeakMindColors.current.track))
}

/** The design's 46×28 violet switch. */
@Composable
private fun MiniSwitch(on: Boolean) {
    val colors = LocalSpeakMindColors.current
    Box(
        Modifier.size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(999.dp))
            .background(if (on) colors.neonCyan else colors.track).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).background(if (on) colors.onPrimary else Color.White, CircleShape))
    }
}

/** One row of the account menu: icon on a soft rounded tile, title, optional subtitle and trailing control. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    tileColor: Color,
    tint: Color,
    title: String,
    subtitle: String? = null,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor ?: colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = colors.textMuted, fontSize = 12.sp) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun EditProfileSheet(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    var nickname by remember { mutableStateOf(uiState.myNickname) }
    var gender by remember { mutableStateOf(uiState.myGender.ifEmpty { "male" }) }
    val picker = rememberImagePickerLauncher { bytes -> viewModel.onPhotoPicked(bytes) }
    ChatSheet(onDismiss = viewModel::onEditProfileDismissed) {
        Text("Edit profile", style = displayStyle(22.sp), color = colors.textPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Box(Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { picker.launch() }) {
            UserAvatar(nickname = nickname.ifEmpty { "?" }, photoUrl = uiState.myPhotoUrl, size = 92.dp, borderColor = colors.neonCyan)
            Box(
                Modifier.align(Alignment.BottomEnd).size(30.dp).background(colors.surface, CircleShape).padding(3.dp)
                    .background(colors.neonCyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeakyIcons.Pencil, contentDescription = "Change photo", tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
        Text("Tap to upload a photo, or pick a fun avatar", color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        val picked = FunAvatars.keyOf(uiState.myPhotoUrl)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FunAvatars.keys.size) { i ->
                val key = FunAvatars.keys[i]
                val on = key == picked
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .then(if (on) Modifier.border(3.dp, colors.neonCyan, CircleShape) else Modifier)
                        .clickable(onClickLabel = key) { viewModel.onFunAvatarPicked(key) }
                        .padding(if (on) 4.dp else 0.dp),
                ) {
                    UserAvatar(nickname = key, photoUrl = FunAvatars.photoUrlOf(key), size = if (on) 48.dp else 56.dp, borderColor = colors.neonCyan)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nickname", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = nickname,
                onValueChange = { if (it.length <= 30) nickname = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(colors.neonCyan),
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(colors.backgroundDark).padding(horizontal = 14.dp),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("male" to "Male", "female" to "Female").forEach { (value, label) ->
                val selected = gender == value
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.neonCyan else colors.surfaceVariant)
                        .clickable { gender = value },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) colors.onPrimary else colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (uiState.isSavingProfile) {
            LinearProgressIndicator(color = colors.neonCyan, trackColor = colors.track, modifier = Modifier.fillMaxWidth())
        }
        if (uiState.photoChecking) {
            Text("Checking your photo…", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        uiState.profileError?.let {
            Text(it, color = colors.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        SheetButton("Save", colors.neonCyan, colors.onPrimary, 54, { viewModel.onSaveProfile(nickname, gender) }, enabled = !uiState.isSavingProfile)
        SheetButton("Cancel", colors.surfaceVariant, colors.textPrimary, 50, viewModel::onEditProfileDismissed)
        // Google Play requires in-app account deletion; kept here, out of the everyday menu.
        Text(
            "Delete my account",
            color = colors.error,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    viewModel.onEditProfileDismissed()
                    viewModel.onDeleteAccountRequested()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}


/** My photo was removed as not modest: can't be dismissed until a modest photo or a cartoon avatar is chosen. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PhotoRequiredSheet(reason: String, onChoose: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = {},
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden },
        ),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🖼️", fontSize = 48.sp)
            Text("Please change your photo", style = displayStyle(24.sp), color = colors.textPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                if (reason == "admin") "An admin removed your profile photo. Please choose a modest photo or a cartoon avatar to keep chatting."
                else "Your profile photo isn't allowed in Speaky. Please choose a modest photo or a cartoon avatar.",
                color = colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "Allowed: your face, fully dressed, hijab or not, cartoons, nature.",
                color = colors.textMuted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(colors.neonCyan)
                    .clickable(onClick = onChoose),
                contentAlignment = Alignment.Center,
            ) { Text("Choose a new photo", color = colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}


/** Edit profile, photo, sign-out and delete sheets; shared by the Chats screen and Settings. */
@Composable
internal fun CommunityHubDialogs(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    if (uiState.showBlocked) BlockedPeopleSheet(uiState, viewModel)
    if (uiState.showEditProfile) {
        EditProfileSheet(uiState, viewModel)
    } else if (uiState.photoRejected != null && !uiState.isGuest) {
        PhotoRequiredSheet(uiState.photoRejected, onChoose = viewModel::onEditProfile)
    }
    if (uiState.confirmSignOut) {
        ConfirmSheet(
            title = "Sign out?",
            message = "You can sign in again any time with Google. Your chats and messages stay saved.",
            confirmLabel = "Sign out",
            icon = SpeakyIcons.SignOut,
            onConfirm = viewModel::onSignOut,
            onDismiss = viewModel::onSignOutDismissed,
        )
    }
    if (uiState.confirmDeleteAccount) {
        ConfirmSheet(
            title = "Delete your account?",
            message = "Your community profile, messages, voice notes and private chats are permanently deleted. This can't be undone.",
            confirmLabel = "Delete forever",
            icon = SpeakyIcons.Trash,
            onConfirm = viewModel::onConfirmDeleteAccount,
            onDismiss = viewModel::onDeleteAccountDismissed,
        )
    }
}

/**
 * The community part of Settings: your chat profile, Hub alerts, the admin tools (admins only), sign out
 * and delete account. Visitors get a sign-in row that opens Chat.
 */
@Composable
internal fun CommunitySettingsSection(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel, onOpenChat: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    @Composable
    fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(6.dp), content = content)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (uiState.isGuest) {
            Card("COMMUNITY") {
                MenuRow(SpeakyIcons.Chat, colors.primarySoft, colors.onPrimarySoft, "Join the community", "Sign in to chat, call and make friends", onClick = onOpenChat)
            }
            return@Column
        }
        Card("COMMUNITY") {
            MenuRow(SpeakyIcons.Pencil, colors.primarySoft, colors.onPrimarySoft, "Chat profile", uiState.myNickname.ifEmpty { "Name, photo and gender" }, onClick = viewModel::onEditProfile)
            uiState.hubAlerts?.let { on ->
                MenuRow(SpeakyIcons.Bell, colors.goldSoft, colors.onGoldSoft, "Speaky Hub alerts", if (on) "On" else "Off", trailing = { MiniSwitch(on) }, onClick = viewModel::onToggleHubAlerts)
            }
            MenuRow(com.speakmind.app.feature.community.ui.components.ChatIcons.Block, colors.surfaceVariant, colors.textSecondary, "Blocked people", if (uiState.blockedCount == 0) "Nobody" else "${uiState.blockedCount}", onClick = viewModel::onBlockedOpened)
        }
        if (uiState.isAdmin) {
            Card("ADMIN") {
                MenuRow(SpeakyIcons.Shield, colors.coralSoft, colors.coral, "Reports", onClick = viewModel::openReports)
                MenuRow(SpeakyIcons.Chat, colors.mintSoft, colors.onMintSoft, "Private chats", "Review", onClick = viewModel::openPrivateChats)
                MenuRow(SpeakyIcons.Phone, colors.primarySoft, colors.onPrimarySoft, "Voice calls", "On/off, bans", onClick = viewModel::openVoiceCalls)
                MenuRow(SpeakyIcons.Crown, colors.goldSoft, colors.onGoldSoft, "Subscribers", "Premium members", onClick = viewModel::openSubscribers)
            }
        }
        Card("ACCOUNT") {
            MenuRow(SpeakyIcons.SignOut, colors.error.copy(alpha = 0.12f), colors.error, "Sign out", titleColor = colors.error, onClick = viewModel::onSignOutRequested)
            MenuRow(SpeakyIcons.Trash, colors.error.copy(alpha = 0.12f), colors.error, "Delete account", titleColor = colors.error, onClick = viewModel::onDeleteAccountRequested)
        }
    }
}


/** Settings → Blocked people: everyone I blocked, with Unblock. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BlockedPeopleSheet(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = viewModel::onBlockedDismissed,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Blocked people", style = com.speakmind.app.ui.components.displayStyle(22.sp), color = colors.textPrimary)
            Text("They can't message you and you don't see their messages.", color = colors.textMuted, fontSize = 13.sp)
            val people = uiState.blockedPeople
            when {
                people == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.neonCyan, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                }
                people.isEmpty() -> Text("You haven't blocked anyone.", color = colors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(people.size) { i ->
                        val p = people[i]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            com.speakmind.app.feature.community.ui.components.UserAvatar(p.nickname, p.photoUrl, 44.dp, Color.Transparent)
                            Text(p.nickname.ifEmpty { "Someone" }, Modifier.weight(1f), color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Unblock", Modifier.clip(RoundedCornerShape(12.dp)).background(colors.primarySoft)
                                    .clickable { viewModel.onUnblock(p) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = colors.onPrimarySoft, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
