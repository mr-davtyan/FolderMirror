package net.davtyan.foldermirror

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import net.davtyan.foldermirror.fileslist.FilesList

class WorkerCompare(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val context = appContext
    private val myNotification = Notification(context)

    private var currentFilesCount: Int = 0
    private var totalFilesCount: Int = 0

    private var lastProgressReportedMillis: Long = 0
    private var lastProgressReportedTimeout: Long = 100

    override suspend fun doWork(): Result {

        mySetProgress(
            -1.0,
            context.getString(R.string.text_initializing),
            "",
            true
        )
        myNotification.buildNotificationIndeterminate(context.getString(R.string.text_comparing_folder))

        totalFilesCount = countFilesInFolder(
            DocumentFile.fromTreeUri(
                context,
                mySourceFolder.myFolderPath
            ), 0
        )
        totalFilesCount += countFilesInFolder(
            DocumentFile.fromTreeUri(
                context,
                myTargetFolder.myFolderPath
            ), 0
        )

        getMyFileList(switchWipeTargetFolder.isChecked, mySourceFolder)
        getMyFileList(switchWipeTargetFolder.isChecked, myTargetFolder)

        if (isWorkCancelled()) return Result.failure()

        currentFilesCount = 0

        myFilesToDelete = compareFileArrays(myTargetFolder, mySourceFolder, "Files to delete")
        myFilesToCopy = compareFileArrays(mySourceFolder, myTargetFolder, "Files to copy")

        mySetProgress(
            -1.0,
            context.getString(R.string.text_comparing_folder),
            "",
            true
        )

        if (!isWorkCancelled()) {
            var myContentTitle = context.getString(R.string.text_folders_are_synced)

            resultMessage = ""

            if (myFilesToCopy.myFiles.isNotEmpty()) {
                myContentTitle = context.getString(R.string.text_folders_are_compared)
                resultMessage = resultMessage
                    .plus(myFilesToCopy.myTotalFilesCount.toString())
                    .plus(" file(s) to be copied (")
                    .plus(myFilesToCopy.convertFileSize(myFilesToCopy.myTotalFilesSize))
                    .plus(")")
                    .plus(
                        if (myFilesToDelete.myFiles.isNotEmpty()) "\n"
                        else ""
                    )
            }

            if (myFilesToDelete.myFiles.isNotEmpty()) {
                myContentTitle = context.getString(R.string.text_folders_are_compared)
                resultMessage = resultMessage
                    .plus(myFilesToDelete.myTotalFilesCount.toString())
                    .plus(" file(s) to be deleted (")
                    .plus(myFilesToCopy.convertFileSize(myFilesToDelete.myTotalFilesSize))
                    .plus(")")
            }

            myNotification.buildNotificationStatic(myContentTitle)
        } else {
            myFilesToDelete.myFiles.clear()
            myFilesToCopy.myFiles.clear()
            return Result.failure()
        }
        return Result.success()
    }

    private fun countFilesInFolder(folder: DocumentFile?, totalFilesStartCount: Int): Int {
        var totalFiles = totalFilesStartCount
        if (isWorkCancelled()) return 0
        if (folder != null && folder.isDirectory) {
            totalFiles += folder.listFiles().size
            folder.listFiles().forEach {
                totalFiles = +countFilesInFolder(it, totalFiles)
            }
        }
        return totalFiles
    }

    private fun isWorkCancelled(): Boolean {
        if (this.isStopped) {
//            Log.w("WorkCompare", "Stopped")
            myNotification.cancelAllNotifications()
            return true
        }
        return false
    }

    private fun mySetProgress(
        WorkerSyncProgressPercent: Double,
        WorkerSyncProgressTitle: String,
        WorkerSyncProgressBody: String,
        immediatelyUpdate: Boolean = false
    ) {
        if (immediatelyUpdate || (System.currentTimeMillis() - lastProgressReportedMillis) > lastProgressReportedTimeout) {
            setProgressAsync(
                workDataOf(
                    WORKER_SYNC_PROGRESS_PERCENT to WorkerSyncProgressPercent.toInt(),
                    WORKER_SYNC_PROGRESS_TITLE to WorkerSyncProgressTitle,
                    WORKER_SYNC_PROGRESS_BODY to WorkerSyncProgressBody
                )
            )
            lastProgressReportedMillis = System.currentTimeMillis()
        }
    }

    private fun compareFileArrays(
        sourceFolder: Folder,
        targetFolder: Folder,
        title: String
    ): FilesList {

        val myFilesArray = FilesList(title)

        myNotification.buildNotificationIndeterminate(context.getString(R.string.text_comparing_folder))

        sourceFolder.myFileList.forEach {

            mySetProgress(
                (100.0 * currentFilesCount / totalFilesCount),
                context.getString(R.string.text_comparing_folder),
                it.value.myFileNameShort
            )
            currentFilesCount++

            if (!isWorkCancelled()) {
                if (it.key !in targetFolder.myFileList) {
                    myFilesArray.myFiles.add(it.value)
                    myFilesArray.myFilesNames.add(it.value.myFileName)
                    myFilesArray.myFilesNamesShort.add(it.value.myFileNameShort)
                    myFilesArray.myFilesNamesFull.add(Uri.decode(it.value.myFile.uri.lastPathSegment.toString()))
                    myFilesArray.myFilesSizes.add(
                        if (it.value.myFile.isDirectory) "Directory" else myFilesToCopy.convertFileSize(
                            it.value.myFile.length()
                        )
                    )
                    myFilesArray.myTotalFilesSize += it.value.myFile.length()
                    myFilesArray.myTotalFilesCount += 1
                }
            } else {
                return FilesList("null")
            }

        }
        return myFilesArray
    }

    private fun getMyFileList(wipeTargetFolder: Boolean, folder: Folder) {
        folder.myFileList.clear()
        val myDocumentFileRoot = DocumentFile.fromTreeUri(context, folder.myFolderPath)
        if (myDocumentFileRoot != null && !isWorkCancelled()) {
            myNotification.buildNotificationIndeterminate(context.getString(R.string.text_comparing_folder))
            getMyFileListFolders(myDocumentFileRoot, wipeTargetFolder, "", folder)
        }
    }

    private fun getMyFileListFolders(
        myDocumentFileRoot: DocumentFile,
        wipeTargetFolder: Boolean,
        parentFolderName: String,
        folder: Folder
    ) {
        myDocumentFileRoot.listFiles().forEach {
            if (isWorkCancelled()) {
                folder.myFileList.clear()
                return
            }

            currentFilesCount++

            val myFileNameShort: String = it.name.toString()
            val myFileName: String = parentFolderName + myFileNameShort

            mySetProgress(
                (100.0 * currentFilesCount / totalFilesCount),
                context.getString(R.string.text_comparing_folder_collecting_data),
                myFileNameShort
            )

            if (it.isDirectory) {
                folder.myFileList[parentFolderName + myFileNameShort] = FileParameters(
                    it,
                    myFileName,
                    myFileNameShort,
                    ""
                )

                getMyFileListFolders(
                    it, wipeTargetFolder,
                    "$parentFolderName$myFileNameShort/",
                    folder
                )

            } else {
                val myKey: String = if (wipeTargetFolder) {
                    parentFolderName + myFileNameShort + it.length()
                        .toString() + (0..999999999999).random().toString()
                } else {
                    parentFolderName + myFileNameShort + it.length().toString()
                }

                folder.myFileList[myKey] = FileParameters(
                    it,
                    myFileName,
                    myFileNameShort,
                    parentFolderName + myFileNameShort + it.length().toString()
                )
            }

        }
    }

}

