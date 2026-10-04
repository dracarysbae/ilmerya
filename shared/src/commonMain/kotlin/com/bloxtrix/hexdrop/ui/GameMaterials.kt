package com.bloxtrix.hexdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.bloxtrix.hexdrop.generated.resources.Res
import com.bloxtrix.hexdrop.generated.resources.mineral_atlas
import com.bloxtrix.hexdrop.generated.resources.mineral_atlas_high
import com.bloxtrix.hexdrop.generated.resources.walnut_material
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.imageResource

/** [minerals] holds the 2–32 stones; [highMinerals] the 64+ stones derived from them. */
data class GameMaterials(val walnut: ImageBitmap, val minerals: ImageBitmap, val highMinerals: ImageBitmap)

val LocalGameMaterials = staticCompositionLocalOf<GameMaterials?> { null }

@OptIn(ExperimentalResourceApi::class)
@Composable
fun loadGameMaterials(): GameMaterials {
    val walnut = imageResource(Res.drawable.walnut_material)
    val minerals = imageResource(Res.drawable.mineral_atlas)
    val high = imageResource(Res.drawable.mineral_atlas_high)
    return remember(walnut, minerals, high) { GameMaterials(walnut, minerals, high) }
}
