package com.ritvik.passvault.autofill

import com.ritvik.passvault.ui.Strings
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.ritvik.passvault.R
import com.ritvik.passvault.cards.CardInfo
import com.ritvik.passvault.items.VaultItem
import java.util.concurrent.atomic.AtomicInteger

/** Builds the pieces of autofill answers. */
object AutofillDatasets {
    private val requestCodes = AtomicInteger(1)

    fun row(context: Context, text: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, text)
        }

    /** A drop-down entry that fills [item] straight away. Only used while the vault is unlocked. */
    fun fillNow(context: Context, form: ParsedForm, item: VaultItem): Dataset {
        val label = item.displayTitle +
            if (item.username.isNotBlank()) " (${item.username})" else ""
        val builder = Dataset.Builder(row(context, Strings.tr(label)))
        setValues(builder, form.usernameId, form.passwordId, item)
        return builder.build()
    }

    /** The values only, without a drop-down entry. This is the answer the unlock/picker screen returns. */
    fun valuesOnly(form: ParsedForm, item: VaultItem): Dataset {
        val builder = Dataset.Builder()
        setValues(builder, form.usernameId, form.passwordId, item)
        return builder.build()
    }

    fun valuesOnly(usernameId: AutofillId?, passwordId: AutofillId?, item: VaultItem): Dataset {
        val builder = Dataset.Builder()
        setValues(builder, usernameId, passwordId, item)
        return builder.build()
    }

    /**
     * A drop-down entry that opens the PassVault screen first ("Unlock PassVault" or "Choose from PassVault").
     * The fields get no value yet; the screen returns the chosen login.
     */
    fun needsScreen(context: Context, form: ParsedForm, label: String): Dataset {
        val builder = Dataset.Builder(row(context, label))
        val card = form.card
        if (card != null) {
            card.allIds.forEach { builder.setValue(it, null) }
        } else {
            form.usernameId?.let { builder.setValue(it, null) }
            form.passwordId?.let { builder.setValue(it, null) }
        }
        builder.setAuthentication(authIntent(context, form))
        return builder.build()
    }

    /**
     * The values of a saved card for each box of a payment form, or null if the card has nothing for any of them.
     * Card numbers go in as digits only; the expiry follows each box's own shape (one box, two boxes or drop-downs).
     */
    fun cardValuesOnly(card: CardFormIds, item: VaultItem): Dataset? {
        val builder = Dataset.Builder()
        var any = false
        fun text(id: AutofillId?, value: String?) {
            if (id != null && !value.isNullOrEmpty()) {
                builder.setValue(id, AutofillValue.forText(value))
                any = true
            }
        }
        fun choice(id: AutofillId?, options: List<String>?, kind: CardFieldKind, fallback: String?) {
            if (id == null) return
            if (options != null) {
                val index = CardFill.pickOption(options, kind, item.cardExpiry) ?: return
                builder.setValue(id, AutofillValue.forList(index))
                any = true
            } else {
                text(id, fallback)
            }
        }
        text(card.numberId, CardInfo.digitsOnly(item.cardNumber))
        text(card.expiryId, CardFill.combined(item.cardExpiry, card.expiryMaxLength))
        choice(card.monthId, card.monthOptions, CardFieldKind.EXP_MONTH, CardFill.month(item.cardExpiry))
        choice(card.yearId, card.yearOptions, CardFieldKind.EXP_YEAR, CardFill.year(item.cardExpiry, if (card.yearMaxLength == 2) 2 else 4))
        text(card.cvvId, item.cardCvv)
        text(card.holderId, item.cardholder)
        return if (any) builder.build() else null
    }

    private fun setValues(builder: Dataset.Builder, usernameId: AutofillId?, passwordId: AutofillId?, item: VaultItem) {
        usernameId?.let { builder.setValue(it, AutofillValue.forText(item.username)) }
        passwordId?.let { builder.setValue(it, AutofillValue.forText(item.password)) }
    }

    private fun authIntent(context: Context, form: ParsedForm): IntentSender {
        val intent = Intent(context, AutofillAuthActivity::class.java)
            .putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, form.usernameId)
            .putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, form.passwordId)
            .putExtra(AutofillAuthActivity.EXTRA_WEB_DOMAIN, form.webDomain)
            .putExtra(AutofillAuthActivity.EXTRA_PACKAGE, form.packageName)
        form.card?.putInto(intent)
        // The system adds its own extras to this intent, so it has to be mutable on Android 12+.
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(context, requestCodes.getAndIncrement(), intent, flags).intentSender
    }
}
