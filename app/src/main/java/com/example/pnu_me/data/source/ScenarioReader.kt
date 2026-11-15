package com.example.pnu_me.data.source

fun interface ScenarioReader {
    fun read(scenario: DrivingScenario): String
}
