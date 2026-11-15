package com.example.pnu_me.domain.report

enum class DrivingState {
    PARKED,
    DRIVING,
    UNKNOWN,
}

data class ReportFlowConfig(
    val consentTimeoutMillis: Long = 15_000L,
    val repromptCooldownMillis: Long = 300_000L,
    val draftRetentionMillis: Long = 600_000L,
)

sealed interface ReportFlowState {
    data object Idle : ReportFlowState

    data class AwaitingConsent(
        val suspicion: SuspicionResult,
        val askedAtMillis: Long,
        val restrictedUi: Boolean,
    ) : ReportFlowState

    data class Locating(
        val suspicion: SuspicionResult,
        val approvedAtMillis: Long,
        val deferUntilParked: Boolean = false,
    ) : ReportFlowState

    data class DeferredUntilParked(
        val draft: PoliceReportDraft,
        val discardAtMillis: Long,
    ) : ReportFlowState

    data class DraftReady(
        val draft: PoliceReportDraft,
        val discardAtMillis: Long = Long.MAX_VALUE,
    ) : ReportFlowState

    data class DraftDiscarded(val atMillis: Long) : ReportFlowState
    data class Declined(val atMillis: Long) : ReportFlowState
    data class Expired(val atMillis: Long) : ReportFlowState
}

class PoliceReportFlow(
    private val config: ReportFlowConfig = ReportFlowConfig(),
    private val isDemo: Boolean = true,
) {
    var state: ReportFlowState = ReportFlowState.Idle
        private set

    private var lastClosedAtMillis: Long? = null

    fun onSuspicion(
        result: SuspicionResult,
        drivingState: DrivingState,
        nowMillis: Long,
    ): ReportFlowState {
        if (!result.shouldAskOccupant) return state
        if (state is ReportFlowState.AwaitingConsent ||
            state is ReportFlowState.Locating ||
            state is ReportFlowState.DeferredUntilParked ||
            state is ReportFlowState.DraftReady
        ) {
            return state
        }
        val closedAt = lastClosedAtMillis
        if (closedAt != null && nowMillis - closedAt < config.repromptCooldownMillis) return state

        state = ReportFlowState.AwaitingConsent(
            suspicion = result,
            askedAtMillis = nowMillis,
            restrictedUi = drivingState != DrivingState.PARKED,
        )
        return state
    }

    fun onOccupantApproved(
        location: ReporterLocation?,
        nowMillis: Long,
        deferUntilParked: Boolean = false,
    ): ReportFlowState {
        val awaiting = state as? ReportFlowState.AwaitingConsent ?: return state
        if (isTimedOut(awaiting, nowMillis)) return expire(nowMillis)
        return createDraft(awaiting.suspicion, location, nowMillis, deferUntilParked)
    }

    fun onOccupantApproved(
        nowMillis: Long,
        deferUntilParked: Boolean = false,
    ): ReportFlowState {
        val awaiting = state as? ReportFlowState.AwaitingConsent ?: return state
        if (isTimedOut(awaiting, nowMillis)) return expire(nowMillis)
        state = ReportFlowState.Locating(awaiting.suspicion, nowMillis, deferUntilParked)
        return state
    }

    fun onLocationResult(result: LocationFetchResult, nowMillis: Long): ReportFlowState {
        val locating = state as? ReportFlowState.Locating ?: return state
        val location = (result as? LocationFetchResult.Success)?.location
        return createDraft(locating.suspicion, location, nowMillis, locating.deferUntilParked)
    }

    private fun createDraft(
        suspicion: SuspicionResult,
        location: ReporterLocation?,
        nowMillis: Long,
        deferUntilParked: Boolean,
    ): ReportFlowState {
        val draft = PoliceReportDraft(
            createdAtMillis = nowMillis,
            suspicion = suspicion,
            location = location,
            locationStatus = ReporterLocationValidator.validate(location, nowMillis),
            isDemo = isDemo,
        )
        val discardAt = safeAdd(nowMillis, config.draftRetentionMillis)
        state = if (deferUntilParked) {
            ReportFlowState.DeferredUntilParked(draft, discardAt)
        } else {
            ReportFlowState.DraftReady(draft, discardAt)
        }
        return state
    }

    fun onParked(nowMillis: Long): ReportFlowState {
        val deferred = state as? ReportFlowState.DeferredUntilParked ?: return state
        if (nowMillis >= deferred.discardAtMillis) return discardDraft(nowMillis)
        state = ReportFlowState.DraftReady(deferred.draft, deferred.discardAtMillis)
        return state
    }

    fun updateWitnessDetails(details: WitnessDetails): ReportFlowState {
        val ready = state as? ReportFlowState.DraftReady ?: return state
        state = ready.copy(draft = ready.draft.withWitnessDetails(details))
        return state
    }

    fun onOccupantDeclined(nowMillis: Long): ReportFlowState {
        if (state !is ReportFlowState.AwaitingConsent) return state
        lastClosedAtMillis = nowMillis
        state = ReportFlowState.Declined(nowMillis)
        return state
    }

    fun tick(nowMillis: Long): ReportFlowState = when (val current = state) {
        is ReportFlowState.AwaitingConsent ->
            if (isTimedOut(current, nowMillis)) expire(nowMillis) else state
        is ReportFlowState.DeferredUntilParked ->
            if (nowMillis >= current.discardAtMillis) discardDraft(nowMillis) else state
        is ReportFlowState.DraftReady ->
            if (nowMillis >= current.discardAtMillis) discardDraft(nowMillis) else state
        else -> state
    }

    fun finish(nowMillis: Long): ReportFlowState {
        if (state is ReportFlowState.Idle) return state
        if (state !is ReportFlowState.Declined &&
            state !is ReportFlowState.Expired &&
            state !is ReportFlowState.DraftDiscarded
        ) {
            lastClosedAtMillis = nowMillis
        }
        state = ReportFlowState.Idle
        return state
    }

    private fun isTimedOut(awaiting: ReportFlowState.AwaitingConsent, nowMillis: Long): Boolean =
        nowMillis - awaiting.askedAtMillis > config.consentTimeoutMillis

    private fun expire(nowMillis: Long): ReportFlowState {
        lastClosedAtMillis = nowMillis
        state = ReportFlowState.Expired(nowMillis)
        return state
    }

    private fun discardDraft(nowMillis: Long): ReportFlowState {
        lastClosedAtMillis = nowMillis
        state = ReportFlowState.DraftDiscarded(nowMillis)
        return state
    }

    private fun safeAdd(value: Long, delta: Long): Long =
        if (delta > 0L && value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta
}
