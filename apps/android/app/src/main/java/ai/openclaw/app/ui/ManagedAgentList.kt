package ai.openclaw.app.ui

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.BotAppearance
import ai.openclaw.app.ui.design.CustomBotAvatar
import ai.openclaw.app.ui.design.LocalBotAppearance
import ai.openclaw.app.ui.design.botColors
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AgentArrangement(val primary: String = "", val sections: List<String> = emptyList(), val assignments: Map<String, String> = emptyMap())

@Composable
internal fun ManagedAgentList(viewModel: MainViewModel, agents: List<GatewayAgentSummary>, matching: List<GatewayAgentSummary>, defaultId: String?, onSelect: (String) -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier) {
  val gateway by viewModel.activeGatewayStableId.collectAsState()
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("clawnova_agent_arrangement", android.content.Context.MODE_PRIVATE) }
  val key = gateway ?: ""
  var arrangement by remember(key) { mutableStateOf(runCatching {
    val json = JSONObject(prefs.getString(key, "{}") ?: "{}")
    val array = json.optJSONArray("sections") ?: JSONArray()
    val assignments = json.optJSONObject("assignments") ?: JSONObject()
    AgentArrangement(json.optString("primary"), (0 until array.length()).map { array.getString(it) }, assignments.keys().asSequence().associateWith { assignments.getString(it) })
  }.getOrDefault(AgentArrangement())) }
  fun save(value: AgentArrangement) {
    arrangement = value
    val json = JSONObject().put("primary", value.primary).put("sections", JSONArray(value.sections)).put("assignments", JSONObject(value.assignments))
    if (key.isNotEmpty()) prefs.edit().putString(key, json.toString()).apply()
  }
  val appearances = LocalBotAppearance.current
  var appearanceAgent by remember(key) { mutableStateOf<GatewayAgentSummary?>(null) }
  var menuAgent by remember { mutableStateOf<GatewayAgentSummary?>(null) }
  var moveAgent by remember { mutableStateOf<GatewayAgentSummary?>(null) }
  var deleteAgent by remember { mutableStateOf<GatewayAgentSummary?>(null) }
  var newSectionFor by remember { mutableStateOf<GatewayAgentSummary?>(null) }
  var removeSection by remember { mutableStateOf<String?>(null) }
  var sectionName by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val primary = agents.firstOrNull { it.id == arrangement.primary } ?: agents.firstOrNull { it.id == defaultId } ?: agents.firstOrNull()
  val visiblePrimary = primary?.takeIf { p -> matching.any { it.id == p.id } }
  val others = matching.filterNot { it.id == primary?.id }
  var collapsed by remember(key) { mutableStateOf(setOf<String>()) }
  val unassigned = stringResource(R.string.cn_unassigned)
  Column(modifier) {
    error?.let { Text(it, color = ClawTheme.colors.danger, modifier = Modifier.padding(8.dp)) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
      if (visiblePrimary != null) item {
        Box { HomeBotHero(visiblePrimary) { onSelect(visiblePrimary.id) }; IconButton(onClick = { menuAgent = visiblePrimary }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cn_bot_options), tint = ClawTheme.colors.text) } }
      }
      if (agents.isEmpty()) item { Text(stringResource(R.string.clawnova_no_agents), modifier = Modifier.padding(16.dp), color = ClawTheme.colors.text) }
      val groups = arrangement.sections.map { it to others.filter { agent -> arrangement.assignments[agent.id] == it } } + (unassigned to others.filter { arrangement.assignments[it.id] !in arrangement.sections })
      groups.forEachIndexed { index, (name, members) ->
        if (members.isNotEmpty() || index < arrangement.sections.size) {
          item(key = "section:$index:$name") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text((if (name in collapsed) "▸ " else "▾ ") + name, color = ClawTheme.colors.textMuted, modifier = Modifier.weight(1f).clickable { collapsed = if (name in collapsed) collapsed - name else collapsed + name }.padding(12.dp))
              if (index < arrangement.sections.size) Text("×", color = ClawTheme.colors.textMuted, modifier = Modifier.clickable { removeSection = name }.padding(12.dp))
            }
          }
          if (name !in collapsed) items(members, key = { "agent:${it.id}" }) { agent ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
              Box(Modifier.weight(1f)) { HomeBotRow(agent) { onSelect(agent.id) } }
              IconButton(onClick = { menuAgent = agent }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cn_bot_options), tint = ClawTheme.colors.text) }
            }
          }
        }
      }
    }
  }
  menuAgent?.let { agent ->
    AlertDialog(onDismissRequest = { menuAgent = null }, title = { Text(agent.name?.takeIf(String::isNotBlank) ?: agent.id) }, text = {
      Column {
        TextButton(enabled = !gateway.isNullOrBlank(), onClick = { appearanceAgent = agent; menuAgent = null }) { Text(stringResource(R.string.cn_edit_bot_icon)) }
        TextButton(onClick = { save(arrangement.copy(primary = agent.id)); menuAgent = null }) { Text(stringResource(R.string.cn_set_primary)) }
        TextButton(onClick = { moveAgent = agent; menuAgent = null }) { Text(stringResource(R.string.cn_move_section)) }
        TextButton(enabled = !busy, onClick = { deleteAgent = agent; menuAgent = null }) { Text(stringResource(R.string.cn_delete_bot), color = ClawTheme.colors.danger) }
      }
    }, confirmButton = { TextButton(onClick = { menuAgent = null }) { Text(stringResource(R.string.cn_cancel)) } })
  }
  appearanceAgent?.let { agent ->
    var draft by remember(agent.id) { mutableStateOf(appearances.styles[agent.id] ?: BotAppearance(0, 0)) }
    val shapeNames = listOf(stringResource(R.string.cn_shape_bubble), stringResource(R.string.cn_shape_round), stringResource(R.string.cn_shape_hexagon), stringResource(R.string.cn_shape_star))
    val colorNames = listOf(stringResource(R.string.cn_color_red), stringResource(R.string.cn_color_orange), stringResource(R.string.cn_color_yellow), stringResource(R.string.cn_color_green), stringResource(R.string.cn_color_teal), stringResource(R.string.cn_color_blue), stringResource(R.string.cn_color_purple), stringResource(R.string.cn_color_pink))
    AlertDialog(
      onDismissRequest = { appearanceAgent = null },
      title = { Text(stringResource(R.string.cn_edit_bot_icon)) },
      text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CustomBotAvatar(draft, 72.dp) }
        Text(stringResource(R.string.cn_shape), style = ClawTheme.type.section)
        shapeNames.forEachIndexed { index, name ->
          Row(Modifier.fillMaxWidth().selectable(selected = draft.shape == index, role = Role.RadioButton, onClick = { draft = draft.copy(shape = index) }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = draft.shape == index, onClick = null)
            Text(name)
          }
        }
        Text(stringResource(R.string.cn_color), style = ClawTheme.type.section)
        botColors.chunked(4).forEachIndexed { row, colors -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          colors.forEachIndexed { column, color ->
            val index = row * 4 + column
            Box(Modifier.size(44.dp).semantics { contentDescription = colorNames[index] }.selectable(selected = draft.color == index, role = Role.RadioButton, onClick = { draft = draft.copy(color = index) }).padding(4.dp).background(Color(color), CircleShape).border(if (draft.color == index) 3.dp else 0.dp, ClawTheme.colors.text, CircleShape))
          }
        } }
      } },
      confirmButton = { TextButton(onClick = { appearances.save(agent.id, draft); appearanceAgent = null }) { Text(stringResource(R.string.cn_save)) } },
      dismissButton = { Row {
        TextButton(onClick = { appearances.save(agent.id, null); appearanceAgent = null }) { Text(stringResource(R.string.cn_restore_icon)) }
        TextButton(onClick = { appearanceAgent = null }) { Text(stringResource(R.string.cn_cancel)) }
      } },
    )
  }
  moveAgent?.let { agent ->
    AlertDialog(onDismissRequest = { moveAgent = null }, title = { Text(stringResource(R.string.cn_move_section)) }, text = { Column {
      (arrangement.sections + unassigned).forEachIndexed { index, name -> TextButton(onClick = {
        val updated = if (index == arrangement.sections.size) arrangement.assignments - agent.id else arrangement.assignments + (agent.id to name)
        save(arrangement.copy(assignments = updated)); moveAgent = null
      }) { Text(name) } }
      TextButton(onClick = { sectionName = ""; newSectionFor = agent; moveAgent = null }) { Text(stringResource(R.string.cn_new_section)) }
    } }, confirmButton = { TextButton(onClick = { moveAgent = null }) { Text(stringResource(R.string.cn_cancel)) } })
  }
  newSectionFor?.let { agent ->
    AlertDialog(onDismissRequest = { newSectionFor = null }, title = { Text(stringResource(R.string.cn_new_section)) }, text = { OutlinedTextField(value = sectionName, onValueChange = { sectionName = it }, singleLine = true, label = { Text(stringResource(R.string.cn_section_name)) }) }, confirmButton = {
      TextButton(enabled = sectionName.trim().isNotEmpty() && arrangement.sections.none { it.equals(sectionName.trim(), true) }, onClick = { val name = sectionName.trim(); save(arrangement.copy(sections = arrangement.sections + name, assignments = arrangement.assignments + (agent.id to name))); newSectionFor = null }) { Text(stringResource(R.string.cn_save)) }
    }, dismissButton = { TextButton(onClick = { newSectionFor = null }) { Text(stringResource(R.string.cn_cancel)) } })
  }
  removeSection?.let { name -> AlertDialog(onDismissRequest = { removeSection = null }, title = { Text(stringResource(R.string.cn_remove_section)) }, text = { Text(stringResource(R.string.cn_remove_section_body)) }, confirmButton = { TextButton(onClick = { save(arrangement.copy(sections = arrangement.sections - name, assignments = arrangement.assignments.filterValues { it != name })); removeSection = null }) { Text(stringResource(R.string.cn_delete)) } }, dismissButton = { TextButton(onClick = { removeSection = null }) { Text(stringResource(R.string.cn_cancel)) } }) }
  deleteAgent?.let { agent -> AlertDialog(onDismissRequest = { deleteAgent = null }, title = { Text(stringResource(R.string.cn_delete_bot)) }, text = { Text(stringResource(R.string.cn_delete_bot_body, agent.name?.takeIf(String::isNotBlank) ?: agent.id)) }, confirmButton = { TextButton(onClick = {
    deleteAgent = null; busy = true; error = null; val requestGateway = gateway
    scope.launch { try { viewModel.deleteAgent(agent.id); if (viewModel.activeGatewayStableId.value == requestGateway) { save(arrangement.copy(primary = if (arrangement.primary == agent.id) "" else arrangement.primary, assignments = arrangement.assignments - agent.id)); appearances.save(agent.id, null) } } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: context.getString(R.string.cn_operation_error) } finally { busy = false } }
  }) { Text(stringResource(R.string.cn_delete), color = ClawTheme.colors.danger) } }, dismissButton = { TextButton(onClick = { deleteAgent = null }) { Text(stringResource(R.string.cn_cancel)) } }) }
}
