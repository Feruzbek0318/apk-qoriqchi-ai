package uz.apkqoriqchi.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.apkqoriqchi.core.data.HistoryRepository
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType

data class HistoryUi(
    val loading: Boolean = true,
    val filter: ScanType? = null,
    val total: Int = 0,
    val groups: List<Pair<DayGroup, List<ScanRecord>>> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: HistoryRepository) : ViewModel() {
    private val filter = MutableStateFlow<ScanType?>(null)

    val ui: StateFlow<HistoryUi> = combine(repo.observeAll(), filter) { all, f ->
        HistoryUi(
            loading = false,
            filter = f,
            total = all.size,
            groups = groupByDay(if (f == null) all else all.filter { it.type == f }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun setFilter(type: ScanType?) { filter.value = type }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun clearAll() = viewModelScope.launch { repo.clear() }

    fun observe(id: Long): Flow<ScanRecord?> = repo.observe(id)
}
