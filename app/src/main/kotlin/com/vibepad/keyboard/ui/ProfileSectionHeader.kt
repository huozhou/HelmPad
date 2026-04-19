package com.vibepad.keyboard.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R

/**
 * Section header sat directly above the macro grid, calling out which profile
 * the grid belongs to.
 *
 * Deliberately not clickable in v1 — there's exactly one profile (Claude Code),
 * so tapping would have nothing to switch to. v2 turns this into an
 * `AssistChip` + `onClick` when additional bundled profiles land (see
 * `docs/roadmap-next-phase.md`).
 *
 * Height is ~32dp (16dp icon + 8dp vertical padding top/bottom) so the layout
 * budget in `LayoutBudget.ProfileHeaderHeight` (Change 5) can plan around it.
 */
@Composable
fun ProfileSectionHeader(
    profileName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_profile_claude_code),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = profileName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
