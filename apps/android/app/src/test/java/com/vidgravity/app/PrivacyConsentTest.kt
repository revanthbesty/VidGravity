package com.vidgravity.app

import org.junit.Assert.*
import org.junit.Test

class PrivacyConsentTest {
    @Test fun enablingCollectionRequiresSuccessfulPersistenceFirst() {
        val events=mutableListOf<String>()
        PrivacyConsent.update(PrivacyChoices(true,true),{events.add("persist:$it");true},{events.add("apply:$it")})
        assertTrue(events[0].startsWith("persist:"));assertTrue(events[1].startsWith("apply:"))
    }
    @Test fun diskFailureNeverEnablesCollectionAndWithdrawalNeverRestoresOldConsent() {
        for(request in listOf(PrivacyChoices(true,true),PrivacyChoices())) {
            val applied=mutableListOf<PrivacyChoices>()
            assertThrows(IllegalStateException::class.java) {PrivacyConsent.update(request,{false},{applied.add(it)})}
            assertEquals(listOf(PrivacyChoices()),applied)
        }
    }
    @Test fun providerFailureDisablesCollectorsAndPersistsWithdrawal() {
        val saved=mutableListOf<PrivacyChoices>();val applied=mutableListOf<PrivacyChoices>()
        assertThrows(IllegalStateException::class.java) {
            PrivacyConsent.update(PrivacyChoices(true,true),{saved.add(it);true},{applied.add(it);if(it.analytics) error("provider failed")})
        }
        assertEquals(PrivacyChoices(),saved.last());assertEquals(PrivacyChoices(),applied.last())
    }
}
