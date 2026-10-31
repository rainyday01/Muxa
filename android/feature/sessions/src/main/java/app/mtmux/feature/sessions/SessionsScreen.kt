package app.mtmux.feature.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.model.Host
import app.mtmux.core.model.HostResourceText
import app.mtmux.core.model.HostResources
import app.mtmux.core.model.SessionListMode
import app.mtmux.core.model.SnapshotText
import app.mtmux.core.model.TmuxSession
import app.mtmux.core.ui.R
import app.mtmux.core.ui.theme.LocalMtmuxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    sessions: List<TmuxSession>,
    hosts: List<Host> = emptyList(),
    loading: Boolean,
    message: String?,
    emptyNeedsInstall: Boolean,
    listMode: SessionListMode = SessionListMode.GroupedByHost,
    /** sessionKey = hostId\\tname → last opened epoch ms */
    lastOpened: Map<String, Long> = emptyMap(),
    /** User-defined host id order (from 上移/下移). */
    hostOrder: List<String> = emptyList(),
    /** User-defined session keys hostId\\tname order. */
    sessionOrder: List<String> = emptyList(),
    /**
     * Key of the session this app currently has a live PTY/Mosh for.
     * Green/yellow dots use this only — not remote tmux "someone attached".
     */
    liveSessionKey: String? = null,
    onListModeChange: (SessionListMode) -> Unit = {},
    onRefresh: () -> Unit,
    onOpen: (TmuxSession) -> Unit,
    onMore: (TmuxSession) -> Unit,
    onNewSession: () -> Unit,
    onOpenInstall: () -> Unit,
    onAddHost: () -> Unit = {},
) {
    val theme = LocalMtmuxTheme.current
    Box(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_sessions"),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Material You large title + refresh only (create via FAB)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.sessions_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("session_refresh")
                        .semantics { contentDescription = "session_refresh" },
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "session_refresh",
                        tint = theme.colors.primary,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = listMode == SessionListMode.GroupedByHost,
                    onClick = { onListModeChange(SessionListMode.GroupedByHost) },
                    label = { Text(stringResource(R.string.sessions_by_host), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.ViewAgenda, null, Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.colors.secondaryContainer,
                        selectedLabelColor = theme.colors.onSecondaryContainer,
                    ),
                )
                FilterChip(
                    selected = listMode == SessionListMode.RecentFlat,
                    onClick = { onListModeChange(SessionListMode.RecentFlat) },
                    label = { Text(stringResource(R.string.sessions_recent), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.ViewList, null, Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.colors.secondaryContainer,
                        selectedLabelColor = theme.colors.onSecondaryContainer,
                    ),
                )
            }
            if (message != null) {
                Text(
                    message,
                    color = theme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                )
            }
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (sessions.isEmpty() && !loading) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.sessions_empty),
                            color = theme.colors.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                hosts.isEmpty() -> stringResource(R.string.sessions_empty_no_hosts)
                                emptyNeedsInstall -> stringResource(R.string.sessions_empty_need_install)
                                else -> stringResource(R.string.sessions_empty_hint)
                            },
                            color = theme.colors.onSurfaceMuted,
                            fontSize = 14.sp,
                        )
                        if (hosts.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onAddHost) { Text(stringResource(R.string.sessions_add_host)) }
                        } else if (emptyNeedsInstall) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onOpenInstall) { Text(stringResource(R.string.sessions_install)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (hosts.isNotEmpty()) {
                            Button(onClick = onNewSession) { Text(stringResource(R.string.sessions_new)) }
                        }
                    }
                } else {
                    when (listMode) {
                        SessionListMode.GroupedByHost -> GroupedSessionList(
                            sessions = sessions,
                            hosts = hosts,
                            hostOrder = hostOrder,
                            sessionOrder = sessionOrder,
                            lastOpened = lastOpened,
                            liveSessionKey = liveSessionKey,
                            onOpen = onOpen,
                            onMore = onMore,
                        )
                        SessionListMode.RecentFlat -> FlatRecentSessionList(
                            sessions = sessions,
                            hosts = hosts,
                            lastOpened = lastOpened,
                            liveSessionKey = liveSessionKey,
                            onOpen = onOpen,
                            onMore = onMore,
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNewSession,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("session_new")
                .semantics { contentDescription = "session_new" },
            containerColor = theme.colors.primaryContainer,
            contentColor = theme.colors.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "session_new")
        }
    }
}

@Composable
private fun GroupedSessionList(
    sessions: List<TmuxSession>,
    hosts: List<Host>,
    hostOrder: List<String>,
    sessionOrder: List<String>,
    lastOpened: Map<String, Long>,
    liveSessionKey: String?,
    onOpen: (TmuxSession) -> Unit,
    onMore: (TmuxSession) -> Unit,
) {
    val hostIndex = hostOrder.withIndex().associate { it.value to it.index }
    val sessionIndex = sessionOrder.withIndex().associate { it.value to it.index }

    val groups = sessions
        .groupBy { it.hostId }
        .toList()
        .sortedWith(
            compareBy<Pair<String, List<TmuxSession>>> { (hostId, _) ->
                hostIndex[hostId] ?: Int.MAX_VALUE
            }.thenBy { (_, list) ->
                list.firstOrNull()?.hostAlias?.lowercase()
                    ?: list.firstOrNull()?.hostId?.lowercase()
                    ?: ""
            },
        )

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { (hostId, list) ->
            val hostLabel = list.firstOrNull()?.hostAlias?.takeIf { it.isNotBlank() } ?: hostId
            val sortedSessions = list.sortedWith(
                compareBy<TmuxSession> { sessionIndex[it.key()] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() },
            )
            item(key = "hdr_$hostId") {
                HostGroupHeader(
                    label = hostLabel,
                    resources = hosts.find { it.id == hostId }?.lastResources,
                )
            }
            items(sortedSessions, key = { "${it.hostId}\t${it.name}" }) { session ->
                SessionCard(
                    session = session,
                    showHost = false,
                    liveConnected = liveSessionKey == session.key(),
                    lastOpenedEpochMs = lastOpened[session.key()],
                    onOpen = { onOpen(session) },
                    onMore = { onMore(session) },
                )
            }
        }
    }
}

@Composable
private fun FlatRecentSessionList(
    sessions: List<TmuxSession>,
    hosts: List<Host>,
    lastOpened: Map<String, Long>,
    liveSessionKey: String?,
    onOpen: (TmuxSession) -> Unit,
    onMore: (TmuxSession) -> Unit,
) {
    val sorted = sessions.sortedWith(
        compareByDescending<TmuxSession> {
            SnapshotText.recencyEpochMs(lastOpened[it.key()], it.snapshotUpdatedAtEpochMs) ?: 0L
        }.thenBy { it.name },
    )
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(sorted, key = { "${it.hostId}\t${it.name}" }) { session ->
            SessionCard(
                session = session,
                showHost = true,
                hostResources = hosts.find { it.id == session.hostId }?.lastResources,
                liveConnected = liveSessionKey == session.key(),
                lastOpenedEpochMs = lastOpened[session.key()],
                onOpen = { onOpen(session) },
                onMore = { onMore(session) },
            )
        }
    }
}

@Composable
private fun HostGroupHeader(
    label: String,
    resources: HostResources?,
) {
    val theme = LocalMtmuxTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = theme.colors.primary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HostResourceChips(resources)
    }
}

@Composable
private fun HostResourceChips(resources: HostResources?) {
    val theme = LocalMtmuxTheme.current
    val res = resources ?: return
    if (res.isEmpty) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        res.memAvailKb?.let { kb ->
            val low = HostResourceText.memLow(kb, res.memTotalKb)
            Text(
                stringResource(R.string.stat_ram, HostResourceText.formatKb(kb)),
                color = if (low) theme.colors.warning else theme.colors.onSurfaceMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        res.diskAvailKb?.let { kb ->
            val low = HostResourceText.diskLow(kb, res.diskTotalKb)
            Text(
                stringResource(R.string.stat_disk, HostResourceText.formatKb(kb)),
                color = if (low) theme.colors.warning else theme.colors.onSurfaceMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: TmuxSession,
    showHost: Boolean,
    hostResources: HostResources? = null,
    liveConnected: Boolean,
    lastOpenedEpochMs: Long? = null,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    Card(
        shape = RoundedCornerShape(theme.cardRadiusDp.dp),
        colors = CardDefaults.cardColors(containerColor = theme.colors.cardGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Green = this app currently attached; yellow = not live
                Spacer(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (liveConnected) theme.colors.success else theme.colors.warning,
                        )
                        .semantics {
                            contentDescription =
                                if (liveConnected) "live_connected" else "not_connected"
                        },
                )
                Text(
                    session.name,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 17.sp,
                    color = theme.colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    snapshotAgeLabel(
                        none = stringResource(R.string.snap_none),
                        justNow = stringResource(R.string.snap_just_now),
                        minutes = stringResource(R.string.snap_minutes),
                        hours = stringResource(R.string.snap_hours),
                        days = stringResource(R.string.snap_days),
                        updatedAtEpochMs = SnapshotText.recencyEpochMs(
                            lastOpenedEpochMs,
                            session.snapshotUpdatedAtEpochMs,
                        ),
                    ),
                    color = theme.colors.onSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
            if (showHost) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        session.hostAlias ?: session.hostId,
                        color = theme.colors.onSurfaceMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HostResourceChips(hostResources)
                }
            }
            val snap = SnapshotText.forCard(
                session.snapshotText,
                maxLines = SnapshotText.CARD_LINES,
            )
            if (!snap.isNullOrBlank()) {
                Text(
                    snap,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.colors.terminalBackground)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = theme.colors.terminalForeground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = SnapshotText.CARD_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .testTag("session_enter_${session.name}")
                        .semantics { contentDescription = "session_enter_${session.name}" },
                ) { Text(stringResource(R.string.session_enter)) }
                TextButton(
                    onClick = onMore,
                    modifier = Modifier
                        .testTag("session_more_${session.name}")
                        .semantics { contentDescription = "session_more_${session.name}" },
                ) { Text(stringResource(R.string.session_more)) }
            }
        }
    }
}

/**
 * Coarse relative age for the session-card corner chip (last used).
 * <1m Just now · <1h minutes · <1d hours · else days · none if never used/snapped.
 */
internal fun snapshotAgeLabel(
    none: String,
    justNow: String,
    minutes: String,
    hours: String,
    days: String,
    updatedAtEpochMs: Long?,
    now: Long = System.currentTimeMillis(),
): String {
    if (updatedAtEpochMs == null || updatedAtEpochMs <= 0L) return none
    val delta = (now - updatedAtEpochMs).coerceAtLeast(0L)
    val min = delta / 60_000L
    val hour = delta / 3_600_000L
    val day = delta / 86_400_000L
    return when {
        min < 1 -> justNow
        hour < 1 -> minutes
        day < 1 -> hours
        else -> days
    }
}
