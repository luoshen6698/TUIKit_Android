package io.trtc.tuikit.chat.demo.xingdun.launch

import java.net.IDN
import java.net.URI

enum class XingDunEnterpriseLookupMode {
    COMPANY_CODE,
    DOMAIN
}

enum class XingDunEnterpriseInputError {
    COMPANY_CODE_REQUIRED,
    COMPANY_CODE_INVALID,
    DOMAIN_REQUIRED,
    DOMAIN_INVALID
}

data class XingDunEnterpriseLookup(
    val companyCode: String? = null,
    val domain: String? = null
)

object XingDunEnterpriseInputValidator {

    fun resolve(
        mode: XingDunEnterpriseLookupMode,
        companyCode: String,
        domain: String
    ): Result<XingDunEnterpriseLookup> = when (mode) {
        XingDunEnterpriseLookupMode.COMPANY_CODE -> resolveCompanyCode(companyCode)
        XingDunEnterpriseLookupMode.DOMAIN -> resolveDomain(domain)
    }

    fun normalizedDomain(value: String): String {
        var normalized = value.trim().lowercase()
        val schemeIndex = normalized.indexOf("://")
        if (schemeIndex >= 0) {
            normalized = normalized.substring(schemeIndex + 3)
        }
        normalized = normalized.substringBefore('/').trimEnd('/')
        return normalized
    }

    private fun resolveCompanyCode(value: String): Result<XingDunEnterpriseLookup> {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return failure(XingDunEnterpriseInputError.COMPANY_CODE_REQUIRED)
        if (normalized.length !in 4..20 || !normalized.all(Char::isLetterOrDigit)) {
            return failure(XingDunEnterpriseInputError.COMPANY_CODE_INVALID)
        }
        return Result.success(XingDunEnterpriseLookup(companyCode = normalized))
    }

    private fun resolveDomain(value: String): Result<XingDunEnterpriseLookup> {
        val normalized = normalizedDomain(value)
        if (normalized.isEmpty()) return failure(XingDunEnterpriseInputError.DOMAIN_REQUIRED)
        if (normalized.any { it.isWhitespace() || it == '\n' || it == '\r' }) {
            return failure(XingDunEnterpriseInputError.DOMAIN_INVALID)
        }
        val asciiHost = runCatching { IDN.toASCII(normalized) }.getOrNull()
            ?: return failure(XingDunEnterpriseInputError.DOMAIN_INVALID)
        val host = runCatching { URI("https://$asciiHost").host }.getOrNull()
        if (host.isNullOrBlank() || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) {
            return failure(XingDunEnterpriseInputError.DOMAIN_INVALID)
        }
        return Result.success(XingDunEnterpriseLookup(domain = normalized))
    }

    private fun failure(error: XingDunEnterpriseInputError): Result<XingDunEnterpriseLookup> =
        Result.failure(XingDunEnterpriseInputException(error))
}

class XingDunEnterpriseInputException(
    val error: XingDunEnterpriseInputError
) : IllegalArgumentException(error.name)
