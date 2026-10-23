package io.github.ocpabdurrahim.fintrack

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * The phone against the local Firebase emulators. "The web" is played by
 * writing to the emulator's REST API directly, i.e. a separate client, so its
 * changes reach the app the way a browser's would.
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val host = BuildConfig.FIREBASE_EMULATOR_HOST

    @Before
    fun setUp() {
        assumeTrue("Needs a build pointed at the Firebase emulators", host.isNotEmpty())
        Firebase.auth.signOut()
        resetApp()
        EmulatorAdmin(host, Firebase.app.options.projectId!!).reset()
    }

    @After
    fun tearDown() {
        if (host.isNotEmpty()) Firebase.auth.signOut()
    }

    private suspend fun eventually(what: String, check: suspend () -> Boolean) {
        try {
            withTimeout(30_000) { while (!check()) delay(250) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting until: $what (${container.sync.status.value})", e)
        }
    }

    @Test
    fun phoneAndCloudStayInStep() = runBlocking {
        Firebase.auth.signInWithEmailAndPassword(OWNER_EMAIL, PASSWORD).await()
        val uid = Firebase.auth.currentUser!!.uid
        assertEquals(OWNER_UID, uid)
        val cloud = EmulatorRest(host, Firebase.app.options.projectId!!, uid)
        val now = System.currentTimeMillis()

        // A brand-new account: the phone uploads its starter records.
        eventually("starter categories are in the cloud") { cloud.get("categories", "cat_food")?.get("name") == "Food" }

        // Made on the phone -> in the cloud.
        val id = container.repository.saveTransaction(
            TransactionEntity(type = TxnType.EXPENSE, amount = 25_000, categoryId = "cat_food", accountId = "acc_cash", date = now, note = "from phone")
        )
        eventually("the phone's expense is in the cloud") { cloud.get("transactions", id)?.get("amount") == 25_000L }

        // Made on the web -> on the phone, and on screen.
        cloud.put(
            "users/$uid/transactions/fromWeb1",
            mapOf(
                "type" to "INCOME", "amount" to 500_000L, "categoryId" to "cat_salary", "accountId" to "acc_bank",
                "toAccountId" to null, "date" to now, "note" to "from web", "recurringId" to null, "createdAt" to now,
                "deleted" to false,
            ),
        )
        eventually("the web's income is on the phone") { container.repository.getTransaction("fromWeb1")?.amount == 500_000L }
        compose.waitForTextOf("balance_total", "৳4,750", timeoutMs = 30_000)

        // Deleted on the web -> gone from the phone.
        cloud.put("users/$uid/transactions/fromWeb1", mapOf("deleted" to true))
        eventually("the web's delete reached the phone") { container.repository.getTransaction("fromWeb1") == null }

        // Deleted on the phone -> a tombstone in the cloud.
        container.repository.deleteTransaction(id)
        eventually("the phone's delete is in the cloud") { cloud.get("transactions", id)?.get("deleted") == true }

        // Currency picked on the web -> used on the phone.
        cloud.put("users/$uid", mapOf("currencyCode" to "USD", "currencySymbol" to "$", "grouping" to "INTERNATIONAL"))
        eventually("the web's currency is on the phone") { container.settings.settings.first().currencySymbol == "$" }
    }

    @Test
    fun anyOtherAccountIsTurnedAway() = runBlocking {
        val id = container.repository.saveTransaction(
            TransactionEntity(
                type = TxnType.EXPENSE, amount = 9_000, categoryId = "cat_food", accountId = "acc_cash",
                date = System.currentTimeMillis(), note = "stays on the phone",
            )
        )
        val stranger = "stranger-${System.nanoTime()}@fintrack.test"
        Firebase.auth.createUserWithEmailAndPassword(stranger, PASSWORD).await()

        eventually("the stranger is signed out again") { Firebase.auth.currentUser == null }
        eventually("the phone knows why") { container.sync.status.value.refusedEmail == stranger }
        assertEquals("stays on the phone", container.repository.getTransaction(id)?.note)

        compose.onNodeWithTag("nav_more").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("private to its owner", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    companion object {
        /** The test account firestore.rules lets in alongside the real owner. */
        const val OWNER_UID = "fintrack-test-owner"
        const val OWNER_EMAIL = "owner@fintrack.test"
        const val PASSWORD = "secret-pass"
    }
}

/** The emulators' admin endpoints; "Bearer owner" acts as the project's owner. */
private class EmulatorAdmin(private val host: String, private val project: String) {
    /** Empties both emulators, then creates the owner account with its fixed ID. */
    fun reset() {
        call("DELETE", "http://$host:8080/emulator/v1/projects/$project/databases/(default)/documents")
        call("DELETE", "http://$host:9099/emulator/v1/projects/$project/accounts")
        call(
            "POST", "http://$host:9099/identitytoolkit.googleapis.com/v1/projects/$project/accounts",
            JSONObject().put("localId", SyncTest.OWNER_UID).put("email", SyncTest.OWNER_EMAIL).put("password", SyncTest.PASSWORD),
        )
    }

    private fun call(method: String, url: String, body: JSONObject? = null) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer owner")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(conn.responseCode == 200) { "$method $url failed: ${conn.responseCode} ${conn.errorStream?.bufferedReader()?.readText()}" }
        } finally {
            conn.disconnect()
        }
    }
}

/** Minimal Firestore emulator REST client; "Bearer owner" bypasses security rules. */
private class EmulatorRest(host: String, project: String, private val uid: String) {
    private val base = "http://$host:8080/v1/projects/$project/databases/(default)/documents"
    private val docPrefix = "projects/$project/databases/(default)/documents"

    fun get(collection: String, id: String): Map<String, Any?>? {
        val conn = URL("$base/users/$uid/$collection/$id").openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer owner")
        return try {
            if (conn.responseCode == 404) return null
            val body = conn.inputStream.bufferedReader().readText()
            val fields = JSONObject(body).optJSONObject("fields") ?: return emptyMap()
            fields.keys().asSequence().associateWith { decode(fields.getJSONObject(it)) }
        } finally {
            conn.disconnect()
        }
    }

    /** Replaces the document at [path] with [fields], stamping updatedAt with the server time. */
    fun put(path: String, fields: Map<String, Any?>) {
        val write = JSONObject()
            .put("update", JSONObject().put("name", "$docPrefix/$path").put("fields", encode(fields)))
            .put("updateTransforms", JSONArray().put(JSONObject().put("fieldPath", "updatedAt").put("setToServerValue", "REQUEST_TIME")))
        val conn = URL("$base:commit").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer owner")
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(JSONObject().put("writes", JSONArray().put(write)).toString().toByteArray()) }
            check(conn.responseCode == 200) { "Emulator write failed: ${conn.responseCode} ${conn.errorStream?.bufferedReader()?.readText()}" }
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(fields: Map<String, Any?>): JSONObject {
        val out = JSONObject()
        for ((k, v) in fields) {
            out.put(k, when (v) {
                null -> JSONObject().put("nullValue", JSONObject.NULL)
                is String -> JSONObject().put("stringValue", v)
                is Boolean -> JSONObject().put("booleanValue", v)
                is Int, is Long -> JSONObject().put("integerValue", v.toString())
                else -> error("Unsupported value $v")
            })
        }
        return out
    }

    private fun decode(v: JSONObject): Any? = when {
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("timestampValue") -> v.getString("timestampValue")
        else -> null
    }
}
