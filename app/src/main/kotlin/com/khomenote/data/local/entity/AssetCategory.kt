package com.khomenote.data.local.entity

enum class AssetCategory { HOME, CAR }

val AssetCategory.label: String
    get() = when (this) {
        AssetCategory.HOME -> "가전"
        AssetCategory.CAR -> "자동차"
    }
