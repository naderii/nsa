# Add project-specific ProGuard/R8 rules here as the app grows.
# https://developer.android.com/build/shrink-code

# Room entities are read via reflection by the generated code; keep field names.
-keep class ir.naderinia.nsa.data.** { *; }

# Kotlin coroutines / Room / Compose ship their own consumer-rules.pro files
# bundled in their AARs, so R8 already knows about them — nothing extra needed
# here for those. The rules above are only for our own data classes, which R8
# can't infer are accessed via Room's reflection-based mapping.
