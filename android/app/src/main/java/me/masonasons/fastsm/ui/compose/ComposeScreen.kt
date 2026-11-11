package me.masonasons.fastsm.ui.compose

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableLongStateOf
import me.masonasons.fastsm.ui.PollDraft
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.masonasons.fastsm.ui.ComposeContext
import me.masonasons.fastsm.ui.CoreViewModel
import me.masonasons.fastsm.ui.MentionSuggestion
import me.masonasons.fastsm.ui.OutgoingMedia

private data class PickedMedia(val uri: Uri, val name: String, val mime: String, val alt: String)

/** Resolve a content Uri's display name + MIME type for the upload. */
private fun queryMeta(context: Context, uri: Uri): Pair<String, String> {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    var name = "attachment"
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.let { name = it }
                }
            }
    }
    return name to mime
}

// Labels track Mastodon's current official names.
private fun visibilityLabel(v: Int): String = when (v) {
    0 -> "Public"
    1 -> "Quiet public"
    2 -> "Followers"
    3 -> "Specific people"
    else -> "Public"
}

/**
 * The composer, driven entirely by the core's compose_context: it fills in the
 * reply/quote/edit target, char limit, and platform capabilities, and `post`
 * does the sending. Media and polls come in a later pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    viewModel: CoreViewModel,
    ctx: ComposeContext,
    onDone: () -> Unit,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(ctx.prefillText, TextRange(ctx.prefillText.length)))
    }
    var cw by remember { mutableStateOf(ctx.prefillCw) }
    // @-mention picker: open flag, the seed query, and the [start, caret) span of
    // the partial handle under the caret that a pick replaces.
    var mentionOpen by remember { mutableStateOf(false) }
    var mentionSeed by remember { mutableStateOf("") }
    var mentionAnchor by remember { mutableStateOf(0 to 0) }
    var visibility by remember {
        mutableIntStateOf(if (ctx.defaultVisibility in 0..3) ctx.defaultVisibility else 0)
    }
    val checked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ctx.participants.forEach { put(it.acct, it.checked) }
        }
    }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Language, poll and scheduling: new posts only (an edit keeps what it had).
    val isEdit = ctx.editId != null
    val showLanguage = ctx.languages.isNotEmpty() && !isEdit
    val showPoll = ctx.featurePolls && !isEdit
    val showSchedule = ctx.featureScheduling && !isEdit
    var languageIndex by remember { mutableIntStateOf(0) }
    var pollOn by remember { mutableStateOf(false) }
    val pollChoices = remember { mutableStateListOf("", "", "", "") }
    var pollMultiple by remember { mutableStateOf(false) }
    var pollDurationIndex by remember { mutableIntStateOf(4) } // 1 day
    var scheduleOn by remember { mutableStateOf(false) }
    var scheduleAt by remember { mutableLongStateOf(System.currentTimeMillis() + 3_600_000L) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments = remember { mutableStateListOf<PickedMedia>() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            val (name, mime) = queryMeta(context, uri)
            attachments.add(PickedMedia(uri, name, mime, ""))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.postResults.collect { ok ->
            sending = false
            if (ok) onDone() else error = "Could not send. Please try again."
        }
    }

    // Closing throws away whatever has been written, so ask first -- but only when
    // something has actually been typed. Visibility and language changes are not
    // counted: changing a dropdown and closing is not lost work, and prompting for it
    // would train people to dismiss the prompt without reading it.
    var confirmDiscard by remember { mutableStateOf(false) }
    fun closeOrAsk() {
        val dirty = field.text != ctx.prefillText || cw != ctx.prefillCw ||
            attachments.isNotEmpty() || (pollOn && pollChoices.any { it.isNotBlank() })
        if (ctx.confirmDiscard && dirty) confirmDiscard = true else onDone()
    }

    // The system back gesture discards just as thoroughly as the Cancel button.
    BackHandler(enabled = true) { closeOrAsk() }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(ctx.discardTitle) },
            text = { Text(ctx.discardText) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDone() }) {
                    Text(ctx.discardOk)
                }
            },
            // Keeping the post is the dismissive action, so a stray tap outside keeps
            // what was written.
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep writing") }
            },
        )
    }

    val remaining = ctx.maxChars - field.text.length
    val canSend = (field.text.isNotBlank() || attachments.isNotEmpty()) && remaining >= 0 && !sending

    fun send() {
        error = null
        val poll = if (showPoll && pollOn) {
            val options = pollChoices.map { it.trim() }.filter { it.isNotEmpty() }
            if (options.size < 2) {
                error = "A poll needs at least two choices."
                return
            }
            PollDraft(options, pollMultiple, POLL_DURATIONS[pollDurationIndex].second)
        } else null
        val scheduled = if (showSchedule && scheduleOn) {
            if (scheduleAt <= System.currentTimeMillis()) {
                error = "The scheduled time has to be in the future."
                return
            }
            scheduleAt / 1000
        } else null
        val language = if (showLanguage) ctx.languages.getOrNull(languageIndex)?.code else null
        sending = true
        val mentions = ctx.participants.filter { checked[it.acct] == true }.map { it.acct }
        val picked = attachments.toList()
        val body = field.text
        scope.launch {
            // Read + base64-encode off the main thread; the binary bridge uploads.
            val media = withContext(Dispatchers.IO) {
                picked.mapNotNull { m ->
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(m.uri)?.use { it.readBytes() }
                    }.getOrNull() ?: return@mapNotNull null
                    OutgoingMedia(m.name, m.mime, m.alt, Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            }
            viewModel.sendPost(
                text = body, cw = cw, visibility = visibility, mentions = mentions, media = media,
                language = language, poll = poll, scheduledAt = scheduled,
            )
        }
    }

    // Open the @-mention picker, seeded from the handle word under the caret.
    fun openMention() {
        val t = field.text
        val caret = field.selection.end.coerceIn(0, t.length)
        var start = caret
        while (start > 0 && isHandleChar(t[start - 1])) start--
        // Forward to the end of the handle as well. Anchoring at the caret left the
        // tail behind when it sat inside a half-typed handle: "@ali|ce" became
        // "@alice ce". FastSM takes the whole word around the cursor.
        var end = caret
        while (end < t.length && isHandleChar(t[end])) end++
        mentionAnchor = start to end
        mentionSeed = t.substring(start, end).removePrefix("@")
        viewModel.clearMentionSuggestions()
        mentionOpen = true
    }

    // Insert the chosen @handle, replacing the partial word the picker started on.
    fun insertMention(acct: String) {
        val (start, caret) = mentionAnchor
        val t = field.text
        val s = start.coerceIn(0, t.length)
        val c = caret.coerceIn(s, t.length)
        // A space only where there isn't one already, so completing mid-sentence
        // doesn't leave a double space behind.
        val spaceFollows = c < t.length && t[c].isWhitespace()
        val prefix = t.substring(0, s) + "@" + acct + if (spaceFollows) "" else " "
        field = TextFieldValue(prefix + t.substring(c), TextRange(prefix.length))
        mentionOpen = false
        viewModel.clearMentionSuggestions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ctx.title) },
                navigationIcon = {
                    IconButton(onClick = { closeOrAsk() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { send() }, enabled = canSend) {
                        Text(if (sending) "Sending…" else "Send")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ctx.contextLabel?.let { label ->
                Text(label, style = MaterialTheme.typography.labelLarge)
            }

            if (ctx.featureContentWarning) {
                OutlinedTextField(
                    value = cw,
                    onValueChange = { cw = it },
                    label = { Text("Content warning (optional)") },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = field,
                // "Return key sends the post": the on-screen keyboard's Return arrives
                // as a newline in the text, so catch it here instead of letting it into
                // the post. Only a single typed newline counts — pasted multi-line text
                // still goes in as-is.
                onValueChange = { new ->
                    val typedReturn = ctx.enterToSend &&
                        new.text.length == field.text.length + 1 &&
                        new.text.count { it == '\n' } == field.text.count { it == '\n' } + 1
                    if (typedReturn) {
                        if (!sending && canSend) send()
                    } else {
                        field = new
                    }
                },
                label = { Text("What's on your mind?") },
                enabled = !sending,
                minLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                // Hardware keyboards, matching the desktop apps: when Return sends,
                // Shift+Return and Ctrl+Return insert a newline; when it doesn't,
                // Ctrl+Return sends.
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown ||
                            (event.key != Key.Enter && event.key != Key.NumPadEnter)) {
                            return@onPreviewKeyEvent false
                        }
                        val newlineCombo = event.isCtrlPressed || event.isShiftPressed
                        val shouldSend =
                            if (ctx.enterToSend) !newlineCombo else event.isCtrlPressed
                        if (!shouldSend) return@onPreviewKeyEvent false
                        if (!sending && canSend) send()
                        true
                    },
            )

            TextButton(onClick = { openMention() }, enabled = !sending) {
                Text("Mention someone")
            }

            Text(
                text = "$remaining",
                style = MaterialTheme.typography.labelLarge,
                color = if (remaining < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "$remaining characters remaining"
                },
            )

            if (ctx.featureMedia) {
                Button(onClick = { picker.launch(arrayOf("image/*", "video/*")) }, enabled = !sending) {
                    Text("Add media")
                }
                attachments.forEachIndexed { i, m ->
                    Column {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                m.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { attachments.removeAt(i) }, enabled = !sending) {
                                Text("Remove")
                            }
                        }
                        OutlinedTextField(
                            value = m.alt,
                            onValueChange = { attachments[i] = m.copy(alt = it) },
                            label = { Text("Description (alt text)") },
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }

            if (ctx.featureVisibility) {
                ComposeCombo(
                    label = "Visibility",
                    options = (0..3).map { visibilityLabel(it) },
                    selected = visibility,
                    enabled = !sending,
                    onSelect = { visibility = it },
                )
            }

            if (showLanguage) {
                ComposeCombo(
                    label = "Language",
                    options = ctx.languages.map { it.name },
                    selected = languageIndex,
                    enabled = !sending,
                    onSelect = { languageIndex = it },
                )
            }

            if (showPoll) {
                CheckRow("Add a poll", pollOn, !sending) { pollOn = it }
                if (pollOn) {
                    pollChoices.forEachIndexed { i, choice ->
                        OutlinedTextField(
                            value = choice,
                            onValueChange = { pollChoices[i] = it },
                            label = { Text("Poll choice ${i + 1}") },
                            singleLine = true,
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CheckRow("Allow multiple choices", pollMultiple, !sending) { pollMultiple = it }
                    ComposeCombo(
                        label = "Duration",
                        options = POLL_DURATIONS.map { it.first },
                        selected = pollDurationIndex,
                        enabled = !sending,
                        onSelect = { pollDurationIndex = it },
                    )
                }
            }

            if (showSchedule) {
                CheckRow("Schedule for later", scheduleOn, !sending) { scheduleOn = it }
                if (scheduleOn) {
                    ScheduleRow(at = scheduleAt, enabled = !sending, onChange = { scheduleAt = it })
                }
            }

            if (ctx.participants.isNotEmpty()) {
                Text("Recipients", style = MaterialTheme.typography.labelLarge)
                ctx.participants.forEach { p ->
                    val isChecked = checked[p.acct] == true
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription =
                                    "@${p.acct}${if (isChecked) ", included" else ", not included"}"
                            },
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked[p.acct] = it },
                            enabled = !sending,
                        )
                        Text("@${p.acct}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (mentionOpen) {
        MentionDialog(
            viewModel = viewModel,
            seed = mentionSeed,
            onPick = { insertMention(it) },
            onDismiss = {
                mentionOpen = false
                viewModel.clearMentionSuggestions()
            },
        )
    }
}

private fun isHandleChar(c: Char): Boolean =
    c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' || c == '@'

/**
 * @-mention autocomplete: type part of a handle, tap a match to insert it. The
 * core does the searching (search shared across front ends); this is the touch
 * equivalent of the desktop's Alt+A picker.
 */
@Composable
private fun MentionDialog(
    viewModel: CoreViewModel,
    seed: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(seed) }
    val suggestions by viewModel.mentionSuggestions.collectAsState()
    val searchFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    var listFocused by remember { mutableStateOf(false) }

    val users: List<MentionSuggestion> = suggestions?.users ?: emptyList()

    // Debounce so each keystroke doesn't fire a request; the core echoes the query.
    LaunchedEffect(query) {
        delay(200)
        viewModel.autocompleteUsers(query)
    }
    // Land on the results (matches are coming for the seeded handle) so a screen
    // reader moves straight through them; with nothing seeded, start in the search
    // box instead since there's nothing yet to land on.
    LaunchedEffect(Unit) {
        if (seed.isEmpty()) runCatching { searchFocus.requestFocus() }
    }
    LaunchedEffect(users.isNotEmpty()) {
        if (seed.isNotEmpty() && !listFocused && users.isNotEmpty()) {
            listFocused = true
            runCatching { listFocus.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mention a user") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocus),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .focusRequester(listFocus)
                        .focusable(),
                ) {
                    items(users) { s ->
                        Text(
                            s.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(s.acct) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val POLL_DURATIONS = listOf(
    "5 minutes" to 300, "30 minutes" to 1800, "1 hour" to 3600, "6 hours" to 21600,
    "1 day" to 86400, "3 days" to 259200, "7 days" to 604800,
)

/** A labelled checkbox row (one focus stop, one TalkBack node). */
@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A labelled choice that opens a menu of [options]. */
@Composable
private fun ComposeCombo(
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(options.getOrNull(selected).orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 400.dp),
        ) {
            options.forEachIndexed { i, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}

/** The scheduled date and time, each changed with the system picker. */
@Composable
private fun ScheduleRow(at: Long, enabled: Boolean, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val dateText = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(cal.time)
    val timeText = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(cal.time)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            enabled = enabled,
            onClick = {
                android.app.DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        val c = java.util.Calendar.getInstance().apply { timeInMillis = at; set(y, m, d) }
                        onChange(c.timeInMillis)
                    },
                    cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH),
                ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
            },
            modifier = Modifier.semantics { contentDescription = "Scheduled date, $dateText" },
        ) { Text(dateText) }
        TextButton(
            enabled = enabled,
            onClick = {
                android.app.TimePickerDialog(
                    context,
                    { _, h, min ->
                        val c = java.util.Calendar.getInstance().apply {
                            timeInMillis = at
                            set(java.util.Calendar.HOUR_OF_DAY, h)
                            set(java.util.Calendar.MINUTE, min)
                            set(java.util.Calendar.SECOND, 0)
                        }
                        onChange(c.timeInMillis)
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            },
            modifier = Modifier.semantics { contentDescription = "Scheduled time, $timeText" },
        ) { Text(timeText) }
    }
}
