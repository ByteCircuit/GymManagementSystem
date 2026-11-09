package org.example.t3gymmanagementsystem.presentation.navigation

/** One-shot hints a screen reads once when it opens (set by the Dashboard shortcuts). */
object ScreenIntent {
    private var register = false
    private var attendanceNfc = false

    fun requestRegister() { register = true }
    fun requestAttendanceNfc() { attendanceNfc = true }

    fun takeRegister(): Boolean { val v = register; register = false; return v }
    fun takeAttendanceNfc(): Boolean { val v = attendanceNfc; attendanceNfc = false; return v }
}