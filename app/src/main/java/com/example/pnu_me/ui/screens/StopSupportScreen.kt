package com.example.pnu_me.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.stop.StopAdvice
import com.example.pnu_me.domain.stop.StopAdvisor
import com.example.pnu_me.domain.stop.StopCandidate
import com.example.pnu_me.domain.stop.StopGuidance
import com.example.pnu_me.ui.model.GuidanceUiStatus
import com.example.pnu_me.ui.model.stopGuidanceUiState

@Composable
internal fun StopSupportScreen(
    advisor: StopAdvisor,
    assessment: RiskAssessment,
    onBack: () -> Unit,
    onShowSummary: () -> Unit,
) {
    val advice = remember(advisor, assessment) { advisor.advise(assessment) }
    var selectedCandidateId by rememberSaveable { mutableStateOf<String?>(null) }
    var guidanceStatus by rememberSaveable { mutableStateOf("IDLE") }
    var guidanceMessage by rememberSaveable { mutableStateOf("") }
    var guidancePrepared by rememberSaveable { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf(false) }

    fun applyGuidanceResult(result: Result<StopGuidance>) {
        val uiState = stopGuidanceUiState(result)
        guidanceStatus = uiState.status.name
        guidanceMessage = uiState.message
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← 대시보드로 돌아가기")
            }
            Text(
                text = "안전 정차 지원 · MOCK",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "실제 위치 검색이나 내비게이션이 아닌 시연용 가상 장소입니다.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = assessment.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(assessment.guidance)
                    Text(
                        text = advisor.sourceLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            when (advice) {
                is StopAdvice.Available -> {
                    CandidateList(
                        candidates = advice.candidates,
                        selectedCandidateId = selectedCandidateId,
                        onSelect = {
                            selectedCandidateId = it.id
                            guidanceStatus = "IDLE"
                            guidanceMessage = ""
                            guidancePrepared = false
                        },
                    )

                    Button(
                        onClick = {
                            operationInProgress = true
                            val result = runCatching {
                                advisor.requestGuidance(
                                    candidateId = requireNotNull(selectedCandidateId),
                                )
                            }
                            guidancePrepared = result.getOrNull() is StopGuidance.Ready
                            applyGuidanceResult(result)
                            operationInProgress = false
                        },
                        enabled = selectedCandidateId != null &&
                            !guidancePrepared &&
                            !operationInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                    ) {
                        Text("선택한 가상 장소로 모의 안내 준비")
                    }

                    OutlinedButton(
                        onClick = {
                            operationInProgress = true
                            val result = runCatching { advisor.cancelGuidance() }
                            if (result.getOrNull() == StopGuidance.Cancelled) {
                                guidancePrepared = false
                            }
                            applyGuidanceResult(result)
                            operationInProgress = false
                        },
                        enabled = guidancePrepared && !operationInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("모의 안내 취소")
                    }
                }

                StopAdvice.NoCandidates -> StatusMessage(
                    title = "정차 후보 데이터 없음",
                    message = "현재 분석 상태에서는 제공할 모의 정차 후보가 없습니다.",
                    isError = false,
                )

                is StopAdvice.Failed -> StatusMessage(
                    title = "정차 지원 오류",
                    message = advice.message,
                    isError = true,
                )
            }

            when (guidanceStatus) {
                GuidanceUiStatus.READY.name -> {
                    StatusMessage(
                        title = "모의 안내 준비 완료",
                        message = guidanceMessage,
                        isError = false,
                    )
                    Button(
                        onClick = onShowSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Text("정차 후 분석 요약 보기")
                    }
                }

                GuidanceUiStatus.CANCELLED.name -> StatusMessage(
                    title = "안내 취소",
                    message = guidanceMessage,
                    isError = false,
                )

                GuidanceUiStatus.FAILED.name -> StatusMessage(
                    title = "안내 요청 실패",
                    message = guidanceMessage,
                    isError = true,
                )
            }
        }
    }
}

@Composable
private fun CandidateList(
    candidates: List<StopCandidate>,
    selectedCandidateId: String?,
    onSelect: (StopCandidate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "시연용 가상 정차 후보",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            candidates.forEach { candidate ->
                OutlinedButton(
                    onClick = { onSelect(candidate) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (candidate.id == selectedCandidateId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = candidate.label,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = candidate.description,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(
    title: String,
    message: String,
    isError: Boolean,
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(message)
            }
        }
    }
}
