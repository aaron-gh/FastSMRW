package me.masonasons.fastsm.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.core.FastSmCore
import me.masonasons.fastsm.background.BackgroundPrefs
import me.masonasons.fastsm.background.BackgroundService
import me.masonasons.fastsm.push.PushManager
import me.masonasons.fastsm.ui.CoreViewModel
import org.json.JSONObject
import kotlin.math.roundToInt

private const val CACHE_MAX = 20000
private const val FETCH_MIN = 1
private const val FETCH_MAX = 10

private val autoRefreshOptions = listOf(
    0 to "Off", 30 to "Every 30 seconds", 60 to "Every minute",
    120 to "Every 2 minutes", 300 to "Every 5 minutes",
)
private val cwOptions = listOf(
    "hide" to "Hide post text (show warning only)",
    "show" to "Show warning, then post text",
    "ignore" to "Ignore warning (show post text)",
)
private val emojiOptions = listOf(
    "none" to "Off", "unicode" to "Unicode emoji",
    "mastodon" to "Custom (:shortcode:)", "both" to "Both",
)
private val tabBarPositionOptions = listOf(
    "top" to "Top of the screen",
    "bottom" to "Bottom of the screen",
)

// Labels for the post-action editor, keyed by post_action_catalog() key.
private val postActionLabels = mapOf(
    "play_media" to "View media", "links" to "Open links", "reply" to "Reply",
    "boost" to "Boost", "favorite" to "Favorite", "bookmark" to "Bookmark",
    "quote" to "Quote", "thread" to "View conversation", "post_info" to "Post info",
    "copy" to "Copy", "user_profile" to "Author's profile",
    "user_timeline" to "Author's posts", "message" to "Send a direct message",
    "followers" to "Followers",
    "following" to "Following", "mute_conversation" to "Mute conversation",
    "favorited_by" to "See who favorited", "reblogged_by" to "See who boosted",
    "alias" to "Add or edit alias", "follow_hashtag" to "Follow hashtag",
    "open_hashtag_timeline" to "Open hashtag timeline",
    "speak_user" to "Speak user info", "speak_reply" to "Speak referenced reply",
    "jump_reply" to "Jump to referenced reply", "edit" to "Edit", "pin_post" to "Pin to profile",
    "report" to "Report post", "browser" to "Open in browser",
    "expand_links" to "Expand links (one action per link)", "delete" to "Delete",
)

private val statusFieldLabels = mapOf(
    "boostedBy" to "Boosted by", "author" to "Author name", "handle" to "Handle (@user)",
    "contentWarning" to "Content warning", "text" to "Post text", "quote" to "Quoted post",
    "media" to "Media / attachments", "poll" to "Poll", "time" to "Time",
    "stats" to "Reply / boost / favorite counts", "favorited" to "Favorited state",
    "boosted" to "Boosted state", "visibility" to "Visibility", "source" to "Posting app / source",
)
private val userFieldLabels = mapOf(
    "author" to "Display name", "handle" to "Handle (@user)", "bot" to "Bot indicator",
    "locked" to "Locked indicator", "bio" to "Bio", "followers" to "Followers count",
    "following" to "Following count", "posts" to "Posts count",
)
private val notificationFieldLabels = mapOf(
    "actor" to "Who (name)", "action" to "What they did", "handle" to "Handle (@user)",
    "text" to "Related post text", "time" to "Time",
)

private fun fieldLabelFor(list: String, field: String): String = when (list) {
    "user", "copy_user" -> userFieldLabels
    "notification", "copy_notification" -> notificationFieldLabels
    else -> statusFieldLabels // status, autoread, copy_status
}[field] ?: field

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: CoreViewModel, onClose: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val soundpacks by viewModel.soundpacks.collectAsStateWithLifecycle()
    val speechCatalog by viewModel.speechCatalog.collectAsStateWithLifecycle()
    val postActionCatalog by viewModel.postActionCatalog.collectAsStateWithLifecycle()
    // Labels come from the core, so fields and actions added there show up here.
    LaunchedEffect(Unit) { viewModel.loadCatalogs() }

    // Two-level navigation: root category list -> a panel -> (speech) a field editor.
    var panel by remember { mutableStateOf<String?>(null) }
    var speechList by remember { mutableStateOf<String?>(null) }

    val goBack: () -> Unit = {
        when {
            speechList != null -> speechList = null
            panel != null -> panel = null
            else -> onClose()
        }
    }
    BackHandler(enabled = true, onBack = goBack)

    val s = settings
    val title = when {
        speechList != null -> speechListTitle(speechList!!)
        panel == "general" -> "General"
        panel == "notifications" -> "Notifications"
        panel == "background" -> "Background"
        panel == "timelines" -> "Timelines"
        panel == "audio" -> "Audio"
        panel == "earcons" -> "Earcons"
        panel == "speech" -> "Speech"
        panel == "advanced" -> "Advanced"
        panel == "confirmation" -> "Confirmation"
        panel == "behavior" -> "Behavior"
        panel == "post_actions" -> "Post actions"
        panel == "updates" -> "Updates"
        else -> "Settings"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        // Keyed by the page so each one starts scrolled to the top, and its pane
        // title makes TalkBack announce the page that opened.
        key(title) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = title },
        ) {
            when {
                speechList != null -> SpeechFieldEditor(
                    s, speechList!!, viewModel,
                    speechCatalog[speechList!!].orEmpty().associate { it.key to it.label },
                )
                panel == "general" -> GeneralPanel(s, viewModel)
                panel == "notifications" -> NotificationsPanel(s, viewModel)
                panel == "background" -> BackgroundPanel()
                panel == "timelines" -> TimelinesPanel(s, viewModel)
                panel == "audio" -> AudioPanel(s, soundpacks, viewModel)
                panel == "earcons" -> EarconsPanel(s, viewModel)
                panel == "speech" -> SpeechPanel(s, viewModel) { speechList = it }
                panel == "advanced" -> AdvancedPanel(s, viewModel)
                panel == "confirmation" -> ConfirmationPanel(s, viewModel)
                panel == "behavior" -> BehaviorPanel(s, viewModel)
                panel == "post_actions" -> PostActionsPanel(
                    s, viewModel, postActionCatalog.associate { it.key to it.label },
                )
                panel == "updates" -> UpdatesPanel(s, viewModel)
                else -> RootList { panel = it }
            }
        }
        }
    }
}

private fun speechListTitle(list: String): String = when (list) {
    "status" -> "Speech Details — Posts"
    "user" -> "Speech Details — Users"
    "notification" -> "Speech Details — Notifications"
    "autoread" -> "Speech Details — Auto-read Posts"
    "copy_status" -> "Copy Details — Posts"
    "copy_user" -> "Copy Details — Users"
    "copy_notification" -> "Copy Details — Notifications"
    else -> list
}

private fun isCopyList(list: String) = list.startsWith("copy_")

@Composable
private fun RootList(onOpen: (String) -> Unit) {
    listOf(
        "general" to "General",
        "notifications" to "Notifications",
        "background" to "Background",
        "timelines" to "Timelines",
        "audio" to "Audio",
        "earcons" to "Earcons",
        "speech" to "Speech",
        "advanced" to "Advanced",
        "confirmation" to "Confirmation",
        "behavior" to "Behavior",
        "post_actions" to "Post actions",
        "updates" to "Updates",
    ).forEach { (key, label) ->
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpen(key) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
        HorizontalDivider()
    }
}

@Composable
private fun GeneralPanel(s: JSONObject, vm: CoreViewModel) {
    SwitchRow("Return key sends the post", s.optBoolean("enter_to_send")) {
        vm.updateSetting { put("enter_to_send", it) }
    }
    HelpText("With this on, Return sends the post and Shift+Return starts a new line. "
        + "With it off, Return starts a new line and Ctrl+Return sends.")
}

/**
 * Push notifications. The switch is a device-side preference (PushManager), not
 * a core setting, so it doesn't come through the settings JSON like the others.
 */
@Composable
private fun NotificationsPanel(s: JSONObject, vm: CoreViewModel) {
    val enabled by vm.pushEnabled.collectAsStateWithLifecycle()
	val types by vm.pushAlertTypes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The stored preference is the truth; pick it up when the panel opens.
    LaunchedEffect(Unit) { vm.syncPushEnabled() }

    if (!vm.pushAvailable) {
        HelpText(
            "Push notifications aren't available in this build of FastSMRW. " +
                "Install a release build from GitHub to use them."
        )
        return
    }

    // Android 13 and later must be asked before an app can show a notification,
    // so the permission prompt is part of turning the switch on.
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.setPushEnabled(true) }

    SwitchRow("Push notifications", enabled) { on ->
        val permission = PushManager.notificationPermission()
        if (on && permission != null && !PushManager.hasNotificationPermission(context)) {
            askPermission.launch(permission)
        } else {
            vm.setPushEnabled(on)
        }
    }
    HelpText(
        "Get notified of mentions, boosts, favorites, follows and more while FastSMRW " +
            "is closed. These choices apply to all Mastodon accounts on this device."
    )
	val alerts = s.optJSONObject("push_alerts")
	types.forEach { (key, label) ->
		SwitchRow(label, alerts?.optBoolean(key, true) ?: true) { on ->
			vm.setPushAlert(key, on)
		}
	}
}

/**
 * Background (Android only): keep running with a notification after the window
 * closes. A device-side preference (BackgroundPrefs), not a core setting.
 */
@Composable
private fun BackgroundPanel() {
    val context = LocalContext.current
    var run by remember { mutableStateOf(BackgroundPrefs.runInBackground(context)) }

    fun setRun(on: Boolean) {
        run = on
        BackgroundPrefs.setRunInBackground(context, on)
        BackgroundService.sync(context)
    }
    // The running notification needs permission on Android 13 and later; the
    // service runs without it, but the notification (and its Stop) stays hidden.
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { setRun(true) }

    SwitchRow("Run in the background", run) { on ->
        val permission = PushManager.notificationPermission()
        if (on && permission != null && !PushManager.hasNotificationPermission(context)) {
            askPermission.launch(permission)
        } else {
            setRun(on)
        }
    }
    HelpText(
        "Keeps FastSMRW running after you leave it, with a notification, so timelines " +
            "stay live and sounds play. Stop it from the notification or here."
    )
}

@Composable
private fun TimelinesPanel(s: JSONObject, vm: CoreViewModel) {
    NumberField(
        "Maximum posts to cache per timeline", s.optInt("cache_limit"), 0, CACHE_MAX,
        "How many posts to keep saved per timeline for instant startup (0-20000, 0 turns " +
            "caching off). This is storage, not how many load at once — see Advanced.",
    ) { vm.updateSetting { put("cache_limit", it) } }
    ComboRow("Auto-refresh", autoRefreshOptions, s.optInt("auto_refresh_seconds")) {
        vm.updateSetting { put("auto_refresh_seconds", it) }
    }
    HelpText("Check timelines for new posts on this interval; new posts play that timeline's sound.")
    SwitchRow("Stream in real time (Mastodon)", s.optBoolean("streaming_enabled")) {
        vm.updateSetting { put("streaming_enabled", it) }
    }
    ComboRow("Tab bar position", tabBarPositionOptions,
        s.optString("tab_bar_position").ifEmpty { "top" }) {
        vm.updateSetting { put("tab_bar_position", it) }
    }
    HelpText("Show the timeline tabs at the top or the bottom of the screen.")
    SwitchRow("Show mentions in the Notifications timeline", s.optBoolean("show_mentions_in_notifications")) {
        vm.updateSetting { put("show_mentions_in_notifications", it) }
    }
    SwitchRow("Reverse timelines (newest at the bottom)", s.optBoolean("reverse_timelines")) {
        vm.updateSetting { put("reverse_timelines", it) }
    }
    SwitchRow("Automatically load older posts when you reach the end", s.optBoolean("auto_load_older")) {
        vm.updateSetting { put("auto_load_older", it) }
    }
    SwitchRow("Sync home position with the server (Mastodon)", s.optBoolean("sync_home_position")) {
        vm.updateSetting { put("sync_home_position", it) }
    }
}

@Composable
private fun AudioPanel(s: JSONObject, soundpacks: List<String>, vm: CoreViewModel) {
    SwitchRow("Play sounds", s.optBoolean("sounds_enabled")) {
        vm.updateSetting { put("sounds_enabled", it) }
    }
    if (soundpacks.isNotEmpty()) {
        ComboRow("Soundpack", soundpacks.map { it to it }, s.optString("soundpack")) {
            vm.updateSetting { put("soundpack", it) }
        }
    }
    HelpText("A Default pack is built in. Add your own pack to the soundpacks folder, then pick it here.")
    SliderRow("Volume", s.optInt("sound_volume", 100)) {
        vm.updateSetting { put("sound_volume", it) }
    }
    SwitchRow("Play a sound at the top or bottom of a timeline (in the window)", s.optBoolean("boundary_sound")) {
        vm.updateSetting { put("boundary_sound", it) }
    }
}

@Composable
private fun EarconsPanel(s: JSONObject, vm: CoreViewModel) {
    HelpText("A short sound plays as you move onto a post that has each of these. Turn off any you don't want.")
    SwitchRow("Image (post has an image)", s.optBoolean("earcon_image", true)) {
        vm.updateSetting { put("earcon_image", it) }
    }
    SwitchRow("Media (post has video, audio, or a GIF)", s.optBoolean("earcon_media", true)) {
        vm.updateSetting { put("earcon_media", it) }
    }
    SwitchRow("Mention (post mentions you)", s.optBoolean("earcon_mention", true)) {
        vm.updateSetting { put("earcon_mention", it) }
    }
    SwitchRow("Pinned (post is pinned to a profile)", s.optBoolean("earcon_pinned", true)) {
        vm.updateSetting { put("earcon_pinned", it) }
    }
    SwitchRow("Poll (post has a poll)", s.optBoolean("earcon_poll", true)) {
        vm.updateSetting { put("earcon_poll", it) }
    }
}

@Composable
private fun SpeechPanel(s: JSONObject, vm: CoreViewModel, onConfigure: (String) -> Unit) {
    HelpText("Details spoken for each kind of row, and their order:")
    listOf("status", "user", "notification", "autoread").forEach { list ->
        ActionRow("${speechListTitle(list)}…") { onConfigure(list) }
    }
    HelpText("Details copied to the clipboard by Copy, and their order:")
    listOf("copy_status", "copy_user", "copy_notification").forEach { list ->
        ActionRow("${speechListTitle(list)}…") { onConfigure(list) }
    }
    ComboRow("Content warnings", cwOptions, s.optString("cw_mode")) {
        vm.updateSetting { put("cw_mode", it) }
    }
    ComboRow("Remove emoji from posts", emojiOptions, s.optString("post_emoji_removal")) {
        vm.updateSetting { put("post_emoji_removal", it) }
    }
    ComboRow("Remove emoji from names", emojiOptions, s.optString("name_emoji_removal")) {
        vm.updateSetting { put("name_emoji_removal", it) }
    }
    NumberField("Max usernames in a post (0 = all)", s.optInt("max_usernames_in_post"), 0, 999, null) {
        vm.updateSetting { put("max_usernames_in_post", it) }
    }
    SwitchRow("Use absolute time (clock time), not relative", s.optBoolean("absolute_time")) {
        vm.updateSetting { put("absolute_time", it) }
    }
    val separator = s.optJSONObject("speech")?.optString("separator") ?: ", "
    TextRow("Separator spoken between items", separator) { v ->
        vm.updateSetting {
            optJSONObject("speech")?.put("separator", v)
        }
    }
}

@Composable
private fun AdvancedPanel(s: JSONObject, vm: CoreViewModel) {
    NumberField(
        "API calls per timeline load (1-10)", s.optInt("fetch_pages", 3), FETCH_MIN, FETCH_MAX,
        "Posts loaded per refresh is about 40 × this number. Raise it to load more at once " +
            "(slower). Applies to refresh and scrollback.",
    ) { vm.updateSetting { put("fetch_pages", it) } }
}

@Composable
private fun BehaviorPanel(s: JSONObject, vm: CoreViewModel) {
    SwitchRow("Put extra reply mentions at the end", s.optBoolean("reply_mentions_at_end")) {
        vm.updateSetting { put("reply_mentions_at_end", it) }
    }
    HelpText("In a reply, mention the person you're replying to up front and move the other mentioned users to the end of the post.")
}

// How often an automatic check comes round while FastSMRW runs (hours; 0: only at start)
private val updateIntervalOptions = listOf(
    0 to "Only when FastSMRW starts",
    1 to "Every hour",
    6 to "Every 6 hours",
    12 to "Every 12 hours",
    24 to "Every day",
    168 to "Every week",
)

@Composable
private fun UpdatesPanel(s: JSONObject, vm: CoreViewModel) {
    SwitchRow("Check for updates automatically", s.optBoolean("check_updates_on_startup", true)) {
        vm.updateSetting { put("check_updates_on_startup", it) }
    }
    ComboRow("Check every", updateIntervalOptions, s.optInt("update_check_hours", 0)) {
        vm.updateSetting { put("update_check_hours", it) }
    }
    HorizontalDivider()
    ActionRow("Check for updates now") { vm.checkForUpdate() }
    HelpText("You're running FastSMRW ${FastSmCore.version}. Updates download from GitHub; tap the downloaded APK to install.")
}

@Composable
private fun ConfirmationPanel(s: JSONObject, vm: CoreViewModel) {
    HelpText("Show a confirmation before:")
    listOf(
        "confirm_boost" to "Boosting",
        "confirm_unboost" to "Unboosting",
        "confirm_favorite" to "Favoriting",
        "confirm_unfavorite" to "Unfavoriting",
        "confirm_clear_timeline" to "Clearing a timeline",
        "confirm_follow" to "Following a user",
        "confirm_unfollow" to "Unfollowing a user",
        "confirm_block" to "Blocking a user",
        "confirm_unblock" to "Unblocking a user",
        "confirm_delete_post" to "Deleting a post",
        "confirm_discard_post" to "Discarding a post you are writing",
    ).forEach { (key, label) ->
        SwitchRow(label, s.optBoolean(key)) { vm.updateSetting { put(key, it) } }
    }
}

@Composable
private fun PostActionsPanel(s: JSONObject, vm: CoreViewModel, catalogLabels: Map<String, String>) {
    val arr = s.optJSONArray("post_actions") ?: return
    val n = arr.length()
    HelpText("The actions offered on a post (TalkBack actions and the long-press menu). Double-tap or Space shows or hides an action. Move it with its TalkBack actions, the arrow buttons, or Ctrl+Up and Ctrl+Down.")
    for (i in 0 until n) {
        val o = arr.getJSONObject(i)
        val action = o.optString("action")
        val enabled = o.optBoolean("enabled", true)
        val label = postActionLabels[action] ?: catalogLabels[action] ?: action
        // key(action) keeps this row's node identity stable across reordering so
        // TalkBack focus follows the moved item (matches the speech editor).
        key(action) {
            val actions = buildList {
                add(CustomAccessibilityAction(if (enabled) "Hide this action" else "Show this action") {
                    vm.togglePostAction(i, !enabled); true
                })
                if (i > 0) add(CustomAccessibilityAction("Move up") { vm.movePostAction(i, -1); true })
                if (i < n - 1) add(CustomAccessibilityAction("Move down") { vm.movePostAction(i, +1); true })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .reorderRowKeys(
                        onToggle = { vm.togglePostAction(i, !enabled) },
                        onMove = { delta ->
                            if (i + delta in 0 until n) vm.movePostAction(i, delta)
                        },
                    )
                    .clearAndSetSemantics {
                        contentDescription =
                            "$label, ${if (enabled) "shown" else "hidden"}, position ${i + 1} of $n"
                        customActions = actions
                        onClick { vm.togglePostAction(i, !enabled); true }
                    }
                    .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { vm.togglePostAction(i, it) },
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(
                    onClick = { vm.movePostAction(i, -1) },
                    enabled = i > 0,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(
                    onClick = { vm.movePostAction(i, +1) },
                    enabled = i < n - 1,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SpeechFieldEditor(s: JSONObject, list: String, vm: CoreViewModel, catalogLabels: Map<String, String>) {
    fun fieldLabel(list: String, field: String) = catalogLabels[field] ?: fieldLabelFor(list, field)
    val arr = s.optJSONObject("speech")?.optJSONArray(list) ?: return
    val n = arr.length()
    // A field currently being edited for its before/after wrap text (dialog open).
    var editing by remember { mutableStateOf<String?>(null) }

    val copy = isCopyList(list)
    val onWord = if (copy) "copied" else "spoken"
    val offWord = if (copy) "not copied" else "not spoken"
    Text(
        speechListTitle(list),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
    HelpText(
        (if (copy) "Double-tap or Space turns a detail on or off in the copied text. "
        else "Double-tap or Space turns a detail on or off in the spoken text. ") +
            "Move it with its TalkBack actions, the arrow buttons, or Ctrl+Up and Ctrl+Down. " +
            "Extra text: the TalkBack action, the edit button, or Ctrl+E."
    )
    for (i in 0 until n) {
        val o = arr.getJSONObject(i)
        val field = o.optString("field")
        val enabled = o.optBoolean("enabled")

        // key(field) keeps this row's semantics node identity stable across
        // reordering, so TalkBack focus follows the moved item to its new spot
        // instead of staying on whatever shifts into the old position.
        key(field) {
            // One TalkBack node: double-tap toggles spoken/muted; Move up/down and
            // Edit extra text are custom actions. The visible switch and arrows stay
            // for touch but are hidden from TalkBack by clearAndSetSemantics.
            val actions = buildList {
                add(CustomAccessibilityAction(
                    when {
                        copy && enabled -> "Do not copy this"
                        copy -> "Copy this"
                        enabled -> "Do not speak this"
                        else -> "Speak this"
                    }
                ) {
                    vm.toggleSpeechField(list, field, !enabled); true
                })
                if (i > 0) add(CustomAccessibilityAction("Move up") { vm.moveSpeechField(list, i, -1); true })
                if (i < n - 1) add(CustomAccessibilityAction("Move down") { vm.moveSpeechField(list, i, +1); true })
                add(CustomAccessibilityAction("Edit extra text") { editing = field; true })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .reorderRowKeys(
                        onToggle = { vm.toggleSpeechField(list, field, !enabled) },
                        onMove = { delta ->
                            if (i + delta in 0 until n) vm.moveSpeechField(list, i, delta)
                        },
                        onEdit = { editing = field },
                    )
                    .clearAndSetSemantics {
                        contentDescription =
                            "${fieldLabel(list, field)}, ${if (enabled) onWord else offWord}, " +
                                "position ${i + 1} of $n"
                        customActions = actions
                        onClick { vm.toggleSpeechField(list, field, !enabled); true }
                    }
                    .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { vm.toggleSpeechField(list, field, it) },
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                Text(
                    fieldLabel(list, field),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(
                    onClick = { vm.moveSpeechField(list, i, -1) },
                    enabled = i > 0,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(
                    onClick = { vm.moveSpeechField(list, i, +1) },
                    enabled = i < n - 1,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                IconButton(
                    onClick = { editing = field },
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
            }
            HorizontalDivider()
        }
    }

    editing?.let { field ->
        val o = (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .firstOrNull { it.optString("field") == field }
        WrapDialog(
            fieldLabel = fieldLabel(list, field),
            before = o?.optString("before") ?: "",
            after = o?.optString("after") ?: "",
            noSeparatorAfter = o?.optBoolean("no_separator_after") ?: false,
            onSave = { b, a, noSep ->
                vm.setSpeechWrap(list, field, b, a, noSep); editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun WrapDialog(
    fieldLabel: String,
    before: String,
    after: String,
    noSeparatorAfter: Boolean,
    onSave: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var b by remember { mutableStateOf(before) }
    var a by remember { mutableStateOf(after) }
    var noSep by remember { mutableStateOf(noSeparatorAfter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extra text: $fieldLabel") },
        text = {
            Column {
                OutlinedTextField(
                    value = b,
                    onValueChange = { b = it },
                    label = { Text("Speak before") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = { Text("Speak after") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = noSep,
                            role = Role.Switch,
                            onValueChange = { noSep = it },
                        )
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "No separator after this field",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = noSep, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(b, a, noSep) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- Shared rows ----------------------------------------------------------

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun <T> ComboRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, optLabel) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = { onSelect(value); open = false },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    help: String?,
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (!it.isFocused) {
                        val v = (text.toIntOrNull() ?: value).coerceIn(min, max)
                        if (v != value) onCommit(v)
                        text = v.toString()
                    }
                },
        )
        help?.let { HelpText(it) }
    }
}

@Composable
private fun TextRow(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onFocusChanged { if (!it.isFocused && text != value) onCommit(text) },
    )
}

@Composable
private fun SliderRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var pos by remember(value) { mutableFloatStateOf(value.toFloat()) }
    // Saved once the value has stopped changing: arrow keys and TalkBack adjust it
    // a step at a time, and not every input path reports a finished drag.
    LaunchedEffect(pos) {
        delay(400)
        if (pos.roundToInt() != value) onChange(pos.roundToInt())
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$label: ${pos.roundToInt()}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = pos,
            onValueChange = { pos = it },
            valueRange = 0f..100f,
            steps = 19,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * Keyboard handling for a reorderable settings row: the row is one focus stop;
 * Space or Enter toggles it, Ctrl+Up / Ctrl+Down move it, and Ctrl+E (when
 * [onEdit] is given) opens its extra-text editor. The inner switch and arrow
 * buttons stay for touch but are not keyboard focus stops.
 */
private fun Modifier.reorderRowKeys(
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onEdit: (() -> Unit)? = null,
): Modifier = this
    .onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
        when {
            e.isCtrlPressed && e.key == Key.DirectionUp -> { onMove(-1); true }
            e.isCtrlPressed && e.key == Key.DirectionDown -> { onMove(+1); true }
            e.isCtrlPressed && e.key == Key.E && onEdit != null -> { onEdit(); true }
            !e.isCtrlPressed && (e.key == Key.Spacebar || e.key == Key.Enter ||
                e.key == Key.NumPadEnter || e.key == Key.DirectionCenter) -> { onToggle(); true }
            else -> false
        }
    }
    .focusable()
