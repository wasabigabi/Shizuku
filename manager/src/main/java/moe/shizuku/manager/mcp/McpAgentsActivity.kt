package moe.shizuku.manager.mcp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.McpAgentItemBinding
import moe.shizuku.manager.databinding.McpAgentsActivityBinding
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

/**
 * Lists paired MCP agents, lets the user pair a new one (showing the one-time
 * token), revoke an existing one, and open the audit log.
 *
 * There is intentionally no per-agent tool allowlist editor yet: the bridge
 * ships with zero capability tools, so there is nothing to grant. That editor
 * arrives together with the first capability tool.
 */
class McpAgentsActivity : AppBarActivity() {

    companion object {
        // Mirrors McpTransportServer.DEFAULT_PORT; shown in the adb-forward hint
        // when the transport is currently off (no live port to report).
        private const val DEFAULT_PORT = 7231
    }

    private lateinit var viewModel: McpViewModel
    private lateinit var binding: McpAgentsActivityBinding
    private val adapter = AgentsAdapter { onAgentClicked(it) }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (!isFinishing) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Shizuku.pingBinder()) {
            finish()
            return
        }

        binding = McpAgentsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.mcp_agents_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[McpViewModel::class.java]

        binding.list.adapter = adapter
        binding.list.fixEdgeEffect()
        binding.list.addEdgeSpacing(top = 8f, bottom = 8f, unit = android.util.TypedValue.COMPLEX_UNIT_DIP)

        viewModel.agents.observe(this) {
            when (it.status) {
                Status.SUCCESS -> {
                    adapter.submit(it.data ?: emptyList())
                    binding.empty.visibility =
                        if (adapter.itemCount == 0) ViewGroup.VISIBLE else ViewGroup.GONE
                }
                Status.ERROR -> {
                    Toast.makeText(this, it.error?.message ?: "error", Toast.LENGTH_LONG).show()
                }
                Status.LOADING -> {}
            }
        }

        viewModel.loadAgents()
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.mcp_agents, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_pair -> { showPairDialog(); true }
            R.id.action_audit -> {
                startActivity(Intent(this, McpAuditActivity::class.java)); true
            }
            R.id.action_transport -> { showTransportDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPairDialog() {
        val input = EditText(this).apply { setHint(R.string.mcp_pair_label_hint) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mcp_pair_title)
            .setMessage(R.string.mcp_pair_message)
            .setView(input)
            .setPositiveButton(R.string.mcp_pair_action) { _, _ ->
                val label = input.text.toString().trim().ifEmpty { getString(R.string.mcp_pair_default_label) }
                doPair(label)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doPair(label: String) {
        lifecycleScope.launch {
            try {
                val paired = withContext(Dispatchers.IO) { McpRepository.pair(label) }
                showTokenDialog(paired)
                viewModel.loadAgents()
            } catch (e: Throwable) {
                Toast.makeText(this@McpAgentsActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTokenDialog(paired: McpRepository.Paired) {
        val message = getString(R.string.mcp_token_message, paired.agentId, paired.rawToken)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mcp_token_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.mcp_token_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("shizuku-mcp-token", paired.rawToken))
                Toast.makeText(this, R.string.mcp_token_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.mcp_token_done, null)
            .show()
    }

    private fun onAgentClicked(agent: McpRepository.Agent) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(agent.label)
            .setMessage(
                getString(
                    R.string.mcp_agent_detail,
                    agent.id,
                    DateUtils.getRelativeTimeSpanString(agent.createdAt),
                    agent.allowedTools.size,
                    if (agent.revoked) getString(R.string.mcp_agent_revoked) else getString(R.string.mcp_agent_active)
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
        if (!agent.revoked) {
            builder.setPositiveButton(R.string.mcp_agent_revoke_action) { _, _ -> confirmRevoke(agent) }
            builder.setNeutralButton(R.string.mcp_agent_edit_tools) { _, _ -> showToolEditor(agent) }
        }
        builder.show()
    }

    private fun showToolEditor(agent: McpRepository.Agent) {
        lifecycleScope.launch {
            try {
                val tools = withContext(Dispatchers.IO) { McpRepository.listTools() }
                if (tools.isEmpty()) {
                    Toast.makeText(this@McpAgentsActivity, R.string.mcp_no_tools, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val names = tools.map { it.name }
                val labels = tools.map { "${it.name}  (${it.dangerLevel})" }.toTypedArray()
                val checked = BooleanArray(tools.size) { agent.allowedTools.contains(names[it]) }
                MaterialAlertDialogBuilder(this@McpAgentsActivity)
                    .setTitle(getString(R.string.mcp_edit_tools_title, agent.label))
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val selected = names.filterIndexed { i, _ -> checked[i] }
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { McpRepository.setAllowedTools(agent.id, selected) }
                                viewModel.loadAgents()
                            } catch (e: Throwable) {
                                Toast.makeText(this@McpAgentsActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (e: Throwable) {
                Toast.makeText(this@McpAgentsActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTransportDialog() {
        lifecycleScope.launch {
            val status = try {
                withContext(Dispatchers.IO) { McpRepository.getTransportStatus() }
            } catch (e: Throwable) {
                Toast.makeText(this@McpAgentsActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
                return@launch
            }
            val port = if (status.running && status.port > 0) status.port else DEFAULT_PORT
            val state = if (status.running) getString(R.string.mcp_transport_on, status.port)
            else getString(R.string.mcp_transport_off)
            val toggle = if (status.running) R.string.mcp_transport_disable else R.string.mcp_transport_enable
            MaterialAlertDialogBuilder(this@McpAgentsActivity)
                .setTitle(R.string.mcp_transport_title)
                .setMessage(getString(R.string.mcp_transport_message, state, port))
                .setPositiveButton(toggle) { _, _ -> toggleTransport(!status.running) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun toggleTransport(enable: Boolean) {
        lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { McpRepository.setTransportEnabled(enable) }
                val msg = if (status.running) getString(R.string.mcp_transport_on, status.port)
                else getString(R.string.mcp_transport_off)
                Toast.makeText(this@McpAgentsActivity, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                Toast.makeText(this@McpAgentsActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmRevoke(agent: McpRepository.Agent) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mcp_agent_revoke_title)
            .setMessage(getString(R.string.mcp_agent_revoke_message, agent.label))
            .setPositiveButton(R.string.mcp_agent_revoke_action) { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { McpRepository.revoke(agent.id) }
                        viewModel.loadAgents()
                    } catch (e: Throwable) {
                        Toast.makeText(this@McpAgentsActivity, e.message ?: "error", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class AgentsAdapter(
        private val onClick: (McpRepository.Agent) -> Unit
    ) : RecyclerView.Adapter<AgentsAdapter.VH>() {

        private val items = mutableListOf<McpRepository.Agent>()

        fun submit(data: List<McpRepository.Agent>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = McpAgentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount() = items.size

        class VH(
            private val binding: McpAgentItemBinding,
            private val onClick: (McpRepository.Agent) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(agent: McpRepository.Agent) {
                val ctx = binding.root.context
                binding.title.text = agent.label
                val state = if (agent.revoked) ctx.getString(R.string.mcp_agent_revoked)
                else ctx.resources.getQuantityString(
                    R.plurals.mcp_agent_tools_count, agent.allowedTools.size, agent.allowedTools.size
                )
                binding.summary.text = ctx.getString(R.string.mcp_agent_summary, agent.id, state)
                binding.root.alpha = if (agent.revoked) 0.5f else 1f
                binding.root.setOnClickListener { onClick(agent) }
            }
        }
    }
}
