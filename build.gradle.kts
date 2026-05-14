/*
 * Copyright © 2026 Dezz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id("com.android.application") version "8.7.2" apply false
}

/**
 * Version name comes from the `VERSION_NAME` environment variable (e.g.
 * `v1.2.3` or `v1.2.3-beta`). When not set, falls back to `v1.0.0`.
 */
fun resolveVersionName(): String {
    val env = System.getenv("VERSION_NAME")
    if (!env.isNullOrBlank()) return env
    logger.warn("Environment variable `VERSION_NAME` is not set, using default version")
    return "v1.0.0"
}

/**
 * versionCode is derived from versionName: vMAJOR.MINOR.PATCH → MAJOR*10000 + MINOR*100 + PATCH.
 * Suffixes like `-beta` are stripped before parsing.
 */
fun resolveVersionCode(versionName: String): Int {
    val clean = versionName.removePrefix("v").replace(Regex("-.*$"), "")
    val parts = clean.split(".").map { it.toIntOrNull() ?: 0 }
    val major = parts.getOrNull(0) ?: 0
    val minor = parts.getOrNull(1) ?: 0
    val patch = parts.getOrNull(2) ?: 0
    return major * 10000 + minor * 100 + patch
}

val appVersionName: String = resolveVersionName()
val appVersionCode: Int = resolveVersionCode(appVersionName)

extra["appVersionName"] = appVersionName
extra["appVersionCode"] = appVersionCode

println("Building version: $appVersionName ($appVersionCode)")
