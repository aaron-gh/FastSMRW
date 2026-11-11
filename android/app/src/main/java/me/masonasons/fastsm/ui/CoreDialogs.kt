package me.masonasons.fastsm.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Add to List: every list on the account, checked when the person is on it.
 * Checking adds them, unchecking removes them (each change is sent at once).
 */
@Composable
fun UserListsDialog(vm: CoreViewModel, lists: UserListsUi) {
    AlertDialog(
        onDismissRequest = vm::dismissUserLists,
        title = { Text("Lists for @${lists.acct}") },
        text = {
            if (lists.lists.isEmpty()) {
                Text("You have no lists. Create one under More, Manage lists.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(lists.lists, key = { it.id }) { l ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = l.member,
                                    role = Role.Checkbox,
                                    onValueChange = { vm.setUserList(l.id, lists.accountId, it) },
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(checked = l.member, onCheckedChange = null)
                            Text(l.title, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissUserLists) { Text("Done") } },
    )
}
