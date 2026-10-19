package uz.apkqoriqchi.core.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import uz.apkqoriqchi.R
import uz.apkqoriqchi.ui.theme.RiskColors

@get:StringRes
val RiskLevel.labelRes: Int
    get() = when (this) {
        RiskLevel.NO_DETECTIONS -> R.string.risk_no_detections
        RiskLevel.LOW -> R.string.risk_low
        RiskLevel.MEDIUM -> R.string.risk_medium
        RiskLevel.HIGH -> R.string.risk_high
        RiskLevel.CRITICAL -> R.string.risk_critical
        RiskLevel.UNKNOWN -> R.string.risk_unknown
    }

val RiskLevel.color: Color
    get() = when (this) {
        RiskLevel.NO_DETECTIONS -> RiskColors.Clean
        RiskLevel.LOW -> RiskColors.Low
        RiskLevel.MEDIUM -> RiskColors.Medium
        RiskLevel.HIGH -> RiskColors.High
        RiskLevel.CRITICAL -> RiskColors.Critical
        RiskLevel.UNKNOWN -> RiskColors.Unknown
    }
