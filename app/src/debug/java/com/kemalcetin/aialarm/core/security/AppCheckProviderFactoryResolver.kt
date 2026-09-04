package com.kemalcetin.aialarm.core.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG build variant resolver. Debug builds use the App Check Debug provider so
 * a locally-installed debug APK can attest to the development backend. The debug
 * provider is a [debugImplementation] dependency and this class lives only in the
 * `debug` source set, so it can NEVER appear in a release APK/AAB (release always
 * resolves [PlayIntegrityAppCheckProviderFactory]).
 */
object AppCheckProviderFactoryResolver {
    fun resolve(): AppCheckProviderFactory =
        DebugAppCheckProviderFactory.getInstance()
}
