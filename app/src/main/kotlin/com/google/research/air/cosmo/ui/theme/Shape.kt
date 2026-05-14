package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

internal val CosmoShapes = Shapes(
    extraSmall = RoundedCornerShape(CosmoTokens.Shape.cornerExtraSmall),
    small = RoundedCornerShape(CosmoTokens.Shape.cornerSmall),
    medium = RoundedCornerShape(CosmoTokens.Shape.cornerMedium),
    large = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
    extraLarge = RoundedCornerShape(CosmoTokens.Shape.cornerExtraLarge)
)
