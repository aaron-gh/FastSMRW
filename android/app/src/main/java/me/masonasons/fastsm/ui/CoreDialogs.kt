package me.masonasons.fastsm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Follow Hashtag: the post's hashtags to pick from, or type one.
 */
@Composable
fun HashtagPromptDialog(vm: CoreViewModel, tags: List<String>) {
    var typed by remember(tags) { mutableStateOf(tags.firstOrNull().orEmpty()) }
    fun follow(name: String) {
        val tag = name.trim().removePrefix("#")
        if (tag.isNotEmpty()) vm.followHashtag(tag)
        vm.dismissHashtagPrompt()
    }
    AlertDialog(
        onDismissRequest = vm::dismissHashtagPrompt,
        title = { Text("Follow hashtag") },
        text = {
            Column {
                tags.forEach { tag ->
                    TextButton(onClick = { follow(tag) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Follow #$tag", modifier = Modifier.fillMaxWidth())
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Hashtag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { follow(typed) }, enabled = typed.isNotBlank()) { Text("Follow") }
        },
        dismissButton = { TextButton(onClick = vm::dismissHashtagPrompt) { Text("Cancel") } },
    )
}
