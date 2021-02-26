package net.davtyan.foldermirror

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

//var myTotalFileSize: Long = 0

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class WorkerSync(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val context = appContext
    private var myTotalFileSizeLive: Long = 0
    private var myTotalFileSizeLivePercentLast: Int = 0
    private var myTotalFileSizePercent: Int = 0
    private var myCurrentFileName = ""
    private val myNotification = Notification(context)
    private var myFileListToCopy = mutableMapOf<String, DocumentFile?>()
    private var lastProgressReportedMillis: Long = 0
    private var lastProgressReportedTimeout: Long = 100

    override suspend fun doWork(): Result {

        myNotification.buildNotificationIndeterminate((context.getString(R.string.text_preparing)))

        var allFilesDeleted = false
        while (!allFilesDeleted) {
            var countIteration = 0
            allFilesDeleted = true
            myFilesToDelete.myFiles.forEach {
                if (isWorkCancelled()) return Result.failure()
                mySetProgress(
                    (100.0 * countIteration / (myFilesToDelete.myTotalFilesCount)).toInt(),
                    "Cleaning",
                    it.myFileNameShort
                )
                allFilesDeleted = !it.myFile.delete()
                countIteration++
            }
        }

        mySetProgress(-1, "Data integrity check", "", true)

        myFileListToCopy.clear()

        createFolderTree(
            DocumentFile.fromTreeUri(context, mySourceFolder.myFolderPath),
            DocumentFile.fromTreeUri(context, myTargetFolder.myFolderPath)
        )

        copyFiles()

        if (isWorkCancelled()) {
            return Result.failure()
        }

        myNotification.buildNotificationStatic(context.getString(R.string.text_folders_have_been_synced))
        return Result.success()
    }

    private fun createFolder(parentFolder: DocumentFile?, folderName: String): DocumentFile? {

        myCurrentFileName = folderName

        mySetProgress(-1, "Checking folders structure", myCurrentFileName)

        if (parentFolder == null) return null
        val dir = DocumentFile.fromTreeUri(context, parentFolder.uri)?.findFile(folderName)

        return if (dir?.isDirectory == true) {
            dir
        } else {
            DocumentFile.fromTreeUri(context, parentFolder.uri)?.createDirectory(folderName)
        }
    }

    private fun copyFile(
        parentFolder: DocumentFile?,
        fileName: String,
        file: DocumentFile
    ): DocumentFile? {

        myCurrentFileName = fileName

        var myContentTitle: String = ("Copying Files")
            .plus("\n(")
            .plus(myFilesToCopy.convertFileSize(myTotalFileSizeLive))
            .plus(" of ")
            .plus(myFilesToCopy.convertFileSize(myFilesToCopy.myTotalFilesSize))
            .plus(")")


        mySetProgress(myTotalFileSizePercent, myContentTitle, myCurrentFileName)

        if (parentFolder == null) return null

        val destUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentFolder.uri,
            file.type.toString(),
            fileName
        )

        try {
            val `is`: InputStream? =
                context.contentResolver.openInputStream(file.uri)

            val os: OutputStream? =
                context.contentResolver.openOutputStream(destUri, "w")

            val buffer = if (file.length() > 500000) {
                ByteArray(524288)
            } else {
                ByteArray(8192)
            }

            var length: Int
            while (`is`!!.read(buffer).also { length = it } > 0) {
                os!!.write(buffer, 0, length)
                myTotalFileSizeLive += buffer.size

                if (isWorkCancelled()) break

                myTotalFileSizePercent =
                    (100.0 * myTotalFileSizeLive / myFilesToCopy.myTotalFilesSize).toInt()


                myContentTitle = ("Copying Files")
                    .plus("\n(")
                    .plus(myFilesToCopy.convertFileSize(myTotalFileSizeLive))
                    .plus(" of ")
                    .plus(myFilesToCopy.convertFileSize(myFilesToCopy.myTotalFilesSize))
                    .plus(")")

                mySetProgress(myTotalFileSizePercent, myContentTitle, myCurrentFileName)

                if ((myTotalFileSizePercent - myTotalFileSizeLivePercentLast) > 0) {
                    myTotalFileSizeLivePercentLast = myTotalFileSizePercent
                    myNotification.buildNotificationProgress(
                        myTotalFileSizePercent.toString() +
                                (context.getString(R.string.text_percent_of)) + " " +
                                myFilesToCopy.convertFileSize(myFilesToCopy.myTotalFilesSize),
                        myCurrentFileName,
                        myTotalFileSizePercent
                    )
                }

            }
            `is`.close()
            os!!.flush()
            os.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun mySetProgress(
        WorkerSyncProgressPercent: Int,
        WorkerSyncProgressTitle: String,
        WorkerSyncProgressBody: String,
        immediatelyUpdate: Boolean = false
    ) {
        if (immediatelyUpdate || (System.currentTimeMillis() - lastProgressReportedMillis) > lastProgressReportedTimeout) {
            setProgressAsync(
                workDataOf(
                    WORKER_SYNC_PROGRESS_PERCENT to WorkerSyncProgressPercent,
                    WORKER_SYNC_PROGRESS_TITLE to WorkerSyncProgressTitle,
                    WORKER_SYNC_PROGRESS_BODY to WorkerSyncProgressBody
                )
            )
            lastProgressReportedMillis = System.currentTimeMillis()
        }
    }

    private fun createFolderTree(sourceFolder: DocumentFile?, targetFolder: DocumentFile?): Result {
        sourceFolder?.listFiles()?.forEach { it ->
            if (isWorkCancelled()) return Result.failure()
            if (it.isDirectory) {
                createFolderTree(it, createFolder(targetFolder, it.name.toString()))
            } else {
                collectFiles(it, targetFolder)
            }
        }
        return Result.success()
    }


    private fun collectFiles(
        file: DocumentFile, targetFolder: DocumentFile?
    ): DocumentFile? {
        val myFileNameShort: String = file.name.toString()
        val myTargetPath =
            Uri.decode(mySourceFolder.myFolderPath.lastPathSegment.toString()).plus("/")
        val parentFolderName = Uri.decode(file.uri.lastPathSegment.toString())
            .replaceFirst(myTargetPath, "")
            .dropLast(myFileNameShort.length)

        val myKey: String = parentFolderName + myFileNameShort + file.length().toString()

        myFileListToCopy[myKey] = targetFolder
        return null
    }

    private fun copyFiles() {
        myFilesToCopy.myFiles.forEach {
            if (isWorkCancelled()) return
            val dir: DocumentFile? = myFileListToCopy[it.mKey]
            copyFile(dir, it.myFileNameShort, it.myFile)
        }
    }

    private fun isWorkCancelled(): Boolean {
        if (this.isStopped) {
            myNotification.cancelAllNotifications()
            return true
        }
        return false
    }

}