package com.ritvik.passvault.sites

import com.ritvik.passvault.autofill.DomainMatcher

/**
 * Turns a saved website into what the list shows: a readable name and a brand colour for the icon.
 * Everything here works offline; nothing is looked up on the internet.
 */
object SiteInfo {
    class Brand(val name: String, val color: Int)

    private fun b(name: String, rgb: Int) = Brand(name, 0xFF000000.toInt() or rgb)

    /** Well-known sites, by registrable domain. Colours are the brands' usual colours, used for the letter badge. */
    private val BRANDS: Map<String, Brand> = buildMap {
        fun add(domains: String, name: String, rgb: Int) = domains.split(' ').forEach { put(it, b(name, rgb)) }
        add("amazon.com amazon.in amazon.co.uk amazon.de amazon.ca amazon.co.jp amazon.com.au", "Amazon", 0xFF9900)
        add("facebook.com fb.com", "Facebook", 0x1877F2)
        add("instagram.com", "Instagram", 0xE4405F)
        add("google.com google.co.in", "Google", 0x4285F4)
        add("youtube.com", "YouTube", 0xFF0000)
        add("x.com twitter.com", "X (Twitter)", 0x14171A)
        add("github.com", "GitHub", 0x24292F)
        add("gitlab.com", "GitLab", 0xFC6D26)
        add("linkedin.com", "LinkedIn", 0x0A66C2)
        add("microsoft.com live.com microsoftonline.com", "Microsoft", 0x00A4EF)
        add("outlook.com", "Outlook", 0x0078D4)
        add("apple.com", "Apple", 0x555555)
        add("icloud.com", "iCloud", 0x3693F3)
        add("netflix.com", "Netflix", 0xE50914)
        add("spotify.com", "Spotify", 0x1DB954)
        add("paypal.com", "PayPal", 0x003087)
        add("ebay.com", "eBay", 0xE53238)
        add("flipkart.com", "Flipkart", 0x2874F0)
        add("paytm.com", "Paytm", 0x00BAF2)
        add("phonepe.com", "PhonePe", 0x5F259F)
        add("whatsapp.com", "WhatsApp", 0x25D366)
        add("telegram.org", "Telegram", 0x26A5E4)
        add("discord.com", "Discord", 0x5865F2)
        add("reddit.com", "Reddit", 0xFF4500)
        add("pinterest.com", "Pinterest", 0xE60023)
        add("tiktok.com", "TikTok", 0x010101)
        add("twitch.tv", "Twitch", 0x9146FF)
        add("dropbox.com", "Dropbox", 0x0061FF)
        add("adobe.com", "Adobe", 0xFA0F00)
        add("slack.com", "Slack", 0x4A154B)
        add("zoom.us", "Zoom", 0x2D8CFF)
        add("yahoo.com", "Yahoo", 0x6001D2)
        add("wikipedia.org", "Wikipedia", 0x636466)
        add("stackoverflow.com", "Stack Overflow", 0xF48024)
        add("cloudflare.com", "Cloudflare", 0xF38020)
        add("digitalocean.com", "DigitalOcean", 0x0080FF)
        add("vercel.com", "Vercel", 0x000000)
        add("netlify.com", "Netlify", 0x00A89A)
        add("notion.so", "Notion", 0x000000)
        add("figma.com", "Figma", 0xF24E1E)
        add("canva.com", "Canva", 0x00A6B2)
        add("uber.com", "Uber", 0x000000)
        add("swiggy.com", "Swiggy", 0xFC8019)
        add("zomato.com", "Zomato", 0xE23744)
        add("myntra.com", "Myntra", 0xFF3F6C)
        add("hotstar.com", "Hotstar", 0x1F80E0)
        add("airtel.in", "Airtel", 0xE40000)
        add("jio.com", "Jio", 0x0F3CC9)
        add("sbi.co.in", "SBI", 0x22409A)
        add("hdfcbank.com", "HDFC Bank", 0x004C8F)
        add("icicibank.com", "ICICI Bank", 0xB02A30)
        add("npmjs.com", "npm", 0xCB3837)
        add("openai.com", "OpenAI", 0x10A37F)
        add("anthropic.com claude.ai", "Claude", 0xD97757)
    }

    /** The website's registrable domain ("login.amazon.in" -> "amazon.in"), or null if there is no usable address. */
    fun domainOf(url: String): String? {
        val host = DomainMatcher.hostOf(url) ?: return null
        if (!host.contains('.')) return null
        if (host.all { it.isDigit() || it == '.' }) return host // IP address
        return DomainMatcher.registrable(host)
    }

    /**
     * True once a typed address looks finished ("amazon.com"), false while it is still being typed ("amaz",
     * "amazon.", "amazon.c"). A website icon is only asked for when this is true, so half-typed names are not sent
     * to the icon service.
     */
    fun looksComplete(url: String): Boolean {
        val domain = domainOf(url) ?: return false
        if (domain.all { it.isDigit() || it == '.' }) return domain.split('.').size == 4
        val tld = domain.substringAfterLast('.')
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    fun brandFor(url: String): Brand? = domainOf(url)?.let { BRANDS[it] }

    /**
     * The name to show. The title the user typed wins; if there is none (or it is just the site's address, as
     * browsers export it), the name comes from the website, then the username, then "Untitled".
     */
    fun displayName(title: String, url: String, username: String): String {
        val typed = title.trim()
        val domain = domainOf(url)
        val typedIsJustTheAddress = typed.isNotEmpty() && domain != null &&
            DomainMatcher.hostOf(typed)?.let { DomainMatcher.registrable(it) } == domain && typed.contains('.')
        if (typed.isNotEmpty() && !typedIsJustTheAddress) return typed
        if (domain != null) return BRANDS[domain]?.name ?: prettyName(domain)
        if (typed.isNotEmpty()) return typed
        return username.trim().ifEmpty { "Untitled" }
    }

    /** "my-bank.co.uk" -> "My Bank". */
    fun prettyName(domain: String): String {
        val first = domain.substringBefore('.')
        if (first.isEmpty()) return domain
        return first.split('-', '_').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            .ifEmpty { domain }
    }

    /** The letter shown in the badge. */
    fun letter(displayName: String): String = displayName.trim().take(1).uppercase().ifEmpty { "?" }
}
