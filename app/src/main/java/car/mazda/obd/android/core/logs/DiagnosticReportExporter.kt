package car.mazda.obd.android.core.logs

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import car.mazda.obd.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportExporter {
    fun share(context: Context, entries: List<AppLogger.Entry>) {
        val exportDirectory = File(context.cacheDir, "diagnostic_exports").apply { mkdirs() }
        exportDirectory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val reportFile = File(exportDirectory, "mazda-obd-diagnostics-$timestamp.txt")
        reportFile.writeText(AppLogger.buildReport(entries, buildAppInfo()))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mazda OBD diagnostic report")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
    }

    private fun buildAppInfo(): String =
        "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.FLAVOR}\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
