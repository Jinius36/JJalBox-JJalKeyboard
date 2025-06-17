package com.myhome.rpgkeyboard

interface KeyboardInterationListener{
    fun onKey(primaryCode: Int, keyCodes: IntArray)
    fun modechange(mode:Int)
}