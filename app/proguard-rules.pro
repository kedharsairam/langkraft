# R8 / ProGuard rules for the release build.
#
# This file is deliberately EMPTY of rules.
#
# It is not empty by oversight, and it must stay empty until something genuinely
# requires otherwise. `build.gradle.kts` turns R8 on for release
# (`isMinifyEnabled = true`) alongside the default
# `proguard-android-optimize.txt`, and that is enough on its own: the Android
# Gradle Plugin merges in the consumer rules that every AndroidX, Compose and
# Navigation AAR already ships, so the libraries are covered without a single
# line here.
#
# WHY NO KEEP RULES ARE NEEDED — the three ways a shrinking build normally
# breaks this app, each checked against the source rather than assumed:
#
#  1. Reflection and dynamic class loading. There is none. Grepping the whole
#     main source set for `Class.forName`, `getDeclaredMethod`,
#     `getDeclaredField`, `ClassLoader`, `::class.java` and `.newInstance`
#     returns nothing. There is no annotation processor, no `TypeToken`, and no
#     reflective field access anywhere.
#
#  2. JSON parsing. The corpus is read with `org.json.JSONObject` /
#     `org.json.JSONArray` (ContentRepository), not Gson, Moshi or
#     kotlinx-serialization. That matters, because those three bind by
#     reflection over field names and would need keep rules for every model
#     class, whereas `org.json` is reached entirely through direct static calls
#     (`getJSONObject`, `getString`, `optStringOrNull`, ...) with the keys written
#     out as string literals. Renaming a Kotlin field cannot change a literal.
#     `org.json` is part of the Android platform, not a library on the classpath,
#     so R8 never shrinks it in the first place.
#
#  3. Enums parsed out of JSON by NAME. This is the one that would have bitten,
#     because R8 is free to rename enum constants and a name looked up from an
#     asset string would stop matching. It does not happen here, and it is worth
#     knowing why: `Direction.parse` is a `when` over literal strings
#     ("say"/"understand"), `Domain.of` and the `TierId` companion key off an
#     explicit `number: Int`, and none of them call `Enum.valueOf`. The lookup key
#     is in code, so obfuscation cannot invalidate it.
#
# WHY NOTHING IS NEEDED FOR FONTS OR ASSETS, since resource shrinking is also on:
#
#  - The eight Noto TTFs are referenced as compile-time constants
#     (`R.font.notosansthairegular` and siblings in ScriptFonts.kt), never by
#     `Resources.getIdentifier`. The resource shrinker works from the same
#     constant references, so it can see they are live and keeps them. A font
#     loaded by string name is exactly the kind of thing that would be silently
#     deleted here.
#  - The corpus is opened by literal asset name ("specs.json", "content.jsonl").
#    `isShrinkResources` only reasons about `res/`, and never touches `assets/`,
#    so the JSONL cannot be stripped even though it is the whole point of the app.
#    JSONL also stays compressed; see the note in build.gradle.kts for why adding
#    it to `noCompress` would only inflate the APK.
#
# THE SIGNATURE PROBLEM WITH `Enum.valueOf` AND COMPOSE — a rule that is NOT here
# on purpose, in case someone adds it on reflex.
#
# Compose derives some runtime behaviour from class and file names, and the
# canonical mitigation for a shrunk Compose app is blanket keep rules like
# `-keep class androidx.compose.** { *; }`. They are omitted here on purpose:
# this app was verified on a device with R8 fully enabled, shrinking resources
# included, and the release APK renders all four scripts correctly. Compose's own
# consumer rules (shipped in its AARs) are what make that work. Blanket keeps
# would have hidden a real regression behind an enormous APK, and a keep rule for
# a problem you have not hit is a guess dressed up as knowledge.
#
# IF YOU EVER ADD A KEEP RULE, add it for a named, reproduced failure and say in a
# comment what breaks without it. A rules file that grows by superstition is
# worse than no file at all.