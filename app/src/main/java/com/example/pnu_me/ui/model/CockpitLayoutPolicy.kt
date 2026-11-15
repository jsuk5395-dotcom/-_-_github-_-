package com.example.pnu_me.ui.model

enum class CockpitLayoutMode {
    SINGLE_PANEL,
    DUAL_PANEL,
}

/**
 * 가용 창 크기만으로 콕핏의 큰 레이아웃을 결정한다.
 * 차량 종류나 물리 디스플레이 해상도를 가정하지 않는다.
 */
fun cockpitLayoutMode(widthDp: Int, heightDp: Int): CockpitLayoutMode =
    if (widthDp >= 1_000 && heightDp >= 600) {
        CockpitLayoutMode.DUAL_PANEL
    } else {
        CockpitLayoutMode.SINGLE_PANEL
    }
