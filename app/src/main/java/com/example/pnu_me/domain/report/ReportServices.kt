package com.example.pnu_me.domain.report

enum class CapabilityStatus {
    READY,
    NEEDS_PERMISSION,
    DISABLED,
    UNAVAILABLE,
    DEMO_ONLY,
    BLOCKED,
}

enum class ReportChannel(val label: String) {
    DIALER("112 전화"),
    SMS("112 문자"),
}

data class ReportReadiness(
    val location: CapabilityStatus,
    val dialer: CapabilityStatus,
    val sms: CapabilityStatus,
    val voiceGuidance: CapabilityStatus,
    val voiceResponse: CapabilityStatus,
    val isDemo: Boolean,
) {
    val availableChannels: Set<ReportChannel>
        get() = buildSet {
            if (dialer == CapabilityStatus.READY) add(ReportChannel.DIALER)
            if (sms == CapabilityStatus.READY) add(ReportChannel.SMS)
        }

    val canUseSms: Boolean get() = sms == CapabilityStatus.READY
    val canReadDraftAloud: Boolean get() = voiceGuidance == CapabilityStatus.READY
    val canAcceptVoiceResponse: Boolean get() = voiceResponse == CapabilityStatus.READY
}

object ReportReadinessFactory {
    fun create(
        hasLocationPermission: Boolean,
        locationEnabled: Boolean,
        dialerAvailable: Boolean,
        smsAvailable: Boolean,
        voiceGuidanceAvailable: Boolean,
        isDemo: Boolean,
    ): ReportReadiness = ReportReadiness(
        location = when {
            !hasLocationPermission -> CapabilityStatus.NEEDS_PERMISSION
            !locationEnabled -> CapabilityStatus.DISABLED
            else -> CapabilityStatus.READY
        },
        dialer = if (dialerAvailable) CapabilityStatus.READY else CapabilityStatus.UNAVAILABLE,
        sms = if (smsAvailable) CapabilityStatus.READY else CapabilityStatus.UNAVAILABLE,
        voiceGuidance = if (voiceGuidanceAvailable) CapabilityStatus.READY else CapabilityStatus.UNAVAILABLE,
        // 마이크 기반 음성 응답은 Pleos/DO/오디오 정책 검증 전까지 활성화하지 않는다.
        voiceResponse = CapabilityStatus.BLOCKED,
        isDemo = isDemo,
    )
}

fun interface ReportReadinessProvider {
    fun current(): ReportReadiness
}

sealed interface ReportHandoffResult {
    val channel: ReportChannel
    val reportConfirmed: Boolean get() = false

    data class OpenedUnconfirmed(override val channel: ReportChannel) : ReportHandoffResult

    data class Unavailable(
        override val channel: ReportChannel,
        val reason: String,
    ) : ReportHandoffResult

    data class Failed(
        override val channel: ReportChannel,
        val reason: String,
    ) : ReportHandoffResult
}

fun interface ReportChannelLauncher {
    fun open(channel: ReportChannel, body: String): ReportHandoffResult
}

sealed interface VoiceGuideResult {
    data object Started : VoiceGuideResult
    data object NotReady : VoiceGuideResult
    data class Unavailable(val reason: String) : VoiceGuideResult
}

interface ReportVoiceGuide {
    val sourceLabel: String
    fun speak(text: String): VoiceGuideResult
    fun stop()
    fun release()
}
