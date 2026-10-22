package io.github.ocpabdurrahim.fintrack.data

import java.security.SecureRandom

/**
 * Record ids are strings so one record has the same id on the phone, in the
 * cloud and on the web. New records get a random 20-character id (the same
 * shape Firestore uses); a few ids are derived instead, so two devices that
 * create "the same" record independently agree on it:
 *  - starter accounts and categories (see [DefaultData]),
 *  - budgets: one per category,
 *  - recurring occurrences: one per schedule and occurrence number.
 */
object Ids {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun newId(): String = buildString(20) {
        repeat(20) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun budget(categoryId: String): String = "budget-$categoryId"

    fun occurrence(recurringId: String, n: Int): String = "${recurringId}_$n"
}
