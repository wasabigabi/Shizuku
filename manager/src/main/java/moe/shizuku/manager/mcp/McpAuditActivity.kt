package moe.shizuku.manager.mcp

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.McpAuditItemBinding
import moe.shizuku.manager.databinding.McpAgentsActivityBinding
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

/**
 * Read-only view of the most recent gate decisions and call outcomes recorded
 * by the server-side audit log.
 */
class McpAuditActivity : AppBarActivity() {

    private lateinit var viewModel: McpViewModel
    private lateinit var binding: McpAgentsActivityBinding
    private val adapter = AuditAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Shizuku.pingBinder()) {
            finish()
            return
        }

        binding = McpAgentsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.mcp_audit_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.empty.setText(R.string.mcp_audit_empty)

        viewModel = ViewModelProvider(this)[McpViewModel::class.java]

        binding.list.adapter = adapter
        binding.list.fixEdgeEffect()
        binding.list.addEdgeSpacing(top = 8f, bottom = 8f, unit = android.util.TypedValue.COMPLEX_UNIT_DIP)

        viewModel.audit.observe(this) {
            when (it.status) {
                Status.SUCCESS -> {
                    adapter.submit(it.data ?: emptyList())
                    binding.empty.visibility =
                        if (adapter.itemCount == 0) ViewGroup.VISIBLE else ViewGroup.GONE
                }
                Status.ERROR -> Toast.makeText(this, it.error?.message ?: "error", Toast.LENGTH_LONG).show()
                Status.LOADING -> {}
            }
        }
        viewModel.loadAudit()
    }

    private class AuditAdapter : RecyclerView.Adapter<AuditAdapter.VH>() {

        private val items = mutableListOf<McpRepository.AuditEntry>()

        fun submit(data: List<McpRepository.AuditEntry>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = McpAuditItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount() = items.size

        class VH(private val binding: McpAuditItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(e: McpRepository.AuditEntry) {
                val ctx = binding.root.context
                binding.title.text = ctx.getString(R.string.mcp_audit_line, e.event, e.tool)
                val time = DateUtils.getRelativeTimeSpanString(e.timestamp)
                val detail = e.detail?.let { " · $it" } ?: ""
                binding.summary.text = "${e.agentLabel} · $time$detail"
            }
        }
    }
}
