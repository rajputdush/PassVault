package com.ritvik.passvault.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/** Which language the person picked in Settings → Appearance. */
enum class AppLanguage(val key: String, val label: String) {
    SYSTEM("system", "System"),
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Translates the app's text. The screens are written in English; [tr] swaps in Hindi when that language is active and
 * leaves the English text alone when there is no translation, so a missing entry is never a crash or a blank.
 * The active language is Compose state, so changing it redraws every screen immediately.
 */
object Strings {
    private var preference by mutableStateOf(AppLanguage.SYSTEM)

    fun applyLanguage(language: AppLanguage) {
        preference = language
    }

    val isHindi: Boolean
        get() = when (preference) {
            AppLanguage.HINDI -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> Locale.getDefault().language == "hi"
        }

    fun tr(text: String): String {
        if (!isHindi || text.isEmpty()) return text
        HindiStrings.exact[text]?.let { return it }
        for (template in templates) {
            val match = template.regex.matchEntire(text) ?: continue
            var result = template.hindi
            match.groupValues.drop(1).forEachIndexed { i, value -> result = result.replace("{$i}", tr(value)) }
            return result
        }
        return text
    }

    private class Template(english: String, val hindi: String) {
        // {} in the English text stands for a changing part, such as a number or a name.
        val regex = Regex(english.split("{}").joinToString("(.+?)") { Regex.escape(it) })
    }

    private fun t(english: String, hindi: String) = Template(english, hindi)

    private val templates = listOf(
        t("Trash ({})", "ट्रैश ({0})"),
        t("{} words", "{0} शब्द"),
        t("{} characters", "{0} अक्षर"),
        t("Items are removed for good after {} days.", "आइटम {0} दिन बाद हमेशा के लिए हट जाते हैं।"),
        t("All {} item(s) will be removed for good. This cannot be undone.", "सभी {0} आइटम हमेशा के लिए हट जाएँगे। इसे वापस नहीं किया जा सकता।"),
        t("\"{}\" will be removed for good. This cannot be undone.", "\"{0}\" हमेशा के लिए हट जाएगा। इसे वापस नहीं किया जा सकता।"),
        t("{}  ·  {} days left", "{0}  ·  {1} दिन बाकी"),
        t("{}  ·  {} day left", "{0}  ·  {1} दिन बाकी"),
        t("Move this {} to Trash?", "इस {0} को ट्रैश में भेजें?"),
        t("Makes an encrypted link or QR code for the saved version of this {}.", "इस {0} के सेव किए संस्करण का एन्क्रिप्टेड लिंक या QR कोड बनाता है।"),
        t("Version {}", "संस्करण {0}"),
        t("{} passwords need attention", "{0} पासवर्ड पर ध्यान देने की ज़रूरत है"),
        t("{} saved passwords, {} need attention.", "{0} सेव किए पासवर्ड, {1} पर ध्यान देने की ज़रूरत है।"),
        t("{} saved password, {} needs attention.", "{0} सेव किया पासवर्ड, {1} पर ध्यान देने की ज़रूरत है।"),
        t("{} saved passwords, {} needs attention.", "{0} सेव किए पासवर्ड, {1} पर ध्यान देने की ज़रूरत है।"),
        t("Shares its password with {} other item(s)", "अपना पासवर्ड {0} अन्य आइटम के साथ साझा करता है"),
        t("Move {} extra copy(ies) to Trash", "{0} अतिरिक्त प्रति ट्रैश में भेजें"),
        t("Expired {}", "{0} को समाप्त हो गया"),
        t("Expires this month ({})", "इसी महीने समाप्त ({0})"),
        t("Expires {}", "{0} को समाप्त होगा"),
        t("Checked {} of {}", "{1} में से {0} जाँचे गए"),
        t("{} password was found in data breaches. Change them now.", "{0} पासवर्ड डेटा लीक में मिला। इसे अभी बदलें।"),
        t("{} passwords were found in data breaches. Change them now.", "{0} पासवर्ड डेटा लीक में मिले। इन्हें अभी बदलें।"),
        t("Seen {} times in breaches", "लीक में {0} बार दिखा"),
        t("Seen {} time in breaches", "लीक में {0} बार दिखा"),
        t("Strength: {}", "मज़बूती: {0}"),
        t("Use at least {} characters.", "कम से कम {0} अक्षर इस्तेमाल करें।"),
        t("Use at least {} characters for the new password.", "नए पासवर्ड के लिए कम से कम {0} अक्षर इस्तेमाल करें।"),
        t("Too many wrong attempts. Try again in {}.", "बहुत ज़्यादा गलत कोशिशें। {0} बाद दोबारा कोशिश करें।"),
        t("Wrong password. Try again in {}.", "गलत पासवर्ड। {0} बाद दोबारा कोशिश करें।"),
        t("Wrong current password. Try again in {}.", "मौजूदा पासवर्ड गलत है। {0} बाद दोबारा कोशिश करें।"),
        t("Wrong master password. Too many attempts: try again in {}.", "गलत मास्टर पासवर्ड। बहुत ज़्यादा कोशिशें: {0} बाद दोबारा कोशिश करें।"),
        t("Backup saved with {} item(s). To open it you need the master password you have now, or your recovery key.", "बैकअप {0} आइटम के साथ सेव हो गया। इसे खोलने के लिए आपको अभी वाला मास्टर पासवर्ड या अपनी रिकवरी कुंजी चाहिए।"),
        t("About {} bits of strength. Easy to type on a phone and easy to remember.", "लगभग {0} बिट की मज़बूती। फ़ोन पर टाइप करना और याद रखना आसान।"),
        t("The last {} passwords this login used, so you can recover one if a change goes wrong.", "इस लॉगिन के पिछले {0} पासवर्ड, ताकि बदलाव गलत हो जाए तो आप कोई वापस पा सकें।"),
        t("Send the passphrase by a different route than the link, for example the link in a message and the passphrase in person or by phone. The link stops working on {}.", "पासफ़्रेज़ को लिंक से अलग रास्ते से भेजें, जैसे लिंक संदेश में और पासफ़्रेज़ आमने-सामने या फ़ोन पर। लिंक {0} को काम करना बंद कर देगा।"),
        t("Includes: {}", "इसमें शामिल: {0}"),
        t("{} of {}", "{0} / {1}"),
    )
}

/**
 * The app's Text. It looks like the normal Material 3 Text, but shows the translated words when another language is
 * active. Every screen imports this one instead of the Material 3 Text.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = Strings.tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}
