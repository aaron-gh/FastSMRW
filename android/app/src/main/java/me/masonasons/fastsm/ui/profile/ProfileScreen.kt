package me.masonasons.fastsm.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.ui.CoreViewModel
import me.masonasons.fastsm.ui.ProfileUi
import me.masonasons.fastsm.ui.ReportDialog

/**
 * A user's profile, driven by the core's user_profile event. [profile.text] is
 * the composed, accessible profile string; the buttons dispatch core commands.
 * Follow state is flipped optimistically (the core confirms with a spoken
 * announcement).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: CoreViewModel,
    profile: ProfileUi,
    onViewPosts: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit,
) {
    var following by remember(profile.accountId) { mutableStateOf(profile.following) }
    var requested by remember(profile.accountId) { mutableStateOf(profile.requested) }
    var showReport by remember(profile.accountId) { mutableStateOf(false) }
    // Set while the Confirmation setting says to ask before following/unfollowing.
    var pendingFollow by remember(profile.accountId) { mutableStateOf(false) }
    var pendingUnfollow by remember(profile.accountId) { mutableStateOf(false) }
    var muting by remember(profile.accountId) { mutableStateOf(profile.muting) }
    var blocking by remember(profile.accountId) { mutableStateOf(profile.blocking) }
    var showingReblogs by remember(profile.accountId) { mutableStateOf(profile.showingReblogs) }
    // Block / unblock waiting on its confirmation (Settings, Confirmation).
    var pendingBlock by remember(profile.accountId) { mutableStateOf<Boolean?>(null) }

    fun setBlock(block: Boolean) {
        viewModel.setRelationship(profile.accountId, if (block) "block" else "unblock", profile.acct)
        blocking = block
    }

    BackHandler(enabled = true) { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@${profile.acct}") },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(profile.text, style = MaterialTheme.typography.bodyLarge)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.hasRelationship) {
                    val label = when {
                        following -> "Unfollow"
                        requested -> "Cancel request"
                        else -> "Follow"
                    }
                    Button(onClick = {
                        if (following || requested) {
                            if (viewModel.confirmsBefore("confirm_unfollow")) {
                                pendingUnfollow = true
                            } else {
                                viewModel.setRelationship(
                                    profile.accountId, "unfollow", profile.acct,
                                )
                                following = false
                                requested = false
                            }
                        } else if (viewModel.confirmsBefore("confirm_follow")) {
                            pendingFollow = true
                        } else {
                            viewModel.setRelationship(profile.accountId, "follow", profile.acct)
                            // A locked account turns the tap into a pending request; we
                            // can't tell here, so assume it followed and let the spoken
                            // confirmation correct the user if it was a request.
                            following = true
                        }
                    }) { Text(label) }
                }

                Button(onClick = onViewPosts) { Text("View posts") }

                if (profile.hasRelationship) {
                    Button(onClick = {
                        viewModel.setRelationship(
                            profile.accountId, if (muting) "unmute" else "mute", profile.acct,
                        )
                        muting = !muting
                    }) { Text(if (muting) "Unmute" else "Mute") }
                    Button(onClick = {
                        val block = !blocking
                        val key = if (block) "confirm_block" else "confirm_unblock"
                        if (viewModel.confirmsBefore(key, default = block)) pendingBlock = block
                        else setBlock(block)
                    }) { Text(if (blocking) "Unblock" else "Block") }
                    if (profile.canHideBoosts && following) {
                        Button(onClick = {
                            viewModel.setRelationship(
                                profile.accountId,
                                if (showingReblogs) "hide_boosts" else "show_boosts",
                                profile.acct,
                            )
                            showingReblogs = !showingReblogs
                        }) { Text(if (showingReblogs) "Hide boosts" else "Show boosts") }
                    }
                }

                Button(onClick = {
                    viewModel.openFollowersOf(profile.accountId, profile.acct)
                    onClose()
                }) { Text("Followers") }
                Button(onClick = {
                    viewModel.openFollowingOf(profile.accountId, profile.acct)
                    onClose()
                }) { Text("Following") }

                if (profile.canUseLists) {
                    Button(onClick = { viewModel.getUserLists(profile.accountId, profile.acct) }) {
                        Text("Add to list…")
                    }
                }

                // Direct messages need the direct visibility level (Mastodon only).
                if (profile.canMessage) {
                    Button(onClick = {
                        viewModel.messageUserPicked(profile.accountId, profile.acct)
                        onClose()
                    }) { Text("Send a message") }
                }

                TextButton(onClick = { showReport = true }) { Text("Report") }

                if (profile.url.isNotBlank()) {
                    TextButton(onClick = { onOpenUrl(profile.url) }) { Text("Open in browser") }
                }
            }
        }
    }

    if (pendingFollow || pendingUnfollow) {
        val un = if (pendingUnfollow) "Unfollow" else "Follow"
        AlertDialog(
            onDismissRequest = { pendingFollow = false; pendingUnfollow = false },
            title = { Text(un) },
            text = { Text("$un @${profile.acct}?") },
            confirmButton = {
                TextButton(onClick = {
                    if (pendingUnfollow) {
                        viewModel.setRelationship(profile.accountId, "unfollow", profile.acct)
                        following = false
                        requested = false
                    } else {
                        viewModel.setRelationship(profile.accountId, "follow", profile.acct)
                        following = true
                    }
                    pendingFollow = false
                    pendingUnfollow = false
                }) { Text(un) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFollow = false; pendingUnfollow = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingBlock?.let { block ->
        val verb = if (block) "Block" else "Unblock"
        AlertDialog(
            onDismissRequest = { pendingBlock = null },
            title = { Text(verb) },
            text = { Text("$verb @${profile.acct}?") },
            confirmButton = {
                TextButton(onClick = { setBlock(block); pendingBlock = null }) { Text(verb) }
            },
            dismissButton = { TextButton(onClick = { pendingBlock = null }) { Text("Cancel") } },
        )
    }

    if (showReport) {
        ReportDialog(
            remote = profile.acct.contains("@"),
            onSubmit = { category, comment, forward ->
                viewModel.reportUser(profile.accountId, profile.acct, category, comment, forward)
            },
            onDismiss = { showReport = false },
        )
    }
}
