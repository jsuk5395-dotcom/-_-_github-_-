package com.example.pnu_me.data.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.pnu_me.domain.report.LocationFetchResult
import com.example.pnu_me.domain.report.ReporterLocationMapper
import com.example.pnu_me.domain.report.ReporterLocationSource

/**
 * Android 플랫폼 [LocationManager]로 신고자(내 차량/기기) 현재 위치를 1회 조회한다.
 * - Pleos 전용 API가 아닌 일반 Android 위치 API다.
 * - 런타임 권한 요청은 호출하는 화면이 담당한다([REQUIRED_PERMISSIONS]).
 * - 위치를 서버로 보내거나 저장하지 않는다.
 */
class AndroidReporterLocationSource(
    context: Context,
    private val timeoutMillis: Long = 10_000L,
) : ReporterLocationSource {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cancellation: CancellationSignal? = null
    private var timeoutRunnable: Runnable? = null
    private var pending: ((LocationFetchResult) -> Unit)? = null

    override val sourceLabel: String = "기기 위치 (Android LocationManager)"

    @SuppressLint("MissingPermission") // selectProvider()에서 권한 확인 후에만 조회한다.
    override fun requestCurrentLocation(onResult: (LocationFetchResult) -> Unit) {
        cancel()
        val manager = locationManager ?: return onResult(LocationFetchResult.NoProvider)

        val hasFine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) return onResult(LocationFetchResult.PermissionDenied)
        if (!manager.isLocationEnabled) return onResult(LocationFetchResult.LocationDisabled)

        val provider = selectProvider(manager, hasFine)
            ?: return onResult(LocationFetchResult.NoProvider)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cancellation = signal
                pending = onResult
                val timeout = Runnable {
                    signal.cancel()
                    deliver(LocationFetchResult.Timeout)
                }
                timeoutRunnable = timeout
                mainHandler.postDelayed(timeout, timeoutMillis)
                manager.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                    deliver(location?.let(::map) ?: LocationFetchResult.Timeout)
                }
            } else {
                // API 28~29: 1회 조회 API가 없어 마지막 위치만 사용한다. 오래된 값은 Validator가 STALE로 거른다.
                onResult(manager.getLastKnownLocation(provider)?.let(::map) ?: LocationFetchResult.Timeout)
            }
        } catch (e: SecurityException) {
            deliver(LocationFetchResult.PermissionDenied, fallback = onResult)
        } catch (e: IllegalArgumentException) {
            deliver(LocationFetchResult.Failed(e.message ?: "provider 오류"), fallback = onResult)
        }
    }

    override fun cancel() {
        cancellation?.cancel()
        cancellation = null
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        pending = null
    }

    private fun deliver(
        result: LocationFetchResult,
        fallback: ((LocationFetchResult) -> Unit)? = null,
    ) {
        val callback = pending ?: fallback ?: return
        cancel()
        callback(result)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun selectProvider(manager: LocationManager, hasFine: Boolean): String? {
        val candidates = buildList {
            if (hasFine) add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.firstOrNull { name ->
            runCatching { manager.isProviderEnabled(name) }.getOrDefault(false)
        }
    }

    private fun map(location: Location): LocationFetchResult =
        ReporterLocationMapper.fromRaw(
            latitude = location.latitude,
            longitude = location.longitude,
            hasAccuracy = location.hasAccuracy(),
            accuracyMeters = location.accuracy,
            timeMillis = location.time,
            hasBearing = location.hasBearing(),
            bearingDegrees = location.bearing,
            isMockProvider = isMock(location),
            hasSpeed = location.hasSpeed(),
            speedMps = location.speed,
        )

    @Suppress("DEPRECATION")
    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider

    companion object {
        /** 화면에서 런타임 권한을 요청할 때 사용한다. 대략 위치만 허용돼도 동작한다. */
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
