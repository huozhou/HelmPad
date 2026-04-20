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

/**
 * Section header sat directly above the macro grid, calling out which profile
 * the grid belongs to.
 *
 * Deliberately not clickable: profile switching happens in Settings (dropdown)
 * or via onboarding, not by tapping the header. The glyph is resolved through
 * [ProfileIconRegistry] from the caller-supplied [profileId] so adding a new
 * bundled profile does not require touching this composable.
 *
 * Height is ~32dp (16dp icon + 8dp vertical padding top/bottom) so the layout
 * budget in `LayoutBudget.ProfileHeaderHeight` (Change 5) can plan around it.
 */
@Composable
fun ProfileSectionHeader(
    profileId: String,
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
            painter = painterResource(id = ProfileIconRegistry.resolve(profileId)),
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
