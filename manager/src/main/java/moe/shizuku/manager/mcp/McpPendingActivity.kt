package moe.shizuku.manager.mcp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.McpAgentsActivityBinding
import moe.shizuku.manager.databinding.McpPendingItemBinding
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

/**
 * Shows pending agent confirmations and lets the user approve or reject each.
 *
 * A parked agent call blocks on the server until it is resolved here (or times
 * out), so this screen polls while it is visible and refreshes right after a
 * decision.
 */
class McpPendingActivity : AppBarActivity() {

    private lateinit var binding: McpAgentsActivityBinding
    private val adapter = PendingAdapter { id, approved -> resolve(id, approved) }
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, POLL_MS)
        }
    }

    companion object {
        private const val POLL_MS = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Shizuku.pingBinder()) {
            finish()
            return
        }
        binding = McpAgentsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.mcp_pending_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.empty.setText(R.string.mcp_pending_empty)

        binding.list.adapter = adapter
        binding.list.fixEdgeEffect()
        binding.list.addEdgeSpacing(top = 8f, bottom = 8f, unit = android.util.TypedValue.COMPLEX_UNIT_DIP)
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(poll)
    }

    private fun refresh() {
        lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) { McpRepository.listPending() }
            } catch (e: Throwable) {
                return@launch
            }
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) ViewGroup.VISIBLE else ViewGroup.GONE
        }
    }

    private fun resolve(id: String, approved: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { McpRepository.resolveConfirmation(id, approved) }
            } catch (e: Throwable) {
                Toast.makeText(this@McpPendingActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
            }
            refresh()
        }
    }

    private class PendingAdapter(
        private val onDecision: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<PendingAdapter.VH>() {

        private val items = mutableListOf<McpRepository.PendingRequest>()

        fun submit(data: List<McpRepository.PendingRequest>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = McpPendingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onDecision)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount() = items.size

        class VH(
            private val binding: McpPendingItemBinding,
            private val onDecision: (String, Boolean) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(r: McpRepository.PendingRequest) {
                val ctx = binding.root.context
                binding.title.text = r.tool
                val time = DateUtils.getRelativeTimeSpanString(r.createdAt)
                binding.summary.text = ctx.getString(R.string.mcp_pending_summary, r.agentLabel, time, r.argsSummary ?: "")
                binding.approve.setOnClickListener { onDecision(r.id, true) }
                binding.reject.setOnClickListener { onDecision(r.id, false) }
            }
        }
    }
}
