package com.example.pnu_me.domain.stop

import com.example.pnu_me.domain.risk.RiskAssessment

interface StopAdvisor {
    val sourceLabel: String

    fun advise(assessment: RiskAssessment): StopAdvice

    fun requestGuidance(candidateId: String): StopGuidance

    fun cancelGuidance(): StopGuidance
}
