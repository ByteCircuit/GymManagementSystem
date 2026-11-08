package org.example.t3gymmanagementsystem.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.example.t3gymmanagementsystem.generated.resources.Res
import org.example.t3gymmanagementsystem.generated.resources.inter_bold
import org.example.t3gymmanagementsystem.generated.resources.inter_medium
import org.example.t3gymmanagementsystem.generated.resources.inter_regular
import org.example.t3gymmanagementsystem.generated.resources.inter_semibold
import org.example.t3gymmanagementsystem.generated.resources.jetbrainsmono_regular
import org.jetbrains.compose.resources.Font

@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
fun monoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrainsmono_regular, FontWeight.Normal),
)