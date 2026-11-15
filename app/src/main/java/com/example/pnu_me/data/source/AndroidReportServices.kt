package com.example.pnu_me.data.source

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReportChannel
import com.example.pnu_me.domain.report.ReportChannelLauncher
import com.example.pnu_me.domain.report.ReportHandoffResult
import com.example.pnu_me.domain.report.ReportReadiness
import com.example.pnu_me.domain.report.ReportReadinessFactory
import com.example.pnu_me.domain.report.ReportReadinessProvider
import com.example.pnu_me.domain.report.ReportVoiceGuide
import com.example.pnu_me.domain.report.VoiceGuideResult
import java.util.Locale

class AndroidReportReadinessProvider(
    context: Context,
    private val isDemo: Boolean,
    private val voiceGuidanceAvailable: () -> Boolean,
) : ReportReadinessProvider {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override fun current(): ReportReadiness {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val locationEnabled = runCatching { locationManager?.isLocationEnabled == true }
            .getOrDefault(false)

        return ReportReadinessFactory.create(
            hasLocationPermission = hasLocationPermission,
            locationEnabled = locationEnabled,
            dialerAvailable = canResolve(dialIntent()),
            smsAvailable = canResolve(smsIntent(body = "")),
            voiceGuidanceAvailable = voiceGuidanceAvailable(),
            isDemo = isDemo,
        )
    }

    private fun canResolve(intent: Intent): Boolean =
        intent.resolveActivity(packageManager) != null
}

class AndroidReportChannelLauncher(
    context: Context,
) : ReportChannelLauncher {
    private val appContext = context.applicationContext

    override fun open(channel: ReportChannel, body: String): ReportHandoffResult {
        val intent = when (channel) {
            ReportChannel.DIALER -> dialIntent()
            ReportChannel.SMS -> smsIntent(body)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(appContext.packageManager) == null) {
            return ReportHandoffResult.Unavailable(channel, "처리할 외부 앱이 없습니다")
        }

        return try {
            appContext.startActivity(intent)
            ReportHandoffResult.OpenedUnconfirmed(channel)
        } catch (_: ActivityNotFoundException) {
            ReportHandoffResult.Unavailable(channel, "처리할 외부 앱이 없습니다")
        } catch (error: SecurityException) {
            ReportHandoffResult.Failed(channel, error.message ?: "외부 앱 실행이 차단되었습니다")
        } catch (error: RuntimeException) {
            ReportHandoffResult.Failed(channel, error.message ?: "외부 앱을 열 수 없습니다")
        }
    }
}

class AndroidReportVoiceGuide(
    context: Context,
) : ReportVoiceGuide, TextToSpeech.OnInitListener {
    override val sourceLabel: String = "Android TTS · 정차 전용"

    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready: Boolean = false
    private var unavailableReason: String? = null

    val isReady: Boolean get() = ready

    override fun onInit(status: Int) {
        val current = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            unavailableReason = "음성 엔진 초기화 실패"
            return
        }
        val languageResult = current.setLanguage(Locale.KOREAN)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            unavailableReason = "한국어 음성 데이터 미지원"
            return
        }
        ready = true
    }

    override fun speak(text: String): VoiceGuideResult {
        val current = engine ?: return VoiceGuideResult.Unavailable("음성 엔진이 종료되었습니다")
        unavailableReason?.let { return VoiceGuideResult.Unavailable(it) }
        if (!ready) return VoiceGuideResult.NotReady
        return if (current.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) ==
            TextToSpeech.SUCCESS
        ) {
            VoiceGuideResult.Started
        } else {
            VoiceGuideResult.Unavailable("음성 안내를 시작하지 못했습니다")
        }
    }

    override fun stop() {
        engine?.stop()
    }

    override fun release() {
        ready = false
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private companion object {
        const val UTTERANCE_ID = "pnu-me-report-summary"
    }
}

private fun dialIntent(): Intent =
    Intent(Intent.ACTION_DIAL, "tel:${PoliceReportDraft.POLICE_NUMBER}".toUri())

private fun smsIntent(body: String): Intent =
    Intent(Intent.ACTION_SENDTO, "smsto:${PoliceReportDraft.POLICE_NUMBER}".toUri())
        .putExtra("sms_body", body)
