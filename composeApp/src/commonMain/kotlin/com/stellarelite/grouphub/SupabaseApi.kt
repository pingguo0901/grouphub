package com.stellarelite.grouphub

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

object SupabaseApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // 登录
    suspend fun signIn(email: String, password: String): Result<String> {
        return try {
            val resp = client.post("${Config.SUPABASE_URL}/auth/v1/token?grant_type=password") {
                header("apikey", Config.SUPABASE_ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody("""{"email":"$email","password":"$password"}""")
            }
            val body: AuthResponse = resp.body()
            if (body.accessToken != null) Result.success(body.accessToken)
            else Result.failure(Exception(body.errorDescription ?: body.msg ?: "登录失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 通用表查询，返回 JsonObject 列表
    suspend fun fetchTable(token: String, table: String, query: String = ""): List<JsonObject> {
        val resp = client.get("${Config.SUPABASE_URL}/rest/v1/$table$query") {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
        }
        val text = resp.bodyAsText()
        if (text.isBlank() || text == "[]") return emptyList()
        return runCatching { json.parseToJsonElement(text).jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())
    }

    // 调用 Edge Function
    suspend fun callFunction(name: String, token: String, body: String = "{}"): String {
        val resp = client.post("${Config.SUPABASE_URL}/functions/v1/$name") {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return resp.bodyAsText()
    }

    // 读业务主体
    suspend fun fetchEntities(token: String): List<BusinessEntity> {
        val resp = client.get("${Config.SUPABASE_URL}/rest/v1/business_entity?select=id,name,business_industry") {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
        }
        return resp.body()
    }

    suspend fun fetchRevenue(token: String, entityId: String): Double {
        val resp = client.get(
            "${Config.SUPABASE_URL}/rest/v1/cashflow_business?select=amount&flow_type=eq.收入&account_type=eq.official&business_entity_id=eq.$entityId"
        ) {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
        }
        val rows: List<CashflowRow> = resp.body()
        return rows.sumOf { it.amount }
    }

    suspend fun fetchCost(token: String, entityId: String): Double {
        val resp = client.get(
            "${Config.SUPABASE_URL}/rest/v1/cashflow_business?select=amount&flow_type=eq.成本支出&account_type=eq.official&business_entity_id=eq.$entityId"
        ) {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
        }
        val rows: List<CashflowRow> = resp.body()
        return rows.sumOf { it.amount }
    }

    suspend fun fetchOwnerDrawing(token: String, entityId: String): Double {
        val resp = client.get(
            "${Config.SUPABASE_URL}/rest/v1/owner_drawing?select=amount_myr&business_entity_id=eq.$entityId"
        ) {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
        }
        val rows: List<OwnerDrawingRow> = resp.body()
        return rows.sumOf { it.amountMyr }
    }

    // 通用写入（插入一行）
    suspend fun insertRow(token: String, table: String, body: String): Result<String> {
        return try {
            val resp = client.post("${Config.SUPABASE_URL}/rest/v1/$table") {
                header("apikey", Config.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (resp.status.value in 200..299) Result.success(resp.bodyAsText())
            else Result.failure(Exception("写入失败(${resp.status.value})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 列出 Storage bucket 文件
    suspend fun listFiles(token: String, bucket: String): List<String> {
        return try {
            val resp = client.post("${Config.SUPABASE_URL}/storage/v1/object/list/$bucket") {
                header("apikey", Config.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"prefix":"","limit":100,"offset":0}""")
            }
            val text = resp.bodyAsText()
            if (text.isBlank()) emptyList()
            else json.parseToJsonElement(text).jsonArray.mapNotNull {
                (it as? JsonObject)?.get("name")?.let { n -> (n as? JsonPrimitive)?.content }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 便捷：解析 JsonObject 的字符串/数字字段
    fun str(o: JsonObject, key: String): String = (o[key] as? JsonPrimitive)?.content ?: ""
    fun dbl(o: JsonObject, key: String): Double = (o[key] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
}
