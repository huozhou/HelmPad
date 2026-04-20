package com.vibepad.keyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.macro.Profile

/**
 * Reusable profile picker: one Material 3 [ListItem] (leading profile icon,
 * headline = current profile name, trailing drop-down chevron) that expands a
 * [DropdownMenu] listing every bundled profile. The selected entry carries a
 * trailing [Icons.Filled.Check] — that's the "打对勾" the spec asks for.
 *
 * Used by the Settings "Active profile" section and by the onboarding
 * PROFILE step. Callers supply [profiles] (the loaded list, typically 2–4
 * entries), [activeProfileId] (source-of-truth from `SelectionsStore`), and
 * an [onSelect] callback invoked **only** when the user picks a different
 * profile — selecting the already-active entry just closes the menu without
 * touching the store.
 *
 * Accessibility: the collapsed row announces its role as "dropdown list" and
 * surfaces the current selection as its state description, so TalkBack users
 * hear "Active profile, Claude Code, dropdown list" rather than a flat row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDropdown(
    profiles: List<Profile>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val current = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
    val rowContentDescription = stringResource(
        R.string.settings_profile_dropdown_row_cd,
        current.name,
    )

    Box(modifier = modifier) {
        ListItem(
            modifier = Modifier
                .clickable { expanded = true }
                .semantics {
                    role = Role.DropdownList
                    stateDescription = current.name
                    contentDescription = rowContentDescription
                },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            leadingContent = {
                Icon(
                    painter = painterResource(ProfileIconRegistry.resolve(current.id)),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text(current.name) },
            supportingContent = {
                Text(stringResource(R.string.settings_profile_dropdown_supporting))
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            profiles.forEach { p ->
                val isSelected = p.id == activeProfileId
                DropdownMenuItem(
                    text = { Text(p.name) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(ProfileIconRegistry.resolve(p.id)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(
                                    R.string.settings_profile_dropdown_selected_cd,
                                ),
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(p.id)
                    },
                )
            }
        }
    }
}
