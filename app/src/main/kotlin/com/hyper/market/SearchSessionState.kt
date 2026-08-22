package com.hyper.market

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.hyper.market.model.MarketAppInfo

@Stable
internal class SearchSessionState(initialHistory: List<String>) {
    val keyword = mutableStateOf("")
    val results = mutableStateOf(emptyList<MarketAppInfo>())
    val history = mutableStateOf(initialHistory)
    val searchedKeyword = mutableStateOf("")
    val page = mutableStateOf(0)
    val hasMore = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val loading = mutableStateOf(false)
    val editing = mutableStateOf(false)
    val showHistory = mutableStateOf(initialHistory.isNotEmpty())
}
