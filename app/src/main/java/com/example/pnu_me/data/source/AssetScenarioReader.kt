package com.example.pnu_me.data.source

import android.content.Context

/**
 * Android assets 접근만 담당하며 분석 엔진에는 Android 타입을 노출하지 않는다.
 */
class AssetScenarioReader(context: Context) : ScenarioReader {
    private val assets = context.applicationContext.assets

    override fun read(scenario: DrivingScenario): String =
        assets.open("scenarios/" + scenario.assetFileName)
            .bufferedReader()
            .use { it.readText() }
}
