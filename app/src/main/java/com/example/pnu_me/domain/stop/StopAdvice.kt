package com.example.pnu_me.domain.stop

data class StopCandidate(
    val id: String,
    val label: String,
    val description: String,
    val isDemo: Boolean,
)

sealed interface StopAdvice {
    data class Available(val candidates: List<StopCandidate>) : StopAdvice
    data object NoCandidates : StopAdvice
    data class Failed(val message: String) : StopAdvice
}

sealed interface StopGuidance {
    data class Ready(
        val candidate: StopCandidate,
        val message: String,
    ) : StopGuidance

    data object Cancelled : StopGuidance
    data class Failed(val message: String) : StopGuidance
}
