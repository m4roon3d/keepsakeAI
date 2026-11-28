package com.keepsakeai.app.ui.screens

import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.ui.focus.focusRequester
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.GradientCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

/** Accent per Smart Collection, used for card stripes and labels. */
fun collectionAccent(name: String): Pair<Color, Color> = when (name) {
    "Travel" -> Flow.Sky to Flow.Cyan
    "Study Notes" -> Flow.Indigo to Flow.Violet
    "Events" -> Flow.Pink to Flow.Purple
    "Personal Projects" -> Flow.Purple to Flow.Indigo
    "Receipts" -> Flow.Emerald to Flow.Teal
    "Watch Later" -> Flow.Amber to Flow.Pink
    "Journal" -> Flow.Pink to Flow.Indigo
    else -> Flow.Indigo to Flow.Sky
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    viewModel: MindSpaceViewModel,
    onTriggerScreenSnap: () -> Unit,
    onOpenPilot: () -> Unit,
    onOpenSnapsake: () -> Unit,
    onOpenSpend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFlow.current
    val memories by viewModel.filteredMemories.collectAsState()
    val allMemories by viewModel.allMemories.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val near by viewModel.nearHere.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshNearHere() }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshCalendar() }
    val collections by viewModel.collections.collectAsState()
    val selectedCol by viewModel.selectedCollection.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val pendingUpi by viewModel.pendingUpi.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val semantic by viewModel.semanticResults.collectAsState()

    var quickNote by remember { mutableStateOf("") }
    var hubView by rememberSaveable { mutableStateOf(HubView.KEEPSAKES) }
    val hubReq by viewModel.hubRequest.collectAsState()
    val noteFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusNote by viewModel.focusNote.collectAsState()
    androidx.compose.runtime.LaunchedEffect(focusNote) {
        if (focusNote) {
            kotlinx.coroutines.delay(400)
            runCatching { noteFocus.requestFocus() }
            viewModel.focusNote.value = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(hubReq) {
        hubReq?.let { r -> HubView.entries.firstOrNull { it.name == r }?.let { hubView = it }; viewModel.hubRequest.value = null }
    }
    val facts by viewModel.facts.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val expiryScanning by viewModel.expiryScanning.collectAsState()
    val reviewedIds by viewModel.reviewed.collectAsState()
    val reviewPending = remember(allMemories, reviewedIds) { com.keepsakeai.app.review.DailyReview.pending(allMemories, reviewedIds, System.currentTimeMillis()) }
    val expenses by viewModel.expenses.collectAsState()
    val context = LocalContext.current
    var pendingReminder by remember { mutableStateOf<com.keepsakeai.app.data.preferences.KeepReminder?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingReminder?.let { r -> viewModel.scheduleReminder(r) }
        pendingReminder = null
    }

    // Gallery documents (not the photo picker) so trove can read where each photo or video was taken.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.captureMediaUris(uris.take(10), CaptureSource.SHARE_SHEET)
    }
    val mediaLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        imagePicker.launch(arrayOf("image/*", "video/*"))
    }
    fun pickMedia() {
        if (com.keepsakeai.app.capture.MediaMeta.canReadPhotoLocation(context)) imagePicker.launch(arrayOf("image/*", "video/*"))
        else mediaLocationPermission.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.captureText(spoken, CaptureSource.VOICE_MEMO)
        }
    }

    val searching = searchQuery.isNotBlank()
    val todosNow by viewModel.todos.collectAsState()
    // Imports (email, SMS) only get a card when new or relevant; picking a collection or searching shows everything.
    val relevantIds = remember(reminders, todosNow) {
        com.keepsakeai.app.finance.ImportSections.relevantIds(
            todos = todosNow.filter { !it.done }.map { it.memoryId to it.due },
            reminders = reminders.filter { it.isUpcoming() && com.keepsakeai.app.expiry.Expiries.showOnHome(it, System.currentTimeMillis()) }.map { it.memoryId to it.atMillis },
            now = System.currentTimeMillis()
        )
    }
    var dateChip by remember { mutableStateOf<com.keepsakeai.app.feed.DateWindow?>(null) }
    val curated = !searching && selectedCol == null && dateChip == null
    val visibleMemories = if (dateChip != null && !searching) {
        // A date chip lists everything from that stretch of time, imports included.
        val r = com.keepsakeai.app.feed.DateWindows.range(dateChip!!, System.currentTimeMillis())
        memories.filter { it.createdAt in r }
    } else if (curated) {
        val now = System.currentTimeMillis()
        // Receipts stay out of the feed (they live in Spend); a bill with a due date or reminder still shows.
        memories.filter {
            (it.collectionName != "Receipts" || it.id in relevantIds) &&
                com.keepsakeai.app.finance.ImportSections.showInFeed(it, now, relevantIds)
        }
    } else memories
    val tuckedAway = memories.size - visibleMemories.size
    val cards: List<Pair<MemoryEntity, Float?>> =
        if (searching) semantic.map { it.memory to it.similarity.takeIf { s -> s >= 0f } } else visibleMemories.map { it to null }

    val refreshing by viewModel.isRefreshing.collectAsState()
    val todos by viewModel.todos.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val backfilling by viewModel.isBackfilling.collectAsState()
    val openTodos = todos.count { !it.done }
    val dueToday = todos.count { !it.done && it.due != null && it.due < com.keepsakeai.app.ui.spend.startOfDay(System.currentTimeMillis()) + com.keepsakeai.app.ui.spend.DAY }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refreshAll() },
        modifier = modifier
    ) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gap.l, end = Gap.l, top = Gap.s, bottom = DockClearance),
        horizontalArrangement = Arrangement.spacedBy(Gap.m),
        verticalItemSpacing = 10.dp
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            FlowHeader(
                eyebrow = "your memory hub",
                title = "",
                gradientSuffix = "trove",
                leading = { LogoMark(size = 46.dp) }
            ) {
                GlassButton("snap", onClick = onTriggerScreenSnap, icon = Icons.Rounded.Screenshot)
            }
        }

        item(span = StaggeredGridItemSpan.FullLine) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassField(
                    value = searchQuery,
                    onValueChange = { viewModel.runSemanticSearch(it) },
                    placeholder = "Search memories by meaning…",
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.weight(1f)
                )
                if (searching) {
                    Spacer(Modifier.width(Gap.s))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c.glass)
                            .clickable { viewModel.runSemanticSearch("") },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, "Clear search", tint = c.muted) }
                }
            }
        }

        if (!searching) {
            item(span = StaggeredGridItemSpan.FullLine) {
                HubPills(hubView, openTodos, entities.count { !it.hidden }, facts.size, com.keepsakeai.app.expiry.Expiries.list(reminders, System.currentTimeMillis()).size, reviewPending.size) { hubView = it }
            }
        }
        if (!searching && hubView != HubView.KEEPSAKES) {
            when (hubView) {
                HubView.TODOS -> todoItems(
                    todos,
                    onAdd = { text, due -> viewModel.addTodo(text, due) },
                    onToggle = { viewModel.toggleTodo(it) },
                    onDelete = { viewModel.deleteTodo(it) },
                    onRemind = { t ->
                        if (android.os.Build.VERSION.SDK_INT >= 33 && !com.keepsakeai.app.notify.Notifier.canNotify(context)) {
                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.remindTodo(t)
                    },
                    onSetDue = { id, due -> viewModel.setTodoDue(id, due) },
                    onOpenSource = { viewModel.openMemory(it) }
                )
                HubView.PEOPLE -> peopleItems(
                    entities,
                    unscanned = (allMemories.count { it.source != com.keepsakeai.app.data.local.CaptureSource.UPI_MESSAGE } - viewModel.backfilledCount()).coerceAtLeast(0),
                    backfilling = backfilling,
                    onBackfill = { viewModel.backfillEntities() },
                    onOpen = { viewModel.openEntityKey.value = it }
                )
                HubView.TIMELINE -> timelineItems(allMemories, expenses, onOpen = { viewModel.openMemory(it) })
                HubView.FACTS -> factItems(facts, onAdd = { viewModel.addFact(it) }, onPin = { viewModel.togglePin(it) }, onDelete = { viewModel.deleteFact(it) })
                HubView.REVIEW -> reviewItems(
                    reviewPending,
                    total = maxOf(reviewPending.size, com.keepsakeai.app.review.DailyReview.recentCount(allMemories, System.currentTimeMillis())),
                    onKeep = { viewModel.reviewKeep(it) },
                    onToDo = { viewModel.reviewToDo(it) },
                    onDelete = { viewModel.reviewDelete(it) },
                    onOpen = { viewModel.openMemory(it) }
                )
                HubView.EXPIRIES -> expiryItems(
                    reminders, scanning = expiryScanning,
                    onScan = { viewModel.scanForExpiries() },
                    onOpen = { viewModel.openMemory(it) },
                    onDismiss = { viewModel.dismissReminder(it) }
                )
                HubView.IMPORTS -> importItems(allMemories, onOpen = { viewModel.openMemory(it) })
                HubView.THREADS -> threadItems(allMemories, onAsk = { q -> viewModel.pilotDraft.value = q; onOpenPilot() }, onOpen = { viewModel.openMemory(it) })
                else -> {}
            }
        } else {
        if (!searching) {
            // Quick capture composer
            item(span = StaggeredGridItemSpan.FullLine) {
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = Gap.s, end = Gap.m, top = Gap.s, bottom = Gap.m)) {
                    GlassField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        placeholder = "What's on your mind? A thought, link, plan…",
                        singleLine = false,
                        minLines = 2,
                        bare = true,
                        modifier = Modifier.fillMaxWidth(),
                        fieldModifier = Modifier.focusRequester(noteFocus)
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(horizontal = Gap.m))
                    Spacer(Modifier.height(Gap.s))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Gap.s)) {
                        ComposerIcon(Icons.Rounded.CenterFocusWeak, "lens", Flow.Indigo, onOpenSnapsake)
                        ComposerIcon(Icons.Rounded.Mic, "Voice note", Flow.Pink) {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            }
                            runCatching { voiceLauncher.launch(intent) }
                        }
                        ComposerIcon(Icons.Rounded.AutoAwesome, "Voice journal", Flow.Purple) { viewModel.overlay.value = "journal" }
                        ComposerIcon(Icons.Rounded.AddPhotoAlternate, "Read text from a photo", Flow.Emerald) {
                            pickMedia()
                        }
                        Spacer(Modifier.weight(1f))
                        GradientButton(
                            text = if (isCapturing) "Saving…" else "Save",
                            enabled = quickNote.isNotBlank() && !isCapturing,
                            onClick = {
                                viewModel.captureText(quickNote)
                                quickNote = ""
                            }
                        )
                    }
                }
            }

            val upcoming = reminders.filter { it.isUpcoming() && com.keepsakeai.app.expiry.Expiries.showOnHome(it, System.currentTimeMillis()) }.sortedBy { it.atMillis }.take(3)
            val nowMs = System.currentTimeMillis()
            val dayStart = com.keepsakeai.app.ui.spend.startOfDay(nowMs)
            val nextEvent = calendarEvents.firstOrNull { it.end > nowMs && !(it.allDay && it.begin < dayStart) }
            // Rest of today, or tomorrow morning once the evening sets in.
            val calendarSoon = calendarEvents.filter { e ->
                e.end > nowMs && (e.begin < dayStart + 24L * 60 * 60 * 1000 ||
                    (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 18 && e.begin < dayStart + 36L * 60 * 60 * 1000))
            }.take(4)
            item(span = StaggeredGridItemSpan.FullLine) {
                TodayCard(
                    next = upcoming.firstOrNull(),
                    memories = allMemories,
                    onAsk = onOpenPilot,
                    onTimeline = { hubView = HubView.TIMELINE },
                    openTodos = openTodos,
                    dueToday = dueToday,
                    onTodos = { hubView = HubView.TODOS },
                    onBrief = { viewModel.overlay.value = "brief" },
                    onJournal = { viewModel.overlay.value = "journal" },
                    nextEvent = nextEvent
                )
            }
            if (near.isNotEmpty()) item(key = "near_here", span = StaggeredGridItemSpan.FullLine) {
                GlassCard(Modifier.fillMaxWidth().animateItem(), onClick = { viewModel.overlay.value = "near" }) {
                    Text("Near here · ${near.size} saved within ${com.keepsakeai.app.places.NearHere.RADIUS_M.toInt()} m", fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(near.first().memory.title, fontSize = Ts.meta, color = c.muted, maxLines = 1)
                }
            }
            if (calendarSoon.isNotEmpty()) item(key = "calendar", span = StaggeredGridItemSpan.FullLine) {
                CalendarTodayCard(calendarSoon, Modifier.animateItem())
            }
            items(upcoming, key = { "rem${it.id}" }, span = { StaggeredGridItemSpan.FullLine }) { r ->
                ReminderSuggestionCard(r, modifier = Modifier.animateItem(), onRemind = {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && !com.keepsakeai.app.notify.Notifier.canNotify(context)) {
                        pendingReminder = r
                        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else viewModel.scheduleReminder(r)
                }, onDismiss = { viewModel.dismissReminder(r) })
            }
            val dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            if (dow == java.util.Calendar.SUNDAY || dow == java.util.Calendar.MONDAY) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.overlay.value = "review" }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LogoMark(size = 34.dp, halo = false)
                            Spacer(Modifier.width(Gap.m))
                            Column(Modifier.weight(1f)) {
                                Text("your week is ready", style = MaterialTheme.typography.titleSmall, color = c.text)
                                Text("spend, highlights and one insight", fontSize = Ts.meta, color = c.muted)
                            }
                            Text("open →", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
                        }
                    }
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) { OnThisDayCard(allMemories, expenses, onOpen = { viewModel.openMemory(it) }) }
            // Threads you're in the middle of, then a compact money row
            item(span = StaggeredGridItemSpan.FullLine) {
                ThreadsCarousel(allMemories, onOpen = { hubView = HubView.THREADS })
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                    SectionLabel("your memories", trailing = "${allMemories.size} kept")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        item {
                            Pill("All (${allMemories.size})", selectedCol == null && dateChip == null, {
                                viewModel.selectedCollection.value = null; dateChip = null
                            })
                        }
                        items(com.keepsakeai.app.feed.DateWindow.values().toList()) { w ->
                            Pill(w.label, dateChip == w, { dateChip = if (dateChip == w) null else w })
                        }
                        items(collections) { col ->
                            val n = allMemories.count { it.collectionName == col }
                            Pill("$col ($n)", selectedCol == col, { viewModel.selectedCollection.value = col })
                        }
                    }
                }
            }
        } else {
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionLabel("Closest matches", trailing = "${cards.size} found")
            }
        }

        if (tuckedAway > 0) {
            item(key = "tucked", span = StaggeredGridItemSpan.FullLine) {
                GlassCard(Modifier.fillMaxWidth(), onClick = { hubView = HubView.IMPORTS }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$tuckedAway receipts and older imports kept out of your feed", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("Search, the Today / Yesterday / This week chips, or browse by type.", fontSize = Ts.meta, color = c.muted)
                        }
                        Text("browse →", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
                    }
                }
            }
        }

        // The spend card stays out of the feed; it only appears when you search about money ("what did I spend today").
        if (searching && com.keepsakeai.app.ui.spend.isMoneyQuestion(searchQuery)) {
            item(key = "spend_answer", span = StaggeredGridItemSpan.FullLine) {
                SpendAnswerCard(expenses, pendingUpi.size, onOpen = onOpenSpend)
            }
        }

        if (cards.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (searching) "Nothing close to that yet." else "Your space is empty. Share anything to trove from any app, or type a note above.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(cards, key = { it.first.id }) { (mem, score) ->
                MemoryCard(mem, score, onDelete = { viewModel.deleteMemory(mem) }, onOpen = { viewModel.openMemory(mem.id) }, modifier = Modifier.animateItem())
            }
        }
        }
    }
    }
}

@Composable
private fun WhiteChip(text: String, solid: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (solid) Color.White else Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = Gap.l, vertical = Gap.s)
    ) {
        Text(text, color = if (solid) Flow.IndigoDeep else Color.White, fontSize = Ts.title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ComposerIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.10f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp)) }
    Spacer(Modifier.width(Gap.s))
}

@Composable
private fun MemoryCard(mem: MemoryEntity, score: Float?, onDelete: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val (a1, a2) = collectionAccent(mem.collectionName)
    val photo = mem.photoUrisCsv.split(",").firstOrNull { it.isNotBlank() }
    GlassCard(modifier, padding = PaddingValues(Gap.m), onClick = onOpen) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = mem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(Gap.m))
        } else {
            Box(
                Modifier.width(40.dp).height(5.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(a1, a2)))
            )
            Spacer(Modifier.height(Gap.m))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mem.collectionName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = a1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Rounded.DeleteOutline, "Delete",
                tint = c.muted,
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onDelete)
            )
        }
        Spacer(Modifier.height(Gap.xs))
        Text(mem.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Gap.xs))
        Text(mem.aiSummary, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 5, overflow = TextOverflow.Ellipsis)
        val place = remember(mem.rawContent) { com.keepsakeai.app.places.PlaceTag.shortPlace(mem.rawContent) }
        if (place != null) {
            Spacer(Modifier.height(Gap.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Rounded.LocationOn, null, tint = Flow.Pink, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(Gap.xs))
                Text(place, fontSize = Ts.meta, fontWeight = FontWeight.Medium, color = c.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val tag = mem.tagsCsv.split(",").firstOrNull { it.isNotBlank() }
        if (tag != null || score != null) {
            Spacer(Modifier.height(Gap.s))
            HorizontalDivider(color = c.divider)
            Spacer(Modifier.height(Gap.s))
            Row {
                Text(tag?.let { "#$it" } ?: "", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Sky, modifier = Modifier.weight(1f), maxLines = 1)
                if (score != null) Text("${(score * 100).toInt()}%", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Emerald)
            }
        }
    }
}
