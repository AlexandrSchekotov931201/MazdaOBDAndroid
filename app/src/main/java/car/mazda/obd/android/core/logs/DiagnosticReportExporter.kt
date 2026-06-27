package car.mazda.obd.android.core.logs

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportExporter {
    fun share(context: Context, entries: List<AppLogger.Entry>, appInfo: String) {
        val exportDirectory = File(context.cacheDir, "diagnostic_exports").apply { mkdirs() }
        exportDirectory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val reportFile = File(exportDirectory, "mazda-obd-diagnostics-$timestamp.txt")
        reportFile.writeText(AppLogger.buildReport(entries, appInfo))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mazda OBD diagnostic report")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
    }
}
