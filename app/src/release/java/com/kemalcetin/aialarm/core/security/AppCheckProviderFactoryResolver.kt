package com.kemalcetin.aialarm.core.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * RELEASE build variant resolver. Release builds always attest via Play Integrity
 * — the debug provider is never referenced here, so it cannot be bundled in the
 * signed APK/AAB. This keeps the App Check enforcement real for deployed users.
 */
object AppCheckProviderFactoryResolver {
    fun resolve(): AppCheckProviderFactory =
        PlayIntegrityAppCheckProviderFactory.getInstance()
}
