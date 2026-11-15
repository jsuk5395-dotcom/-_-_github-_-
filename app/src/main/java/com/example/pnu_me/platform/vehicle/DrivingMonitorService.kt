package com.example.pnu_me.platform.vehicle

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager

/**
 * 실차 데이터 수집 서비스. SAFE DRIVE(MyApplication3)의 DrivingMonitorService에서 이식했다.
 *
 * - 속도: Android Car API `PERF_VEHICLE_SPEED` (권한 `android.car.permission.CAR_SPEED`, 런타임 요청)
 * - 조향: 공식 Pleos AVD와 typed API 검증 전이므로 수집하지 않는다.
 *
 * 수집한 값은 [VehicleTelemetryHub]로만 전달하고 위험 판단은 하지 않는다.
 * SAFE DRIVE의 급가감속·불규칙 조향·복합 위험 규칙은 단일 분석 엔진
 * ([com.example.pnu_me.domain.risk.RuleBasedRiskEngine])으로 옮겨 MOCK과 같은 방식으로 판단한다.
 *
 * 반드시 CAR_SPEED 권한이 허용된 뒤 [com.example.pnu_me.MainActivity]에서 시작한다.
 */
class DrivingMonitorService : Service() {
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var registered = false

    private val speedCallback =
        object : CarPropertyManager.CarPropertyEventCallback {

            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.propertyId !=
                    VehiclePropertyIds.PERF_VEHICLE_SPEED) return

                // 값을 제공하지 못하면 지어내지 않고 누락으로 기록 → 분석은 "측정 불가"
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
                    VehicleTelemetryHub.onSpeed(null)
                    return
                }

                val speed = value.value as? Float
                VehicleTelemetryHub.onSpeed(speed?.let { kotlin.math.abs(it).toDouble() })
            }

            override fun onErrorEvent(
                propertyId: Int,
                areaId: Int
            ) {
                Log.e(TAG, "차량 속도 데이터 수신 오류")
                VehicleTelemetryHub.onSpeed(null)
            }
        }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(TAG, "감시 서비스 시작")

        if (!registered) {
            startVehicleMonitoring()
        }

        return START_NOT_STICKY
    }

    private fun startVehicleMonitoring() {
        try {
            if (checkSelfPermission(Car.PERMISSION_SPEED) !=
                PackageManager.PERMISSION_GRANTED) {

                Log.e(TAG, "차량 속도 권한 없음")
                return
            }

            val newCar = Car.createCar(this)
            car = newCar

            val manager = newCar.getCarManager(
                Car.PROPERTY_SERVICE
            ) as? CarPropertyManager

            if (manager == null) {
                Log.e(TAG, "차량 서비스 연결 실패")
                return
            }

            propertyManager = manager

            registered = manager.registerCallback(
                speedCallback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                2f
            )

            VehicleTelemetryHub.onConnectionChanged(registered)

            Log.d(
                TAG,
                if (registered) "차량 속도 감시 시작 완료"
                else "차량 속도 감시 등록 실패"
            )

        } catch (e: Exception) {
            Log.e(TAG, "감시 시작 오류", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            if (registered) {
                propertyManager?.unregisterCallback(speedCallback)
                registered = false
            }

            car?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "서비스 종료 오류", e)
        }

        // 수신이 끝났으므로 화면에 남은 실차 표본을 지운다 (오래된 값으로 판단하지 않음)
        VehicleTelemetryHub.onConnectionChanged(false)

        Log.d(TAG, "감시 서비스 종료")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SAFE_DRIVE_SERVICE"
    }
}
