package com.parental.control.core.turso

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP para Turso Cloud (LibSQL) utilizando la API v2 Pipeline.
 * Proporciona ejecución remota de SQL con alta velocidad, connection pooling y TLS v1.3.
 */
class TursoClient(
    private val databaseUrl: String = DEFAULT_DATABASE_URL,
    private val authToken: String = DEFAULT_AUTH_TOKEN,
    private val client: OkHttpClient = defaultHttpClient()
) {

    private val pipelineEndpoint: String = run {
        val base = databaseUrl.trim()
            .replace("libsql://", "https://")
            .removeSuffix("/")
        "$base/v2/pipeline"
    }

    /**
     * Ejecuta una única sentencia SQL de forma sincrónica.
     */
    fun execute(sql: String, args: List<Any?> = emptyList()): TursoResult {
        val results = pipeline(listOf(TursoStatement(sql, args)))
        return results.firstOrNull() ?: TursoResult(error = "No result returned from Turso")
    }

    /**
     * Ejecuta una consulta SELECT y devuelve una lista de mapas (nombre_columna -> valor).
     */
    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, Any?>> {
        val result = execute(sql, args)
        if (!result.isSuccess) {
            throw IOException("Error en consulta Turso: ${result.error}")
        }
        return result.rows
    }

    /**
     * Ejecuta un lote (pipeline) de sentencias atómicamente.
     */
    fun pipeline(statements: List<TursoStatement>): List<TursoResult> {
        if (statements.isEmpty()) return emptyList()

        val requestsArray = JSONArray()
        for (stmt in statements) {
            val stmtObj = JSONObject()
            stmtObj.put("sql", stmt.sql)

            if (stmt.args.isNotEmpty()) {
                val argsArray = JSONArray()
                for (arg in stmt.args) {
                    val argObj = JSONObject()
                    when (arg) {
                        null -> {
                            argObj.put("type", "null")
                        }
                        is Number -> {
                            if (arg is Float || arg is Double) {
                                argObj.put("type", "float")
                                argObj.put("value", arg.toDouble())
                            } else {
                                argObj.put("type", "integer")
                                argObj.put("value", arg.toLong().toString())
                            }
                        }
                        is Boolean -> {
                            argObj.put("type", "integer")
                            argObj.put("value", if (arg) "1" else "0")
                        }
                        is ByteArray -> {
                            argObj.put("type", "blob")
                            argObj.put("base64", java.util.Base64.getEncoder().encodeToString(arg))
                        }
                        else -> {
                            argObj.put("type", "text")
                            argObj.put("value", arg.toString())
                        }
                    }
                    argsArray.put(argObj)
                }
                stmtObj.put("args", argsArray)
            }

            val reqObj = JSONObject()
            reqObj.put("type", "execute")
            reqObj.put("stmt", stmtObj)
            requestsArray.put(reqObj)
        }

        val rootPayload = JSONObject()
        rootPayload.put("requests", requestsArray)

        val body = rootPayload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(pipelineEndpoint)
            .header("Authorization", "Bearer $authToken")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(respBody).optString("error", response.message)
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $respBody"
                    }
                    return statements.map { TursoResult(error = errorMsg) }
                }

                return parsePipelineResponse(respBody)
            }
        } catch (e: Exception) {
            return statements.map { TursoResult(error = "Excepción de conexión a Turso: ${e.message}") }
        }
    }

    private fun parsePipelineResponse(jsonStr: String): List<TursoResult> {
        val root = JSONObject(jsonStr)
        val resultsJson = root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<TursoResult>()

        for (i in 0 until resultsJson.length()) {
            val item = resultsJson.getJSONObject(i)
            val type = item.optString("type")
            if (type == "error") {
                val errObj = item.optJSONObject("error")
                val msg = errObj?.optString("message") ?: "Unknown error"
                list.add(TursoResult(error = msg))
                continue
            }

            val respObj = item.optJSONObject("response")
            val resultObj = respObj?.optJSONObject("result")
            if (resultObj == null) {
                list.add(TursoResult())
                continue
            }

            val colsArray = resultObj.optJSONArray("cols") ?: JSONArray()
            val colNames = mutableListOf<String>()
            for (c in 0 until colsArray.length()) {
                val col = colsArray.getJSONObject(c)
                colNames.add(col.optString("name", "col_$c"))
            }

            val rowsArray = resultObj.optJSONArray("rows") ?: JSONArray()
            val rows = mutableListOf<Map<String, Any?>>()
            for (r in 0 until rowsArray.length()) {
                val rowArr = rowsArray.getJSONArray(r)
                val rowMap = mutableMapOf<String, Any?>()
                for (c in 0 until rowArr.length()) {
                    val cell = rowArr.getJSONObject(c)
                    val cellType = cell.optString("type")
                    val value: Any? = when (cellType) {
                        "null" -> null
                        "integer" -> cell.optString("value").toLongOrNull()
                        "float" -> cell.optDouble("value")
                        "text" -> cell.optString("value")
                        "blob" -> cell.optString("base64")
                        else -> cell.opt("value")
                    }
                    val colName = colNames.getOrElse(c) { "col_$c" }
                    rowMap[colName] = value
                }
                rows.add(rowMap)
            }

            val affected = resultObj.optInt("affected_row_count", 0)
            list.add(TursoResult(rows = rows, affectedRowCount = affected))
        }

        return list
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val DEFAULT_DATABASE_URL = "https://aegis-parental-devherles.aws-us-east-2.turso.io"
        const val DEFAULT_AUTH_TOKEN = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3ODk5MDY2NzEsImlkIjoiMDFhMGJlYzAtMmEwMS03OGIxLWE3MjEtMzI4ZjZlMThjODMxIiwia2lkIjoiUXY5SVlXWjhGTkF6Mmgtc2pWU0xydWdTdG9RTWdaTlRtbUluQVdxUld0SSIsInJpZCI6ImEwYzE5YTRmLTUwOGUtNDY0MC1hYTViLWYwZmZjMDMyNzFhYSJ9.MTm3Pk32uvxfPSuCFm7UVJLdGg_VBpZFp09C2cMCZjKpCJY6wN-1WyvNmGT8c71zS34f5MbxMni7pOSYft8SBA"

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
