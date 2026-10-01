package pl.nju.opencode.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand
// ---------------------------------------------------------------------------
// Sampled from the current nju. app (com.orange.rn.nju) as shipped on Google
// Play: canvas #00B1CD, logo yellow #F0E503, ink navy #00273F. The older
// com.orange.njumobile APK this project started from used #00a4c4 / #e6e600;
// those are kept only where they are still the better choice.
// ---------------------------------------------------------------------------
val NjuTeal = Color(0xFF00B1CD)       // main brand cyan (official canvas)
val NjuYellow = Color(0xFFF0E503)     // logo / accent
val NjuYellowBorder = Color(0xFFE8E800)
val NjuWhite = Color(0xFFFFFFFF)
val NjuDarkNavy = Color(0xFF0B2A4A)   // active tab / dark surface
val NjuLightGray = Color(0xFFE9EEF1)  // inactive tab
val NjuTextDark = Color(0xFF0B2A4A)

/**
 * Hero canvas for the data card.
 *
 * Deliberately ~8% deeper than the official #00B1CD: white body text on the
 * store cyan only reaches 2.6:1, and this lands at 3.2:1 so the large figure
 * and its labels clear the WCAG large-text threshold while still reading as
 * the same nju cyan.
 */
val NjuCyan = Color(0xFF009DB6)

/** Official navy ink — pairs at 11:1 on [NjuYellow]. */
val NjuInk = Color(0xFF00273F)

// ---------------------------------------------------------------------------
// Material 3 schemes — hand-tuned for WCAG contrast rather than auto-derived.
// The brand teal at #00A9C4 only reaches ~2.5:1 against white, so it cannot be
// used as `primary` with white text. Containers and the darker teal carry the
// accessible weight; #00A9C4 stays as an accent where it has dark text.
// ---------------------------------------------------------------------------

// -- Light -------------------------------------------------------------------
val MdPrimaryLight = Color(0xFF00697B)
val MdOnPrimaryLight = Color(0xFFFFFFFF)
val MdPrimaryContainerLight = Color(0xFF9EEFFF)
val MdOnPrimaryContainerLight = Color(0xFF001F24)

val MdSecondaryLight = Color(0xFF4C635F)
val MdOnSecondaryLight = Color(0xFFFFFFFF)
val MdSecondaryContainerLight = Color(0xFFCEE9E3)
val MdOnSecondaryContainerLight = Color(0xFF08201C)

// nju yellow as tertiary accent — always paired with dark text
val MdTertiaryLight = Color(0xFF7A7A00)
val MdOnTertiaryLight = Color(0xFFFFFFFF)
val MdTertiaryContainerLight = Color(0xFFFFFE7A)
val MdOnTertiaryContainerLight = Color(0xFF252A00)

val MdBackgroundLight = Color(0xFFF4FBFC)
val MdOnBackgroundLight = Color(0xFF191C1D)
val MdSurfaceLight = Color(0xFFF4FBFC)
val MdOnSurfaceLight = Color(0xFF191C1D)
val MdSurfaceVariantLight = Color(0xFFDBE4E7)
val MdOnSurfaceVariantLight = Color(0xFF3F494B)
val MdOutlineLight = Color(0xFF6F797B)
val MdOutlineVariantLight = Color(0xFFBEC9CB)

val MdErrorLight = Color(0xFFBA1A1A)
val MdOnErrorLight = Color(0xFFFFFFFF)
val MdErrorContainerLight = Color(0xFFFFDAD6)
val MdOnErrorContainerLight = Color(0xFF410002)

// -- Dark --------------------------------------------------------------------
val MdPrimaryDark = Color(0xFF80D4E7)
val MdOnPrimaryDark = Color(0xFF00343D)
val MdPrimaryContainerDark = Color(0xFF004C5A)
val MdOnPrimaryContainerDark = Color(0xFF9EEFFF)

val MdSecondaryDark = Color(0xFFB4CCC7)
val MdOnSecondaryDark = Color(0xFF1E3531)
val MdSecondaryContainerDark = Color(0xFF344B47)
val MdOnSecondaryContainerDark = Color(0xFFCEE9E3)

val MdTertiaryDark = Color(0xFFD7DC47)
val MdOnTertiaryDark = Color(0xFF383A00)
val MdTertiaryContainerDark = Color(0xFF515200)
val MdOnTertiaryContainerDark = Color(0xFFFFFE7A)

val MdBackgroundDark = Color(0xFF101416)
val MdOnBackgroundDark = Color(0xFFE0E3E4)
val MdSurfaceDark = Color(0xFF101416)
val MdOnSurfaceDark = Color(0xFFE0E3E4)
val MdSurfaceVariantDark = Color(0xFF3F494B)
val MdOnSurfaceVariantDark = Color(0xFFBEC9CB)
val MdOutlineDark = Color(0xFF899395)
val MdOutlineVariantDark = Color(0xFF40494B)

val MdErrorDark = Color(0xFFFFB4AB)
val MdOnErrorDark = Color(0xFF690005)
val MdErrorContainerDark = Color(0xFF93000A)
val MdOnErrorContainerDark = Color(0xFFFFDAD6)
