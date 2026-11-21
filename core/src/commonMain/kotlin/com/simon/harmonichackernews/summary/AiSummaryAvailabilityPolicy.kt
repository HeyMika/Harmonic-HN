package com.simon.harmonichackernews.summary

object AiSummaryAvailabilityPolicy {
    const val MODE_LOCAL = "local"

    fun canProvideSummary(
        explicitlyEnabled: Boolean?,
        mode: String,
        localAvailable: Boolean,
        cloudCredentialAvailable: Boolean,
    ): Boolean {
        if (explicitlyEnabled == false) return false
        return if (mode == MODE_LOCAL) localAvailable else cloudCredentialAvailable
    }

    fun isEnabled(
        explicitlyEnabled: Boolean?,
        localAvailable: Boolean,
        cloudCredentialAvailable: Boolean,
    ): Boolean = explicitlyEnabled ?: (localAvailable || cloudCredentialAvailable)

    fun shouldAutomaticallySummarize(
        automaticSummariesEnabled: Boolean,
        requestAlreadyMade: Boolean,
        isArticle: Boolean,
        hasSuccessfulSummary: Boolean,
        canProvideSummary: Boolean,
    ): Boolean = automaticSummariesEnabled && !requestAlreadyMade && isArticle &&
        !hasSuccessfulSummary && canProvideSummary
}
