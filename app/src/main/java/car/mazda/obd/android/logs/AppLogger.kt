package car.mazda.obd.android.logs

import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())
        _logs.value += "[$time] $msg"
    }
}