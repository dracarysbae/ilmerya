package com.bloxtrix.hexdrop.platform

import androidx.compose.runtime.Composable

/** System back gesture/button. iOS has no system back action, so its host ignores it. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
