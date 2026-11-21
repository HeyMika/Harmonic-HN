package com.simon.harmonichackernews.summary

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSummaryAvailabilityPolicyTest {
    @Test
    fun explicitDisableWinsOverAvailableProviders() {
        assertFalse(
            AiSummaryAvailabilityPolicy.canProvideSummary(
                explicitlyEnabled = false,
                mode = AiSummaryAvailabilityPolicy.MODE_LOCAL,
                localAvailable = true,
                cloudCredentialAvailable = true,
            ),
        )
    }

    @Test
    fun selectedModeMustHaveItsOwnConfiguration() {
        assertFalse(
            AiSummaryAvailabilityPolicy.canProvideSummary(
                explicitlyEnabled = true,
                mode = AiSummaryAvailabilityPolicy.MODE_LOCAL,
                localAvailable = false,
                cloudCredentialAvailable = true,
            ),
        )
        assertTrue(
            AiSummaryAvailabilityPolicy.canProvideSummary(
                explicitlyEnabled = null,
                mode = "cloud",
                localAvailable = false,
                cloudCredentialAvailable = true,
            ),
        )
    }

    @Test
    fun defaultEnabledStateFollowsAnyConfiguredProvider() {
        assertTrue(
            AiSummaryAvailabilityPolicy.isEnabled(
                explicitlyEnabled = null,
                localAvailable = true,
                cloudCredentialAvailable = false,
            ),
        )
        assertFalse(
            AiSummaryAvailabilityPolicy.isEnabled(
                explicitlyEnabled = null,
                localAvailable = false,
                cloudCredentialAvailable = false,
            ),
        )
    }

    @Test
    fun automaticSummariesRequireAnUnsummarizedArticleAndReadyProvider() {
        assertTrue(
            AiSummaryAvailabilityPolicy.shouldAutomaticallySummarize(
                automaticSummariesEnabled = true,
                requestAlreadyMade = false,
                isArticle = true,
                hasSuccessfulSummary = false,
                canProvideSummary = true,
            ),
        )
        assertFalse(
            AiSummaryAvailabilityPolicy.shouldAutomaticallySummarize(
                automaticSummariesEnabled = true,
                requestAlreadyMade = false,
                isArticle = true,
                hasSuccessfulSummary = true,
                canProvideSummary = true,
            ),
        )
        assertFalse(
            AiSummaryAvailabilityPolicy.shouldAutomaticallySummarize(
                automaticSummariesEnabled = true,
                requestAlreadyMade = false,
                isArticle = true,
                hasSuccessfulSummary = false,
                canProvideSummary = false,
            ),
        )
    }
}
