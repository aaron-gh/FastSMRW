package me.masonasons.fastsm.ui.post

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.ui.CoreViewModel
import me.masonasons.fastsm.ui.PostInfoUi
import me.masonasons.fastsm.ui.ReportDialog

/**
 * Post Info, from the core's post_info event: the post's full text — including
 * the text behind a content warning — then its actions. Matches the iPhone and
 * desktop Post Info. The text is one item per line so TalkBack can step through it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostInfoScreen(viewModel: CoreViewModel, info: PostInfoUi, onClose: () -> Unit) {
    var showReport by remember(info.id) { mutableStateOf(false) }
    var showVote by remember(info.id) { mutableStateOf(false) }
    val id = info.id

    // Actions that move elsewhere close Post Info first, as on iPhone.
    fun thenClose(run: () -> Unit) { onClose(); run() }

    BackHandler(enabled = true) { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post Info", modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = "Post Info" },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    info.text.split('\n').filter { it.isNotBlank() }.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { thenClose { viewModel.composeReply(id) } }) { Text("Reply") }
                Button(onClick = { viewModel.toggleBoost(id) }) { Text("Boost") }
                Button(onClick = { viewModel.toggleFavorite(id) }) { Text("Favourite") }
                if (info.canQuote) {
                    Button(onClick = { thenClose { viewModel.composeQuote(id) } }) { Text("Quote") }
                }
                if (info.pollOptions != null) {
                    Button(onClick = { showVote = true }) { Text("Vote…") }
                }
                TextButton(onClick = { thenClose { viewModel.openThread(id) } }) {
                    Text("View conversation")
                }
                if (info.favoritesCount > 0) {
                    TextButton(onClick = { thenClose { viewModel.openFavoritedBy(id) } }) {
                        Text("See who favourited (${info.favoritesCount})")
                    }
                }
                if (info.boostsCount > 0) {
                    TextButton(onClick = { thenClose { viewModel.openRebloggedBy(id) } }) {
                        Text("See who boosted (${info.boostsCount})")
                    }
                }
                if (info.canMuteConversation) {
                    TextButton(onClick = { viewModel.toggleMuteConversation(id) }) {
                        Text(if (info.muted) "Unmute conversation" else "Mute conversation")
                    }
                }
                if (info.hasUrl) {
                    TextButton(onClick = { viewModel.openStatusInBrowser(id) }) { Text("Open in browser") }
                }
                if (!info.isMine) {
                    TextButton(onClick = { showReport = true }) { Text("Report") }
                }
            }
        }
    }

    if (showReport) {
        ReportDialog(
            remote = false,
            onSubmit = { category, comment, forward -> viewModel.reportPost(id, category, comment, forward) },
            onDismiss = { showReport = false },
        )
    }

    val options = info.pollOptions
    if (showVote && options != null) {
        VoteDialog(
            options = options,
            multiple = info.pollMultiple,
            onVote = { choices -> showVote = false; viewModel.votePoll(id, choices) },
            onDismiss = { showVote = false },
        )
    }
}

/** Pick one poll option (or several, for a multiple-choice poll) and vote. */
@Composable
private fun VoteDialog(
    options: List<String>,
    multiple: Boolean,
    onVote: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { mutableStateListOf<Int>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vote") },
        text = {
            Column(modifier = if (multiple) Modifier else Modifier.selectableGroup()) {
                options.forEachIndexed { i, label ->
                    val selected = i in chosen
                    val rowModifier = if (multiple) {
                        Modifier.toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { on -> if (on) chosen.add(i) else chosen.remove(i) },
                        )
                    } else {
                        Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { chosen.clear(); chosen.add(i) },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().then(rowModifier).padding(vertical = 4.dp),
                    ) {
                        if (multiple) Checkbox(checked = selected, onCheckedChange = null)
                        else RadioButton(selected = selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onVote(chosen.sorted()) }, enabled = chosen.isNotEmpty()) { Text("Vote") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
