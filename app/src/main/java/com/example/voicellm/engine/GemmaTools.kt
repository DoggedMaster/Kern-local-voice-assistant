package com.example.voicellm.engine

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun buildTools(enableWebSearch: Boolean): List<ToolProvider> = buildList {
    add(tool(DateTimeTool()))
    if (enableWebSearch) add(tool(WebSearchTool()))
}

private class DateTimeTool : OpenApiTool {
    override fun getToolDescriptionJsonString() = """
        {
          "name": "get_current_datetime",
          "description": "Returns the current date and exact time.",
          "parameters": { "type": "object", "properties": {} }
        }
    """.trimIndent()

    override fun execute(argsJson: String): String {
        val fmt = SimpleDateFormat("EEEE, dd.MM.yyyy, HH:mm:ss 'Uhr'", Locale.GERMAN)
        return fmt.format(Date())
    }
}

private class WebSearchTool : OpenApiTool {
    override fun getToolDescriptionJsonString() = """
        {
          "name": "web_search",
          "description": "Searches the internet for current information, news, facts, or any real-time data.",
          "parameters": {
            "type": "object",
            "properties": {
              "query": {
                "type": "string",
                "description": "The search query"
              }
            },
            "required": ["query"]
          }
        }
    """.trimIndent()

    override fun execute(argsJson: String): String {
        val query = runCatching { JSONObject(argsJson).getString("query") }
            .getOrDefault(argsJson)
        return runBlocking { WebSearch().search(query) }.ifBlank { "Keine Ergebnisse gefunden." }
    }
}
