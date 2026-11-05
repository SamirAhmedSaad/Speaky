package com.speakmind.app.feature.community.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.voice.platform.MicAccess
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.premium.Paywall
import com.speakmind.app.premium.Premium
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.TintStatusBar
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object HubCallDestination

private val CallBg = Color(0xFF1E1450)
private val CallSoft = Color(0x1FFFFFFF)
private val CallMuted = Color(0xFFD9D3FF)
private val CallGreen = Color(0xFF19B88A)
private val CallLive = Color(0xFF34D399)
private val CallRed = Color(0xFFF04438)
private val Gold = listOf(Color(0xFFF7D774), Color(0xFFC9962B))
private val GoldInk = Color(0xFF2A1E05)

fun NavGraphBuilder.hubCallScreen() {
    animatedComposable<HubCallDestination> {
        val manager = koinInject<HubCallManager>()
        val navigation = koinInject<NavigationManager>()
        val state by manager.state.collectAsStateWithLifecycle()
        DisposableEffect(Unit) {
            manager.screenOpen.value = true
            onDispose { manager.screenOpen.value = false }
        }
        TintStatusBar(CallBg)
        HubCallContent(
            state = state,
            manager = manager,
            onMinimize = { navigation.back() },
            onClose = {
                manager.dismissEnded()
                navigation.back()
            },
        )
    }
}

// ---------------- In the Hub chat ----------------

/** Header button: start the call, or open it while in it. Hidden when an admin turned calls off. */
@Composable
fun HubCallHeaderButton(tint: Color, onOpenCall: () -> Unit) {
    val manager = koinInject<HubCallManager>()
    val state by manager.state.collectAsStateWithLifecycle()
    if (!state.enabled && !state.inCall) return
    var asking by remember { mutableStateOf(false) }
    val live = state.live != null
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (live) CallLive else tint.copy(alpha = 0.18f))
            .clickable(role = Role.Button) {
                when {
                    state.inCall -> onOpenCall()
                    live -> joinWithChecks(manager, state, onOpenCall)
                    else -> asking = true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(SpeakyIcons.Phone, contentDescription = "Voice call", tint = if (live) Color.White else tint, modifier = Modifier.size(20.dp))
    }
    if (asking) {
        StartCallSheet(
            onStart = {
                asking = false
                if (!MicAccess.isGranted()) return@StartCallSheet MicAccess.request()
                manager.start()
                onOpenCall()
            },
            onDismiss = { asking = false },
        )
    }
}

/** Live call card under the Hub header, for people not in the call. */
@Composable
fun HubCallBanner(onOpenCall: () -> Unit, modifier: Modifier = Modifier) {
    val manager = koinInject<HubCallManager>()
    val state by manager.state.collectAsStateWithLifecycle()
    val live = state.live ?: return
    if (state.inCall) return
    val colors = LocalSpeakMindColors.current
    val premiumUser by Premium.active.collectAsStateWithLifecycle()
    val banned = state.bannedUntil != null
    val full = state.isFull
    val count = live.members.size
    Row(
        modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface).border(1.5.dp, if (full) colors.track else Color(0xFFA7EBD0), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarStack(live.members.take(4))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiveDot(if (full) colors.textMuted else CallGreen)
                Text(
                    if (full) "Call is full · $MAX_SEATS of $MAX_SEATS" else "Voice call · $count of $MAX_SEATS",
                    color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
            }
            Text(
                when {
                    banned -> banText(state.bannedUntil)
                    full && premiumUser -> "You have a Premium seat · ${MAX_PREMIUM_SEATS - state.premiumCount} left"
                    full -> "Premium members can still join"
                    else -> "Tap Join to talk"
                },
                color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            banned -> SmallButton("Can't join", colors.track, colors.textMuted, null) {}
            full -> SmallButton("Join", null, GoldInk, SpeakyIcons.Crown, gold = true) {
                if (premiumUser) joinWithChecks(manager, state, onOpenCall)
                else Premium.showPaywall(
                    Paywall(
                        title = "The call is full",
                        message = "Premium members never miss a call: they get one of $MAX_PREMIUM_SEATS extra seats, even when all $MAX_SEATS are taken.",
                        freeThing = "free seat",
                        emoji = "🎙️",
                        onUnlocked = { joinWithChecks(manager, manager.state.value, onOpenCall) },
                    ),
                )
            }
            else -> SmallButton("Join", CallGreen, Color.White, SpeakyIcons.Phone) { joinWithChecks(manager, state, onOpenCall) }
        }
    }
}

private fun joinWithChecks(manager: HubCallManager, state: HubCallState, onOpenCall: () -> Unit) {
    if (state.bannedUntil != null) return
    if (!MicAccess.isGranted()) return MicAccess.request()
    manager.join()
    onOpenCall()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartCallSheet(onStart: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(colors.mintSoft), contentAlignment = Alignment.Center) {
                Icon(SpeakyIcons.Phone, null, tint = colors.onMintSoft, modifier = Modifier.size(30.dp))
            }
            Text("Start a voice call?", style = displayStyle(24.sp), color = colors.textPrimary)
            Text("Practise speaking live with learners in the Hub.", color = colors.textSecondary, fontSize = 15.sp)
            SheetPoint(SpeakyIcons.Person, "Anyone in the Hub can join · up to $MAX_SEATS people")
            SheetPoint(SpeakyIcons.Bell, "Closes by itself if nobody joins in 5 minutes")
            SheetPoint(SpeakyIcons.Sparkle, "Keeps going when you leave the app")
            SpeakyPrimaryButton("Start call", onStart, background = CallGreen, color = Color.White, leading = SpeakyIcons.Phone)
            Text(
                "Not now",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onDismiss).padding(12.dp),
                color = colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SheetPoint(icon: ImageVector, text: String) {
    val colors = LocalSpeakMindColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = colors.neonCyan, modifier = Modifier.size(20.dp))
        Text(text, color = colors.textSecondary, fontSize = 14.sp)
    }
}

// ---------------- The call screen ----------------

@Composable
private fun HubCallContent(state: HubCallState, manager: HubCallManager, onMinimize: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CallBg)) {
        when {
            state.ended != null -> CallEndedContent(state.ended, onClose = onClose, onStartAgain = {
                manager.dismissEnded()
                manager.start()
            })
            state.phase == CallPhase.Idle -> {
                // Join failed (message shown) or the call was left: go back.
                LaunchedEffect(Unit) { onClose() }
            }
            else -> CallLiveContent(state, manager, onMinimize, onLeave = {
                manager.leave()
                onClose()
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLiveContent(state: HubCallState, manager: HubCallManager, onMinimize: () -> Unit, onLeave: () -> Unit) {
    val members = state.live?.members.orEmpty()
    val alone = members.size <= 1
    var person by remember { mutableStateOf<CallMember?>(null) }
    var tick by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = Clock.System.now().toEpochMilliseconds()
        }
    }
    val startedAt = state.live?.startedAtMillis ?: tick
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(SpeakyIcons.ChevronDown, "Minimize call", CallSoft, onClick = onMinimize)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hub voice call", style = displayStyle(18.sp), color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveDot(CallLive)
                    val premiumNote = if (state.premiumCount > 0) " + ${state.premiumCount} Premium" else ""
                    Text(
                        when {
                            state.reconnecting -> "Reconnecting…"
                            state.phase == CallPhase.Connecting -> "Connecting…"
                            alone -> "1 of $MAX_SEATS · waiting"
                            else -> "${state.regularCount} of $MAX_SEATS$premiumNote · ${clock(tick - startedAt)}"
                        },
                        color = Color(0xFFB8AFF0), fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.size(44.dp))
        }

        if (alone) {
            Waiting(members.firstOrNull(), manager.nobodyJoinedLeftMs()?.let { clock(it) }, Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(members, key = { it.userId }) { member ->
                    Seat(
                        member = member,
                        isMe = member.userId == manager.myId(),
                        speaking = member.userId in state.speaking,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { if (state.isAdmin && member.userId != manager.myId()) person = member },
                        ),
                    )
                }
            }
        }

        if (state.isAdmin && !alone) {
            Text(
                "End call for everyone",
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(CallRed)
                    .clickable(role = Role.Button, onClick = manager::adminEnd).wrapContentHeight(),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Control(if (state.muted) SpeakyIcons.MicOff else SpeakyIcons.Mic, if (state.muted) "Unmute" else "Mute",
                if (state.muted) Color.White else CallSoft, if (state.muted) CallBg else Color.White, manager::toggleMute)
            Control(SpeakyIcons.Speaker, if (state.speaker) "Speaker" else "Earpiece",
                if (state.speaker) CallSoft else Color(0x0DFFFFFF), Color.White, manager::toggleSpeaker)
            Control(SpeakyIcons.ChevronDown, "Minimize", CallSoft, Color.White, onMinimize)
            Control(SpeakyIcons.Phone, "Leave", CallRed, Color.White, onLeave, rotate = 135f)
        }
    }
    person?.let { member ->
        CallPersonSheet(
            member = member,
            onMute = { manager.adminMute(member.userId); person = null },
            onRemove = { manager.adminRemove(member.userId); person = null },
            onBan = { days -> manager.adminBan(member.userId, days); person = null },
            onDismiss = { person = null },
        )
    }
}

@Composable
private fun Waiting(me: CallMember?, left: String?, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "wait")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Restart), label = "wave")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).scale(1f + 0.6f * wave).alpha(0.5f * (1f - wave)).border(2.dp, Color(0xFF8B7BFF), CircleShape))
            if (me != null) UserAvatar(me.nickname, me.photoUrl, 120.dp, Color.Transparent)
        }
        Text(if (left == null) "Everyone else left" else "Waiting for others to join…", style = displayStyle(24.sp), color = Color.White, textAlign = TextAlign.Center)
        Text("Everyone in the Hub can see your call and join it.", color = CallMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
        // The 5-minute close only applies to a call nobody has joined yet.
        if (left != null) Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x1AFFFFFF)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Closes in", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(left, style = displayStyle(18.sp), color = Color(0xFFFFB38F))
            Text("if nobody joins", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Seat(member: CallMember, isMe: Boolean, speaking: Boolean, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "speak")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "pulse")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            if (speaking) Box(Modifier.size(84.dp).scale(1f + 0.18f * pulse).alpha(1f - pulse).border(3.dp, CallLive, CircleShape))
            val ring = when {
                speaking -> CallLive
                member.premiumSeat -> Gold.last()
                else -> Color.Transparent
            }
            Box(Modifier.size(84.dp).border(3.dp, ring, CircleShape).padding(3.dp)) {
                UserAvatar(member.nickname, member.photoUrl, 78.dp, Color.Transparent)
            }
            if (member.premiumSeat) {
                Box(
                    Modifier.align(Alignment.TopCenter).size(26.dp).clip(CircleShape).background(CallBg),
                    contentAlignment = Alignment.Center,
                ) { Icon(SpeakyIcons.Crown, null, tint = Gold.first(), modifier = Modifier.size(16.dp)) }
            }
            if (member.muted) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(CallBg).padding(2.dp)
                        .clip(CircleShape).background(CallRed),
                    contentAlignment = Alignment.Center,
                ) { Icon(SpeakyIcons.MicOff, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            }
        }
        Text(
            if (isMe) "You" else member.nickname,
            color = when {
                speaking -> Color(0xFF7DE3BE)
                member.premiumSeat -> Gold.first()
                else -> Color.White
            },
            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CallEndedContent(reason: CallEnd, onClose: () -> Unit, onStartAgain: () -> Unit) {
    val (title, body) = when (reason) {
        CallEnd.NobodyJoined -> "No one joined this time" to
            "We closed your call after 5 minutes so it doesn't run empty. Don't worry, it happens! Say hi in the Hub chat first, then try again."
        CallEnd.Admin -> "This call was ended by an admin" to "Thanks for practising! You can keep chatting in the Hub."
        CallEnd.CallsOff -> "Voice calls are paused" to "An admin turned calls off for now. You can keep chatting in the Hub."
        CallEnd.Removed -> "You were removed from the call" to "An admin removed you from this call. You can keep chatting in the Hub."
        CallEnd.ConnectionLost -> "Connection lost" to "We couldn't reconnect. Your network may block calls: try another Wi-Fi or mobile data, then join again from the Hub."
        CallEnd.Left -> "The call ended" to "Everyone left the call."
    }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(104.dp).clip(RoundedCornerShape(36.dp)).background(CallSoft), contentAlignment = Alignment.Center) {
            Icon(if (reason == CallEnd.Admin || reason == CallEnd.CallsOff) SpeakyIcons.Shield else SpeakyIcons.Phone, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Text(title, style = displayStyle(26.sp, 30.sp), color = Color.White, textAlign = TextAlign.Center)
        Text(body, color = CallMuted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        if (reason == CallEnd.NobodyJoined) {
            SpeakyPrimaryButton("Start again", onStartAgain, background = CallGreen, color = Color.White, leading = SpeakyIcons.Phone)
        }
        SpeakyPrimaryButton("Back to Hub chat", onClose, background = CallSoft, color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallPersonSheet(member: CallMember, onMute: () -> Unit, onRemove: () -> Unit, onBan: (Int?) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    var days by remember { mutableStateOf<Int?>(7) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UserAvatar(member.nickname, member.photoUrl, 48.dp, Color.Transparent)
                Text(member.nickname, style = displayStyle(20.sp), color = colors.textPrimary)
            }
            AdminRow(SpeakyIcons.MicOff, "Mute ${member.nickname}", "They can unmute themselves again", colors.textPrimary, colors.backgroundDark, onMute)
            AdminRow(SpeakyIcons.SignOut, "Remove from this call", "They can join the next call", Color(0xFF9A3412), Color(0xFFFFF1EC), onRemove)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFDECEC)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(SpeakyIcons.Ban, null, tint = Color(0xFFB42318), modifier = Modifier.size(20.dp))
                    Column {
                        Text("Ban from voice calls", color = Color(0xFFB42318), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Removes them now and stops them joining calls", color = colors.textMuted, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "24h", 7 to "7 days", 30 to "30 days", null to "Forever").forEach { (value, label) ->
                        val on = days == value
                        Text(
                            label,
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Color.White else Color(0x99FFFFFF))
                                .border(2.dp, if (on) Color(0xFFB42318) else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { days = value }.padding(vertical = 8.dp),
                            color = Color(0xFFB42318), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        )
                    }
                }
                SpeakyPrimaryButton("Ban", { onBan(days) }, background = Color(0xFFB42318), color = Color.White, height = 46.dp, radius = 14.dp)
            }
        }
    }
}

@Composable
private fun AdminRow(icon: ImageVector, title: String, subtitle: String, color: Color, background: Color, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = colors.textMuted, fontSize = 12.sp)
        }
    }
}

// ---------------- Minimized pill and ended card (whole app) ----------------

/** Shown over every screen while in a call that isn't open: tap to return, mute, leave. */
@Composable
fun HubCallOverlay(onOpenCall: () -> Unit, modifier: Modifier = Modifier) {
    val manager = koinInject<HubCallManager>()
    val state by manager.state.collectAsStateWithLifecycle()
    val screenOpen by manager.screenOpen.collectAsStateWithLifecycle()
    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(3500)
            manager.clearMessage()
        }
        Text(
            message,
            modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 70.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF17142B)).padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    if (screenOpen) return
    if (state.ended != null) {
        Row(
            modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 14.dp, end = 14.dp, top = 76.dp).fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)).background(Color(0xFF17142B)).clickable(onClick = onOpenCall)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.ended == CallEnd.NobodyJoined) "No one joined your call this time. Tap to see why." else "Your Hub call ended.",
                Modifier.weight(1f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text("OK", Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = manager::dismissEnded).padding(8.dp), color = CallLive, fontWeight = FontWeight.ExtraBold)
        }
        return
    }
    if (!state.inCall) return
    // Sits below the screen's header (back button, title) so it never covers them.
    val speaker = state.live?.members?.firstOrNull { it.userId in state.speaking }
    // Compact, bottom right, above the tab bar and chat composer: never covers a header or the back button.
    Row(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(end = 14.dp, bottom = 120.dp)
            .wrapContentSize(Alignment.BottomEnd).widthIn(max = 300.dp).height(60.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFF17142B)).clickable(onClick = onOpenCall).padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val first = speaker ?: state.live?.members?.firstOrNull()
        if (first != null) UserAvatar(first.nickname, first.photoUrl, 40.dp, Color.Transparent)
        Column(Modifier.widthIn(max = 130.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiveDot(CallLive)
                Text("Hub call", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            val count = state.live?.members?.size ?: 1
            Text(
                speaker?.let { "${it.nickname} is speaking" } ?: if (count <= 1) "Waiting for others" else "$count people",
                color = Color(0xFFB8AFF0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        RoundButton(if (state.muted) SpeakyIcons.MicOff else SpeakyIcons.Mic, if (state.muted) "Unmute" else "Mute",
            if (state.muted) Color.White else CallSoft, size = 44.dp, tint = if (state.muted) CallBg else Color.White, onClick = manager::toggleMute)
        RoundButton(SpeakyIcons.Phone, "Leave", CallRed, size = 44.dp, rotate = 135f, onClick = manager::leave)
    }
}

// ---------------- Small pieces ----------------

@Composable
private fun Control(icon: ImageVector, label: String, background: Color, tint: Color, onClick: () -> Unit, rotate: Float = 0f) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundButton(icon, label, background, size = 64.dp, tint = tint, rotate = rotate, onClick = onClick)
        Text(label, color = CallMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    background: Color,
    size: Dp = 44.dp,
    tint: Color = Color.White,
    rotate: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.4f).rotate(rotate))
    }
}

@Composable
private fun SmallButton(text: String, background: Color?, color: Color, icon: ImageVector?, gold: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(14.dp))
            .then(if (gold) Modifier.background(Brush.linearGradient(Gold)) else Modifier.background(background ?: Color.Transparent))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun AvatarStack(members: List<CallMember>) {
    Box(Modifier.width((30 + (members.size - 1).coerceAtLeast(0) * 20).dp).height(30.dp)) {
        members.forEachIndexed { i, m ->
            Box(Modifier.offset(x = (i * 20).dp).size(30.dp).clip(CircleShape).background(Color.White).padding(2.dp)) {
                UserAvatar(m.nickname, m.photoUrl, 26.dp, Color.Transparent)
            }
        }
    }
}

@Composable
private fun LiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(color))
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun banText(until: Long?): String {
    if (until == null || until == Long.MAX_VALUE) return "You can't join voice calls"
    val days = ((until - Clock.System.now().toEpochMilliseconds()) / 86_400_000L).coerceAtLeast(0) + 1
    return "You can join calls again in $days day${if (days == 1L) "" else "s"}"
}
