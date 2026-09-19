package com.coursetrace.app.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import com.coursetrace.app.CourseTraceApplication

class StudyBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val appContext = requireNotNull(context).applicationContext
        enforceAuthorizedCaller(appContext.packageManager)
        if (URI_MATCHER.match(uri) != BUSY_WINDOWS) {
            throw IllegalArgumentException("Unsupported CourseTrace bridge URI")
        }
        if (selection != null || selectionArgs != null) {
            throw IllegalArgumentException("Selections are not supported")
        }
        if (uri.queryParameterNames != setOf("date") || uri.getQueryParameters("date").size != 1) {
            throw IllegalArgumentException("Exactly one date query parameter is required")
        }

        val columns = StudyBridgeContract.validateProjection(projection)
        val date = StudyBridgeContract.parseDate(uri.getQueryParameter("date"))
        val app = appContext as? CourseTraceApplication
            ?: error("CourseTrace application is unavailable")
        val windows = StudyBridgeContract.busyWindows(app.repository.state.value, date)
        return MatrixCursor(columns.toTypedArray(), windows.size).apply {
            windows.forEach { window ->
                addRow(columns.map { column ->
                    when (column) {
                        StudyBridgeContract.COLUMN_START_EPOCH -> window.startEpochMillis
                        StudyBridgeContract.COLUMN_END_EPOCH -> window.endEpochMillis
                        else -> error("Unexpected bridge column")
                    }
                }.toTypedArray())
            }
            setNotificationUri(appContext.contentResolver, BUSY_WINDOWS_URI)
        }
    }

    override fun getType(uri: Uri): String {
        if (URI_MATCHER.match(uri) != BUSY_WINDOWS) {
            throw IllegalArgumentException("Unsupported CourseTrace bridge URI")
        }
        return "vnd.android.cursor.dir/vnd.${StudyBridgeContract.AUTHORITY}.busy-window"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("CourseTrace study bridge is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("CourseTrace study bridge is read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("CourseTrace study bridge is read-only")

    private fun enforceAuthorizedCaller(packageManager: PackageManager) {
        val callingUid = Binder.getCallingUid()
        val identity = Binder.clearCallingIdentity()
        try {
            val packages = packageManager.getPackagesForUid(callingUid)?.toList()
            val packageInfo = runCatching { packageInfo(packageManager, StudyBridgeAccessPolicy.ALLOWED_PACKAGE) }
                .getOrElse { throw SecurityException("CourseTrace bridge caller is not installed", it) }
            if (packageInfo.applicationInfo?.uid != callingUid) {
                throw SecurityException("CourseTrace bridge caller UID does not match")
            }
            val signingInfo = packageInfo.signingInfo
                ?: throw SecurityException("CourseTrace bridge caller has no signing information")
            val signatures = buildList {
                addAll(signingInfo.apkContentsSigners.orEmpty())
                addAll(signingInfo.signingCertificateHistory.orEmpty())
            }
            val digests = signatures.map { StudyBridgeAccessPolicy.sha256(it.toByteArray()) }.toSet()
            if (!StudyBridgeAccessPolicy.isCallerAllowed(packages, digests)) {
                throw SecurityException("CourseTrace bridge caller is not authorized")
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageManager: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    companion object {
        private const val BUSY_WINDOWS = 1
        private val BUSY_WINDOWS_URI = Uri.parse(StudyBridgeContract.BUSY_WINDOWS_URI_STRING)
        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(StudyBridgeContract.AUTHORITY, StudyBridgeContract.PATH_BUSY_WINDOWS, BUSY_WINDOWS)
        }
    }
}
