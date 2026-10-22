package moe.shizuku.manager.mcp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.lifecycle.Resource

class McpViewModel : ViewModel() {

    private val _agents = MutableLiveData<Resource<List<McpRepository.Agent>>>()
    val agents = _agents as LiveData<Resource<List<McpRepository.Agent>>>

    private val _audit = MutableLiveData<Resource<List<McpRepository.AuditEntry>>>()
    val audit = _audit as LiveData<Resource<List<McpRepository.AuditEntry>>>

    fun loadAgents() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _agents.postValue(Resource.success(McpRepository.listAgents()))
            } catch (e: CancellationException) {
            } catch (e: Throwable) {
                _agents.postValue(Resource.error(e, null))
            }
        }
    }

    fun loadAudit() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Newest first for display.
                _audit.postValue(Resource.success(McpRepository.recentAudit().asReversed()))
            } catch (e: CancellationException) {
            } catch (e: Throwable) {
                _audit.postValue(Resource.error(e, null))
            }
        }
    }
}
