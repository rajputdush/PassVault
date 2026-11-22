package com.ritvik.passvault.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.core.content.IntentCompat

/** What the autofill service needs to know about a login form on screen. */
class ParsedForm(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    /**
     * The box holding the password to save. Same as [passwordId] on a login form; on a sign-up or
     * "change password" form it is the NEW password, not the current one.
     */
    val newPasswordId: AutofillId?,
    /** The site shown in a browser, as reported by the browser. Null for ordinary apps. */
    val webDomain: String?,
    /** The app that owns the form (the browser's package for web pages). */
    val packageName: String?,
    /** The text currently typed into the fields (used when saving). */
    val usernameValue: String?,
    val passwordValue: String?,
    /** Set when the screen is a payment-card form instead of a login form. Then the login fields above are all null. */
    val card: CardFormIds? = null,
) {
    val ids: List<AutofillId> get() = card?.allIds ?: listOfNotNull(usernameId, passwordId)
    /** Every box worth watching for a save, with the new-password box first. */
    val saveIds: List<AutofillId> get() = listOfNotNull(newPasswordId ?: passwordId, usernameId, passwordId).distinct()
}

/**
 * The boxes of a payment-card form. A card can have one combined expiry box ([expiryId]) or separate month and
 * year boxes; drop-downs list their entries in [monthOptions] / [yearOptions] so the right one can be picked.
 */
class CardFormIds(
    val numberId: AutofillId?,
    val expiryId: AutofillId?,
    val monthId: AutofillId?,
    val yearId: AutofillId?,
    val cvvId: AutofillId?,
    val holderId: AutofillId?,
    val monthOptions: List<String>? = null,
    val yearOptions: List<String>? = null,
    val expiryMaxLength: Int? = null,
    val yearMaxLength: Int? = null,
) {
    val allIds: List<AutofillId> get() = listOfNotNull(numberId, expiryId, monthId, yearId, cvvId, holderId)

    /** Packs the boxes into the intent that opens the card picker. */
    fun putInto(intent: Intent): Intent = intent
        .putExtra(EXTRA_NUMBER, numberId).putExtra(EXTRA_EXPIRY, expiryId).putExtra(EXTRA_MONTH, monthId)
        .putExtra(EXTRA_YEAR, yearId).putExtra(EXTRA_CVV, cvvId).putExtra(EXTRA_HOLDER, holderId)
        .putExtra(EXTRA_MONTH_OPTIONS, monthOptions?.toTypedArray())
        .putExtra(EXTRA_YEAR_OPTIONS, yearOptions?.toTypedArray())
        .putExtra(EXTRA_EXPIRY_MAX, expiryMaxLength ?: 0)
        .putExtra(EXTRA_YEAR_MAX, yearMaxLength ?: 0)

    companion object {
        private const val EXTRA_NUMBER = "passvault.cc_number_id"
        private const val EXTRA_EXPIRY = "passvault.cc_expiry_id"
        private const val EXTRA_MONTH = "passvault.cc_month_id"
        private const val EXTRA_YEAR = "passvault.cc_year_id"
        private const val EXTRA_CVV = "passvault.cc_cvv_id"
        private const val EXTRA_HOLDER = "passvault.cc_holder_id"
        private const val EXTRA_MONTH_OPTIONS = "passvault.cc_month_options"
        private const val EXTRA_YEAR_OPTIONS = "passvault.cc_year_options"
        private const val EXTRA_EXPIRY_MAX = "passvault.cc_expiry_max"
        private const val EXTRA_YEAR_MAX = "passvault.cc_year_max"

        /** The boxes packed by [putInto], or null if the intent is for a login form. */
        fun fromIntent(intent: Intent): CardFormIds? {
            fun id(key: String) = IntentCompat.getParcelableExtra(intent, key, AutofillId::class.java)
            val ids = CardFormIds(
                numberId = id(EXTRA_NUMBER), expiryId = id(EXTRA_EXPIRY), monthId = id(EXTRA_MONTH),
                yearId = id(EXTRA_YEAR), cvvId = id(EXTRA_CVV), holderId = id(EXTRA_HOLDER),
                monthOptions = intent.getStringArrayExtra(EXTRA_MONTH_OPTIONS)?.toList(),
                yearOptions = intent.getStringArrayExtra(EXTRA_YEAR_OPTIONS)?.toList(),
                expiryMaxLength = intent.getIntExtra(EXTRA_EXPIRY_MAX, 0).takeIf { it > 0 },
                yearMaxLength = intent.getIntExtra(EXTRA_YEAR_MAX, 0).takeIf { it > 0 },
            )
            return ids.takeIf { it.allIds.isNotEmpty() }
        }
    }
}

/** Finds the username and password fields (or the payment-card boxes) in the screen structure Android hands to the autofill service. */
object FormParser {
    private enum class Kind { PASSWORD, USERNAME, TEXT, CARD }

    private class Field(
        val id: AutofillId,
        val kind: Kind,
        val value: String?,
        val card: CardFieldKind? = null,
        val options: List<String>? = null,
        val maxLength: Int? = null,
    )

    fun parse(structure: AssistStructure): ParsedForm? {
        val fields = ArrayList<Field>()
        val state = State()
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, fields, state)
        }
        // The activity's own package is reported by the system and can't be faked by the page; view id packages
        // are often just "android" for the window frame, so they are only a fallback.
        val pkg = structure.activityComponent?.packageName ?: state.packageName

        // A payment form: the boxes are for a card, not for a login.
        val cardFields = fields.filter { it.kind == Kind.CARD }
        if (CardFieldClassifier.isCardForm(cardFields.mapNotNull { it.card })) {
            fun first(kind: CardFieldKind) = cardFields.firstOrNull { it.card == kind }
            return ParsedForm(
                usernameId = null, passwordId = null, newPasswordId = null,
                webDomain = state.webDomain, packageName = pkg, usernameValue = null, passwordValue = null,
                card = CardFormIds(
                    numberId = first(CardFieldKind.NUMBER)?.id,
                    expiryId = first(CardFieldKind.EXPIRY)?.id,
                    monthId = first(CardFieldKind.EXP_MONTH)?.id,
                    yearId = first(CardFieldKind.EXP_YEAR)?.id,
                    cvvId = first(CardFieldKind.CVV)?.id,
                    holderId = first(CardFieldKind.HOLDER)?.id,
                    monthOptions = first(CardFieldKind.EXP_MONTH)?.options,
                    yearOptions = first(CardFieldKind.EXP_YEAR)?.options,
                    expiryMaxLength = first(CardFieldKind.EXPIRY)?.maxLength,
                    yearMaxLength = first(CardFieldKind.EXP_YEAR)?.maxLength,
                ),
            )
        }

        val passwordIndex = fields.indexOfFirst { it.kind == Kind.PASSWORD }
        val password = fields.getOrNull(passwordIndex)
        val newPassword = pickNewPassword(fields.filter { it.kind == Kind.PASSWORD })
        val username: Field? = if (password != null) {
            // The closest sensible field above the password box.
            val before = fields.subList(0, passwordIndex)
            before.lastOrNull { it.kind == Kind.USERNAME } ?: before.lastOrNull { it.kind == Kind.TEXT }
        } else {
            // A username-only step (many sites ask for the password on the next page).
            fields.firstOrNull { it.kind == Kind.USERNAME }
        }
        if (password == null && username == null) return null
        return ParsedForm(
            usernameId = username?.id,
            passwordId = password?.id,
            newPasswordId = newPassword?.id,
            webDomain = state.webDomain,
            packageName = pkg,
            usernameValue = username?.value,
            passwordValue = newPassword?.value,
        )
    }

    /**
     * One password box: that one. Several: a sign-up or change-password form. The new password is the value typed
     * twice (new + confirm); failing that, the second box of a "current + new" pair, or the middle of three.
     */
    private fun pickNewPassword(passwords: List<Field>): Field? {
        if (passwords.size < 2) return passwords.firstOrNull()
        val repeated = passwords.mapNotNull { it.value?.takeIf { v -> v.isNotEmpty() } }
            .groupingBy { it }.eachCount().filter { it.value >= 2 }.keys.firstOrNull()
        if (repeated != null) return passwords.first { it.value == repeated }
        return passwords[1]
    }

    private class State {
        var webDomain: String? = null
        var packageName: String? = null
        /** Every typed text on the page, so a card number can still be found when its box has an unrecognisable name. */
        val texts = ArrayList<String?>()
    }

    private fun walk(node: AssistStructure.ViewNode, out: MutableList<Field>, state: State) {
        if (state.webDomain == null) state.webDomain = node.webDomain?.takeIf { it.isNotBlank() }
        if (state.packageName == null) state.packageName = node.idPackage?.takeIf { it.isNotBlank() }

        val id = node.autofillId
        if (id != null && node.visibility == View.VISIBLE) {
            val type = node.autofillType
            if (type == View.AUTOFILL_TYPE_TEXT) {
                val typed = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                if (!typed.isNullOrBlank() && node.autofillHints?.contains(View.AUTOFILL_HINT_PASSWORD) != true) state.texts += typed
            }
            val cardKind = if (type == View.AUTOFILL_TYPE_TEXT || type == View.AUTOFILL_TYPE_LIST) classifyCard(node) else null
            if (cardKind != null) {
                val options = if (type == View.AUTOFILL_TYPE_LIST) node.autofillOptions?.map { it.toString() } else null
                val maxLength = node.htmlInfo?.attributes?.firstOrNull { it.first.equals("maxlength", ignoreCase = true) }?.second?.toIntOrNull()
                val value = node.autofillValue
                // A drop-down reports the position of the chosen entry; turn it back into the entry's text.
                val text = when {
                    value == null -> null
                    value.isText -> value.textValue.toString()
                    value.isList -> options?.getOrNull(value.listValue)
                    else -> null
                }
                out += Field(id, Kind.CARD, text, cardKind, options, maxLength)
            } else if (type == View.AUTOFILL_TYPE_TEXT) {
                val kind = classify(node)
                if (kind != null) {
                    val text = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                    out += Field(id, kind, text)
                }
            }
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i), out, state)
    }

    /** What payment-card box this is, if it is one. Android hints and HTML autocomplete come first, names last. */
    private fun classifyCard(node: AssistStructure.ViewNode): CardFieldKind? {
        val hints = node.autofillHints?.toMutableList() ?: mutableListOf()
        val names = mutableListOf<String>()
        node.idEntry?.let { names += it }
        node.hint?.let { names += it }
        // android.util.Pair has no destructuring, so read first/second directly.
        node.htmlInfo?.attributes?.forEach { attr ->
            val name = attr.first?.lowercase() ?: return@forEach
            val value = attr.second ?: return@forEach
            when (name) {
                "autocomplete" -> hints += value
                "name", "id", "placeholder", "aria-label" -> names += value
            }
        }
        return CardFieldClassifier.classify(hints, names)
    }

    private fun classify(node: AssistStructure.ViewNode): Kind? {
        val hints = node.autofillHints?.toList().orEmpty()
        if (View.AUTOFILL_HINT_PASSWORD in hints) return Kind.PASSWORD
        if (hints.any { it == View.AUTOFILL_HINT_USERNAME || it == View.AUTOFILL_HINT_EMAIL_ADDRESS }) return Kind.USERNAME

        val input = node.inputType
        val cls = input and InputType.TYPE_MASK_CLASS
        val variation = input and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        ) return Kind.PASSWORD
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return Kind.PASSWORD

        // Web pages: look at the HTML attributes.
        val html = node.htmlInfo
        if (html != null) {
            val attrs = html.attributes.orEmpty().associate { it.first.lowercase() to it.second.lowercase() }
            val type = attrs["type"]
            if (type == "password") return Kind.PASSWORD
            val name = (attrs["name"].orEmpty() + " " + attrs["id"].orEmpty() + " " + attrs["autocomplete"].orEmpty())
            if (type == "email" || "username" in name || "email" in name || "login" in name || "user" in name) return Kind.USERNAME
            if (type == null || type == "text" || type == "tel") return Kind.TEXT
            return null
        }

        // Native apps: hints from the view id, e.g. "edit_username".
        val entry = node.idEntry?.lowercase().orEmpty()
        if ("pass" in entry || "pwd" in entry) return Kind.PASSWORD
        if ("user" in entry || "email" in entry || "login" in entry || "account" in entry) return Kind.USERNAME
        return if (cls == InputType.TYPE_CLASS_TEXT) Kind.TEXT else null
    }
}
