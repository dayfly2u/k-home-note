package com.khomenote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.data.local.dto.AssetWithCategory
import com.khomenote.ui.theme.BackgroundGray
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary

/**
 * 홈 헤더 "+" 버튼이 여는 바텀시트 — 대상(자산)을 고른 뒤 주기성작업/일회성작업 중 무엇을 추가할지 고른다.
 * 실제 입력 화면은 기존 것(주기성작업 폼, 대상 상세의 일회성작업 다이얼로그)을 그대로 쓰고 여기서는 진입만 돕는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAddSheet(
    assets: List<AssetWithCategory>,
    initialAssetId: Long?,
    onDismiss: () -> Unit,
    onAddAsset: () -> Unit,
    onAddRecurring: (Long) -> Unit,
    onAddOneTime: (Long) -> Unit,
) {
    // initialAssetId: "새 대상 만들기"로 방금 만든 대상이 있으면 그 대상이 이미 골라진 상태로 시작한다.
    var selectedId by remember { mutableStateOf(initialAssetId) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceWhite) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            val target = assets.firstOrNull { it.asset.id == selectedId }
            if (target == null) {
                Text("어디에 추가할까요?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (assets.isEmpty()) {
                    Text("등록된 대상이 없어요. 먼저 대상(보일러, 자동차, 카드 등)을 추가해 주세요.", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    ChoiceButton(title = "새 대상 만들기", description = null, filled = true, onClick = onAddAsset)
                } else {
                    Text("작업을 추가할 대상을 골라 주세요.", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(assets, key = { it.asset.id }) { asset ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selectedId = asset.asset.id }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BackgroundGray),
                                    contentAlignment = Alignment.Center,
                                ) { Text(asset.categoryEmoji, fontSize = 18.sp) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(asset.asset.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(asset.categoryName, fontSize = 12.sp, color = TextSecondary)
                                }
                                Text("›", fontSize = 20.sp, color = TextFaint)
                            }
                            HorizontalDivider(color = RowDivider)
                        }
                    }
                    // 대상이 많아 목록이 스크롤되어도 이 줄은 항상 보인다.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onAddAsset),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+ 새 대상 만들기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    }
                }
            } else {
                Text(
                    "< ${target.asset.name}",
                    modifier = Modifier.clickable { selectedId = null }.padding(vertical = 4.dp),
                    fontSize = 14.sp,
                    color = TealPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text("무엇을 추가할까요?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                ChoiceButton(
                    title = "주기성작업 추가",
                    description = "반복해서 챙기는 일 — 필터 교체, 카드 실적, 기념일",
                    filled = true,
                    onClick = { onAddRecurring(target.asset.id) },
                )
                Spacer(Modifier.height(10.dp))
                ChoiceButton(
                    title = "일회성작업 추가",
                    description = "한 번만 하는 일 — 점검 예약, 수리, 신청 마감",
                    filled = false,
                    onClick = { onAddOneTime(target.asset.id) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(title: String, description: String?, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val contentColor = if (filled) Color.White else TealPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (filled) TealPrimary else SurfaceWhite)
            .then(if (filled) Modifier else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = contentColor)
        if (description != null) {
            Text(description, fontSize = 12.sp, color = if (filled) Color(0xFFB9D6D4) else TextSecondary)
        }
    }
}
