package com.khomenote.ui.navigation

/** golf-round-tracker와 동일하게 별도 Navigation 라이브러리 없이 수동 백스택으로 처리한다. */
sealed interface Screen {
    data object Home : Screen
    data object AssetList : Screen
    data object Settings : Screen
    data class AssetForm(val assetId: Long?) : Screen
    /** [openOneTimeForm]이 true면 들어가자마자 일회성작업 추가 다이얼로그를 연다(홈 "+" 시트에서 진입할 때). */
    data class AssetDetail(val assetId: Long, val openOneTimeForm: Boolean = false) : Screen
    /** [assetId]는 새 항목을 만들 때만 필요하다. 수정([itemId]가 있는 경우)은
     * ItemFormViewModel이 기존 항목에서 assetId를 읽어오므로 null로 넘겨도 된다. */
    data class ItemForm(val assetId: Long?, val itemId: Long?) : Screen
    data class ItemDetail(val itemId: Long) : Screen
}
