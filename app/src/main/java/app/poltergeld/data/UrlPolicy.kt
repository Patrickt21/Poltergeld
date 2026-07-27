package app.poltergeld.data

import java.net.URI

/**
 * Cleartext http:// is fine for a self-hosted instance on the local network
 * but must not carry the security token across the open internet. The
 * manifest allows cleartext globally (Android's network security config
 * cannot express "private IP ranges only"), so this policy enforces it.
 */
object UrlPolicy {

    /** True if the URL is https, or http to a host that is clearly local. */
    fun isCleartextAllowed(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("http", ignoreCase = true)) return true
        val host = uri.host ?: return false
        return isPrivateHost(host)
    }

    fun isPrivateHost(raw: String): Boolean {
        val host = raw.lowercase().removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host == "::1") return true
        // IPv6: loopback handled above; ULA fc00::/7 and link-local fe80::/10.
        if (host.contains(':')) {
            return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")
        }
        // Plain single-label hostnames only resolve on the local network.
        if (!host.contains('.')) return true
        // Common LAN / mDNS suffixes (incl. AVM routers) and the RFC 8375 home domain.
        val lanSuffixes = listOf(".local", ".lan", ".internal", ".home", ".home.arpa", ".fritz.box")
        if (lanSuffixes.any { host.endsWith(it) }) return true
        // Private / loopback / link-local IPv4 ranges.
        val parts = host.split('.')
        if (parts.size == 4) {
            val nums = parts.map { it.toIntOrNull() ?: return false }
            if (nums.any { it !in 0..255 }) return false
            val (a, b) = nums
            return a == 10 || a == 127 || (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) || (a == 169 && b == 254)
        }
        return false
    }
}
