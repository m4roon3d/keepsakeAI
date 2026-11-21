package com.keepsakeai.app.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity
import com.keepsakeai.app.data.local.PersonalFactEntity
import com.keepsakeai.app.ui.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted backup of memories, payments, review queue, facts and settings.
 * File = "KSAI1" | salt(16) | iv(12) | AES-256-GCM(gzip(json)), key from PBKDF2-SHA256.
 * API keys are never included.
 */
object BackupManager {
    private const val MAGIC = "KSAI1"
    private const val ITERATIONS = 120_000
    private val prefFiles = listOf("money_prefs", "reminders", "theme_prefs", "upi_prefs")

    data class Summary(val memories: Int, val payments: Int, val facts: Int)

    suspend fun export(context: Context, uri: Uri, password: CharArray): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext as MindSpaceApp
        val dao = app.database.dao()
        val mem = dao.getAllMemories()
        val exp = dao.getAllExpenses()
        val pend = dao.getAllPending()
        val facts = dao.getFacts()
        val root = JSONObject()
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("memories", JSONArray(mem.map(::memToJson)))
            .put("expenses", JSONArray(exp.map(::expToJson)))
            .put("pending", JSONArray(pend.map(::pendToJson)))
            .put("facts", JSONArray(facts.map(::factToJson)))
            .put("prefs", JSONObject().apply { prefFiles.forEach { put(it, prefsToJson(context, it)) } })
        val plain = gzip(root.toString().toByteArray(Charsets.UTF_8))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        }
        val body = cipher.doFinal(plain)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            out.write(MAGIC.toByteArray()); out.write(salt); out.write(iv); out.write(body)
        }
        Summary(mem.size, exp.size, facts.size)
    }

    /** Replaces current data with the backup. Throws on a wrong password or a damaged file. */
    suspend fun restore(context: Context, uri: Uri, password: CharArray): Summary = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        require(bytes.size > 33 && String(bytes, 0, 5) == MAGIC) { "This isn't a keepsake backup file." }
        val salt = bytes.copyOfRange(5, 21)
        val iv = bytes.copyOfRange(21, 33)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        }
        val plain = try { cipher.doFinal(bytes, 33, bytes.size - 33) } catch (e: Exception) {
            throw IllegalArgumentException("Wrong password, or the file is damaged.")
        }
        val root = JSONObject(String(gunzip(plain), Charsets.UTF_8))
        val app = context.applicationContext as MindSpaceApp
        val dao = app.database.dao()
        val mem = root.optJSONArray("memories").objects().map(::memFromJson)
        val exp = root.optJSONArray("expenses").objects().map(::expFromJson)
        val pend = root.optJSONArray("pending").objects().map(::pendFromJson)
        val facts = root.optJSONArray("facts").objects().map(::factFromJson)
        app.database.withTransaction {
            dao.deleteAllMemories(); dao.deleteAllExpenses(); dao.clearPendingUpi(); dao.deleteAllFacts()
            dao.insertMemories(mem); dao.insertExpenses(exp); dao.insertPendings(pend); dao.insertFacts(facts)
        }
        root.optJSONObject("prefs")?.let { p -> prefFiles.forEach { name -> p.optJSONObject(name)?.let { restorePrefs(context, name, it) } } }
        ThemeStore.load(context)
        Summary(mem.size, exp.size, facts.size)
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    private fun gzip(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray) = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private fun memToJson(m: MemoryEntity) = JSONObject().put("id", m.id).put("title", m.title).put("raw", m.rawContent)
        .put("summary", m.aiSummary).put("collection", m.collectionName).put("source", m.source.name).put("url", m.sourceUrl ?: "")
        .put("tags", m.tagsCsv).put("emb", m.embeddingCsv).put("thread", m.threadKey).put("photos", m.photoUrisCsv).put("at", m.createdAt)

    private fun memFromJson(o: JSONObject) = MemoryEntity(
        id = o.optLong("id"), title = o.optString("title"), rawContent = o.optString("raw"), aiSummary = o.optString("summary"),
        collectionName = o.optString("collection", "General"),
        source = runCatching { CaptureSource.valueOf(o.optString("source")) }.getOrDefault(CaptureSource.TEXT_NOTE),
        sourceUrl = o.optString("url").ifBlank { null }, tagsCsv = o.optString("tags"), embeddingCsv = o.optString("emb"),
        threadKey = o.optString("thread", "General"), photoUrisCsv = o.optString("photos"), createdAt = o.optLong("at")
    )

    private fun expToJson(e: ExpenseEntity) = JSONObject().put("id", e.id).put("merchant", e.merchant).put("amount", e.amount)
        .put("currency", e.currency).put("category", e.category).put("notes", e.notes).put("source", e.sourceMethod).put("at", e.timestamp)

    private fun expFromJson(o: JSONObject) = ExpenseEntity(
        id = o.optLong("id"), merchant = o.optString("merchant"), amount = o.optDouble("amount"), currency = o.optString("currency", "INR"),
        category = o.optString("category", "Other"), notes = o.optString("notes"), sourceMethod = o.optString("source", "Manual"), timestamp = o.optLong("at")
    )

    private fun pendToJson(p: PendingUpiTransactionEntity) = JSONObject().put("id", p.id).put("merchant", p.merchant).put("amount", p.amount)
        .put("currency", p.currency).put("category", p.suggestedCategory).put("bank", p.bankOrApp).put("utr", p.utrRef ?: "")
        .put("raw", p.rawMessage).put("at", p.receivedAt)

    private fun pendFromJson(o: JSONObject) = PendingUpiTransactionEntity(
        id = o.optLong("id"), merchant = o.optString("merchant"), amount = o.optDouble("amount"), currency = o.optString("currency", "INR"),
        suggestedCategory = o.optString("category", "Other"), bankOrApp = o.optString("bank"), utrRef = o.optString("utr").ifBlank { null },
        rawMessage = o.optString("raw"), receivedAt = o.optLong("at")
    )

    private fun factToJson(f: PersonalFactEntity) = JSONObject().put("id", f.id).put("fact", f.fact).put("category", f.category)
        .put("pinned", f.pinned).put("at", f.createdAt)

    private fun factFromJson(o: JSONObject) = PersonalFactEntity(
        id = o.optLong("id"), fact = o.optString("fact"), category = o.optString("category", "Profile"),
        pinned = o.optBoolean("pinned"), createdAt = o.optLong("at")
    )

    private fun prefsToJson(context: Context, name: String): JSONObject {
        val o = JSONObject()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            when (v) {
                is Set<*> -> o.put(k, JSONObject().put("set", JSONArray(v.map { it.toString() })))
                is Boolean, is Int, is Long, is Float, is String -> o.put(k, JSONObject().put("type", v::class.simpleName).put("v", v))
            }
        }
        return o
    }

    private fun restorePrefs(context: Context, name: String, o: JSONObject) {
        val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        o.keys().forEach { k ->
            val item = o.getJSONObject(k)
            if (item.has("set")) {
                val arr = item.getJSONArray("set"); e.putStringSet(k, List(arr.length()) { arr.getString(it) }.toSet())
            } else when (item.optString("type")) {
                "Boolean" -> e.putBoolean(k, item.getBoolean("v"))
                "Int" -> e.putInt(k, item.getInt("v"))
                "Long" -> e.putLong(k, item.getLong("v"))
                "Float" -> e.putFloat(k, item.getDouble("v").toFloat())
                else -> e.putString(k, item.optString("v"))
            }
        }
        e.apply()
    }
}
