package com.example.pnu_me

import android.car.Car
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pnu_me.platform.vehicle.DrivingMonitorService
import com.example.pnu_me.ui.PnuMeApp
import com.example.pnu_me.ui.theme.Pnu_meTheme

class MainActivity : ComponentActivity() {

    // CAR_SPEED 권한 결과를 받은 뒤에만 감시 서비스를 시작한다.
    // (SAFE DRIVE에서는 권한 요청 전에 서비스를 시작해 첫 실행 시 속도 감시가 등록되지 않았다.)
    private val speedPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startDrivingMonitor()
            } else {
                Log.w(TAG, "차량 속도 권한 거부 - 감시 서비스를 시작하지 않음")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(compatibleSavedState(savedInstanceState))
        enableEdgeToEdge()
        setContent {
            Pnu_meTheme {
                PnuMeApp()
            }
        }

        startDrivingMonitorWhenPermitted()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(AppSavedStatePolicy.VERSION_KEY, AppSavedStatePolicy.CURRENT_VERSION)
    }

    private fun compatibleSavedState(savedInstanceState: Bundle?): Bundle? {
        if (savedInstanceState == null) return null
        return try {
            val savedVersion = if (savedInstanceState.containsKey(AppSavedStatePolicy.VERSION_KEY)) {
                savedInstanceState.getInt(AppSavedStatePolicy.VERSION_KEY)
            } else {
                null
            }
            savedInstanceState.takeIf { AppSavedStatePolicy.shouldRestore(savedVersion) }
        } catch (error: RuntimeException) {
            Log.w(TAG, "호환되지 않는 이전 저장 상태를 폐기함", error)
            null
        }
    }

    private fun startDrivingMonitorWhenPermitted() {
        // 일반 폰 에뮬레이터에는 android.car 런타임이 없으므로 MOCK 데모만 유지한다.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.i(TAG, "Automotive 기기가 아님 - 차량 감시 서비스 생략")
            return
        }

        if (checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED) {
            startDrivingMonitor()
        } else {
            speedPermissionLauncher.launch(Car.PERMISSION_SPEED)
        }
    }

    private fun startDrivingMonitor() {
        try {
            startService(Intent(this, DrivingMonitorService::class.java))
        } catch (e: IllegalStateException) {
            // 백그라운드 상태에서 시작이 거부된 경우
            Log.e(TAG, "감시 서비스 시작 실패", e)
        }
    }

    private companion object {
        const val TAG = "SAFE_DRIVE"
    }
}
