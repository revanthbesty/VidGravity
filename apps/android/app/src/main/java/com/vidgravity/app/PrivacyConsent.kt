package com.vidgravity.app

data class PrivacyChoices(val analytics:Boolean=false,val crashes:Boolean=false)

/** Persist before enabling SDK collection. Any failure attempts to disable both collectors. */
internal object PrivacyConsent {
    fun update(value:PrivacyChoices,persist:(PrivacyChoices)->Boolean,apply:(PrivacyChoices)->Unit) {
        try {
            check(persist(value)) {"Privacy preference could not be saved; collection was disabled for this session."}
            apply(value)
        } catch(failure:Exception) {
            // Withdrawal must never re-enable the previous consent after a disk/SDK failure.
            // Permanent disk failure cannot guarantee persistence across process death.
            try {apply(PrivacyChoices())} catch(disableFailure:Exception) {failure.addSuppressed(disableFailure)}
            try {persist(PrivacyChoices())} catch(storageFailure:Exception) {failure.addSuppressed(storageFailure)}
            throw failure
        }
    }
}
