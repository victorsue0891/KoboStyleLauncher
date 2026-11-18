package org.kobostyle.launcher.platform

fun interface EinkRefreshController {
    fun contentChanged()
    companion object { val NONE = EinkRefreshController { /* Standard Android has no E Ink refresh API. */ } }
}
