package net.davtyan.foldermirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.google.android.material.snackbar.BaseTransientBottomBar.ANIMATION_MODE_SLIDE
import com.google.android.material.snackbar.Snackbar
import net.davtyan.foldermirror.fileslist.FilesList
import net.davtyan.foldermirror.fileslist.FilesListView
import kotlin.properties.Delegates

var myFilesToDelete: FilesList = FilesList("Files to delete")
var myFilesToCopy: FilesList = FilesList("Files to copy")

lateinit var mySourceFolderPathFromPrefs: Uri
lateinit var myTargetFolderPathFromPrefs: Uri

lateinit var mySourceFolder: Folder
lateinit var myTargetFolder: Folder

lateinit var switchWipeTargetFolder: SwitchCompat

var resultMessage: String = ""

private const val PREFS_FILENAME = "net.davtyan.foldermirror.prefs"
private const val SOURCE_FOLDER_PREFS = "SOURCE_FOLDER_PREFS"
private const val TARGET_FOLDER_PREFS = "TARGET_FOLDER_PREFS"
private const val CHECK_MODIFY_DATE_INT_PREFS = "CHECK_MODIFY_DATE_INT_PREFS"

const val WORKER_SYNC_PROGRESS_PERCENT = "WORKER_SYNC_PROGRESS_PERCENT"
const val WORKER_SYNC_PROGRESS_BODY = "WORKER_SYNC_PROGRESS_BODY"
const val WORKER_SYNC_PROGRESS_TITLE = "WORKER_SYNC_PROGRESS_TITLE"

class MainActivity : AppCompatActivity() {

    private var prefs: SharedPreferences? = null

    private lateinit var buttonCompareFolders: Button
    private lateinit var buttonStartSync: Button
    private lateinit var buttonStopSync: Button

    private lateinit var textSourceFolder: TextView
    private lateinit var textTargetFolder: TextView

    private lateinit var layoutSource: CardView
    private lateinit var layoutTarget: CardView

    private lateinit var layoutSummary: CardView
    private lateinit var textViewSummaryTitle: TextView
    private lateinit var textViewSummaryBody: TextView
    private lateinit var buttonShowFilesToBeDeleted: Button
    private lateinit var buttonShowFilesToBeCopied: Button

    private lateinit var layoutProgressbar: CardView
    private lateinit var layoutStartStop: CardView

    private lateinit var myProgressbarTitle: TextView
    private lateinit var myProgressbar: ProgressBar
    private lateinit var myProgressbarBody: TextView

    private lateinit var copyDelWorkRequest: OneTimeWorkRequest
    private lateinit var compareWorkRequest: OneTimeWorkRequest

    private lateinit var myNotification: Notification

    private val myWorkManager: WorkManager = WorkManager.getInstance(this)

    private var myWipeTargetFolderFromPrefs: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (resources.configuration.orientation == 2) {
            supportActionBar?.hide()
        }

        val filesListIntent = Intent(this, FilesListView()::class.java)

        val snackBarView = findViewById<View>(R.id.mainLayout)

        myNotification = Notification(this)
        myNotification.createNotificationChannel()

        prefs = this.getSharedPreferences(PREFS_FILENAME, 0)

        mySourceFolderPathFromPrefs = Uri.parse(
            prefs!!.getString(SOURCE_FOLDER_PREFS, "").toString()
        )
        myTargetFolderPathFromPrefs = Uri.parse(
            prefs!!.getString(TARGET_FOLDER_PREFS, "").toString()
        )

        myWipeTargetFolderFromPrefs = prefs!!.getBoolean(CHECK_MODIFY_DATE_INT_PREFS, false)

        switchWipeTargetFolder = findViewById(R.id.wipeTargetFolder)
        switchWipeTargetFolder.isChecked = myWipeTargetFolderFromPrefs

        buttonCompareFolders = findViewById(R.id.buttonCompareFolders)
        buttonStartSync = findViewById(R.id.buttonStartSync)
        buttonStopSync = findViewById(R.id.buttonStopSync)

        layoutSource = findViewById(R.id.LayoutSource)
        layoutTarget = findViewById(R.id.LayoutTarget)
        textSourceFolder = findViewById(R.id.pathSource)
        textTargetFolder = findViewById(R.id.pathTarget)

        mySourceFolder = Folder(mySourceFolderPathFromPrefs)
        myTargetFolder = Folder(myTargetFolderPathFromPrefs)

        folderPrefsSaveAndUpdate(myTargetFolder, Uri.EMPTY, TARGET_FOLDER_PREFS, textTargetFolder)
        folderPrefsSaveAndUpdate(mySourceFolder, Uri.EMPTY, SOURCE_FOLDER_PREFS, textSourceFolder)

        if (mySourceFolderPathFromPrefs.toString() != "" && myTargetFolderPathFromPrefs.toString() != "") {
            when (checkIfPathsValid(mySourceFolder, myTargetFolder, false)) {
                1, 4, 5, 6 -> textSourceFolder.text = getString(R.string.hint_source_folder)
                2 -> textTargetFolder.text = getString(R.string.hint_target_folder)
                3 -> {
                    textSourceFolder.text = getString(R.string.hint_source_folder)
                    textTargetFolder.text = getString(R.string.hint_target_folder)
                }
            }
        }

        layoutSummary = findViewById(R.id.LayoutSummary)
        buttonShowFilesToBeDeleted = findViewById(R.id.buttonDetailsDelete)
        buttonShowFilesToBeCopied = findViewById(R.id.buttonDetailsCopy)
        textViewSummaryTitle = findViewById(R.id.textViewSummaryTitle)
        textViewSummaryBody = findViewById(R.id.textViewSummaryBody)

        layoutProgressbar = findViewById(R.id.LayoutProgressbar)
        myProgressbarTitle = findViewById(R.id.textViewProgressBarTitle)
        myProgressbar = findViewById(R.id.progressbar)
        myProgressbarBody = findViewById(R.id.textViewProgressBarBody)
        layoutStartStop = findViewById(R.id.layoutStartStop)

        var currentState: State by Delegates.observable(State.WAIT) { _, _, newValue ->
            Log.w("state changed", newValue.toString())
            when (newValue) {
                State.WAIT -> {
                    buttonShowFilesToBeDeleted.visibility = View.GONE
                    buttonShowFilesToBeCopied.visibility = View.GONE
                    layoutStateWait()
                    enableButtons(true)
                }
                State.COMPARING -> {
                    progressBarIndeterminate("")
                    layoutStateRunning()
                    enableButtons(false)
                }
                State.COMPARED -> {
                    layoutStateWait()
                    enableButtons(true)

                    if (myFilesToDelete.myFiles.isEmpty() && myFilesToCopy.myFiles.isEmpty()) {
                        mySnackbar(snackBarView, getString(R.string.text_folders_are_synced))
                        layoutStartStop.visibility = View.GONE
                        buttonStartSync.visibility = View.GONE
                        layoutSummary.visibility = View.GONE
                    } else {

                        buttonShowFilesToBeDeleted.visibility =
                            if (myFilesToDelete.myFiles.isEmpty()) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }

                        buttonShowFilesToBeCopied.visibility =
                            if (myFilesToCopy.myFiles.isEmpty()) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }

                        layoutStartStop.visibility = View.VISIBLE
                        buttonStartSync.visibility = View.VISIBLE
                        layoutSummary.visibility = View.VISIBLE

                        textViewSummaryTitle.text = getString(R.string.text_comparing_result_title)
                        textViewSummaryBody.text = resultMessage

                    }

                }
                State.SYNC -> {
                    progressBarIndeterminate(getString(R.string.text_preparing))
                    layoutStateRunning()
                    enableButtons(false)
                }
            }
        }

        try {
            if (myWorkManager.getWorkInfosByTag("COMPARE").get().isEmpty()
                && myWorkManager.getWorkInfosByTag("SYNC").get().isEmpty()
            ) {
                currentState = State.WAIT
                cancelEverything()
            }
        } catch (e: Exception) {
            currentState = State.WAIT
            cancelEverything()
        }

        compareWorkRequest =
            OneTimeWorkRequestBuilder<WorkerCompare>()
                .setInputData(
                    workDataOf(
                        "myTargetFolder" to myTargetFolder.myFolderPath.toString(),
                        "mySourceFolder" to mySourceFolder.myFolderPath.toString()
                    )
                )
                .build()

        myWorkManager.getWorkInfosForUniqueWorkLiveData("COMPARE")
            .observe(this, { workInfo ->
                if (workInfo != null && workInfo.size > 0) {
                    Log.w("COMPARE status - ", workInfo[0].state.toString())

                    myProgressbarTitle.text =
                        workInfo[0].progress.getString(WORKER_SYNC_PROGRESS_TITLE)
                    myProgressbarBody.text =
                        workInfo[0].progress.getString(WORKER_SYNC_PROGRESS_BODY)
                    val progressValue =
                        workInfo[0].progress.getInt(WORKER_SYNC_PROGRESS_PERCENT, -1)

                    if (progressValue < 0 || progressValue > 100) {
                        if (!myProgressbar.isIndeterminate) myProgressbar.isIndeterminate = true
                    } else {
                        if (myProgressbar.isIndeterminate) myProgressbar.isIndeterminate = false
                        myProgressbar.progress = progressValue
                    }

                    when (workInfo[0].state) {
                        WorkInfo.State.SUCCEEDED -> {
                            currentState = State.COMPARED
                        }
                        WorkInfo.State.CANCELLED -> {
                            currentState = State.WAIT
                            cancelEverything()
                        }
                        WorkInfo.State.FAILED -> {
                            currentState = State.WAIT
                            mySnackbar(snackBarView, getString(R.string.text_work_failed))
                            myNotification.buildNotificationStatic(getString(R.string.text_work_failed))

                            folderPrefsSaveAndUpdate(
                                myTargetFolder,
                                Uri.EMPTY,
                                TARGET_FOLDER_PREFS,
                                textTargetFolder,
                                true
                            )
                            folderPrefsSaveAndUpdate(
                                mySourceFolder,
                                Uri.EMPTY,
                                SOURCE_FOLDER_PREFS,
                                textSourceFolder,
                                true
                            )
                        }
                    }
                }
            })

        copyDelWorkRequest =
            OneTimeWorkRequestBuilder<WorkerSync>()
                .setInputData(
                    workDataOf(
                        "myTargetFolder" to myTargetFolder.myFolderPath.toString(),
                        "mySourceFolder" to mySourceFolder.myFolderPath.toString()
                    )
                )
                .build()

        myWorkManager.getWorkInfosForUniqueWorkLiveData("SYNC")
            .observe(this, { workInfo ->
                if (workInfo != null && workInfo.size > 0) {
                    Log.w("SYNC status - ", workInfo[0].state.toString())

                    myProgressbarTitle.text =
                        workInfo[0].progress.getString(WORKER_SYNC_PROGRESS_TITLE)
                    myProgressbarBody.text =
                        workInfo[0].progress.getString(WORKER_SYNC_PROGRESS_BODY)
                    val progressValue =
                        workInfo[0].progress.getInt(WORKER_SYNC_PROGRESS_PERCENT, -1)

                    if (progressValue < 0 || progressValue > 100) {
                        if (!myProgressbar.isIndeterminate) myProgressbar.isIndeterminate = true
                    } else {
                        if (myProgressbar.isIndeterminate) myProgressbar.isIndeterminate = false
                        myProgressbar.progress = progressValue
                    }

                    when (workInfo[0].state) {
                        WorkInfo.State.SUCCEEDED -> {
                            currentState = State.WAIT
                            mySnackbar(
                                snackBarView,
                                getString(R.string.text_folders_have_been_synced)
                            )
                        }
                        WorkInfo.State.CANCELLED -> {
                            currentState = State.WAIT
                            cancelEverything()
                        }
                        WorkInfo.State.FAILED -> {
                            folderPrefsSaveAndUpdate(
                                myTargetFolder,
                                Uri.EMPTY,
                                TARGET_FOLDER_PREFS,
                                textTargetFolder,
                                true
                            )
                            folderPrefsSaveAndUpdate(
                                mySourceFolder,
                                Uri.EMPTY,
                                SOURCE_FOLDER_PREFS,
                                textSourceFolder,
                                true
                            )
                            currentState = State.WAIT
                            mySnackbar(snackBarView, getString(R.string.text_work_failed))
                            myNotification.buildNotificationStatic(getString(R.string.text_work_failed))
                        }
                    }

                }
            })

        val intentFileTree = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intentFileTree.flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        switchWipeTargetFolder.setOnCheckedChangeListener { _, isChecked ->
            val editor = prefs!!.edit()
            editor.putBoolean(CHECK_MODIFY_DATE_INT_PREFS, isChecked)
            editor.apply()
            currentState = State.WAIT
            cancelEverything()
        }

        buttonShowFilesToBeDeleted.setOnClickListener {
            filesListIntent.putExtra("delete", true)
            startActivity(filesListIntent)
        }

        buttonShowFilesToBeCopied.setOnClickListener {
            filesListIntent.putExtra("delete", false)
            startActivity(filesListIntent)
        }

        layoutSource.setOnClickListener {
            currentState = State.WAIT
            startActivityForResult(intentFileTree, 1)
            cancelEverything()
        }
        textSourceFolder.setOnClickListener {
            currentState = State.WAIT
            startActivityForResult(intentFileTree, 1)
            cancelEverything()
        }
        layoutTarget.setOnClickListener {
            currentState = State.WAIT
            startActivityForResult(intentFileTree, 2)
            cancelEverything()
        }
        textTargetFolder.setOnClickListener {
            currentState = State.WAIT
            startActivityForResult(intentFileTree, 2)
            cancelEverything()
        }


        buttonCompareFolders.setOnClickListener {
            currentState = State.WAIT
            if (shouldAskPermissions()) {
                askPermissions()
            } else if (checkIfPathsValid(mySourceFolder, myTargetFolder, true) == 0) {
                currentState = State.COMPARING
                myWorkManager.enqueueUniqueWork(
                    "COMPARE",
                    ExistingWorkPolicy.REPLACE,
                    compareWorkRequest
                )
            }
        }

        buttonStartSync.setOnClickListener {
            currentState = State.WAIT
            if (shouldAskPermissions()) {
                askPermissions()
            } else if (checkIfPathsValid(mySourceFolder, myTargetFolder, true) == 0) {
                currentState = State.SYNC
                myWorkManager.enqueueUniqueWork(
                    "SYNC",
                    ExistingWorkPolicy.REPLACE,
                    copyDelWorkRequest
                )
            }
        }

        buttonStopSync.setOnClickListener {
            cancelEverything()
            currentState = State.WAIT
        }
    }

    private fun layoutStateWait() {
        layoutStartStop.visibility = View.GONE
        buttonStartSync.visibility = View.GONE
        buttonStopSync.visibility = View.GONE
        layoutProgressbar.visibility = View.INVISIBLE
        layoutSummary.visibility = View.INVISIBLE
    }

    private fun layoutStateRunning() {
        layoutStartStop.visibility = View.VISIBLE
        buttonStartSync.visibility = View.GONE
        buttonStopSync.visibility = View.VISIBLE
        layoutProgressbar.visibility = View.VISIBLE
        layoutSummary.visibility = View.INVISIBLE
    }

    private fun enableButtons(enable: Boolean) {
        switchWipeTargetFolder.isEnabled = enable
        switchWipeTargetFolder.isClickable = enable
        buttonCompareFolders.isEnabled = enable

        layoutSource.isEnabled = enable
        layoutTarget.isEnabled = enable
        textSourceFolder.isEnabled = enable
        textTargetFolder.isEnabled = enable
    }

    private fun progressBarIndeterminate(title: String) {
        myProgressbarTitle.text = title
        myProgressbarBody.text = ""
        myProgressbar.progress = 0
        myProgressbar.isIndeterminate = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                val directoryUri = resultData?.data ?: return
                contentResolver.takePersistableUriPermission(
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                folderPrefsSaveAndUpdate(
                    mySourceFolder,
                    directoryUri,
                    SOURCE_FOLDER_PREFS,
                    textSourceFolder,
                    true
                )
            }
        }
        if (requestCode == 2) {
            if (resultCode == Activity.RESULT_OK) {
                val directoryUri = resultData?.data ?: return
                contentResolver.takePersistableUriPermission(
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                folderPrefsSaveAndUpdate(
                    myTargetFolder,
                    directoryUri,
                    TARGET_FOLDER_PREFS,
                    textTargetFolder,
                    true
                )
            }
        }
    }

    private fun folderPrefsSaveAndUpdate(
        folder: Folder,
        uri: Uri,
        prefsString: String,
        textViewText: TextView,
        savePrefs: Boolean = false,
    ) {
        if (savePrefs) {
            folder.myFolderPath = uri
            val editor = prefs!!.edit()
            editor.putString(prefsString, uri.toString())
            editor.apply()
        }

        val uriFromPrefs: Uri = Uri.parse(
            prefs!!.getString(prefsString, "").toString()
        )

        textViewText.text = if (uriFromPrefs.lastPathSegment != null) {
            uriFromPrefs.lastPathSegment.toString().replaceBeforeLast(
                ":",
                ""
            ).replace(":", "")
        } else ""
    }


    private var doubleBackToExitPressedOnce = false
    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            cancelEverything()
            super.onBackPressed()
            return
        }
        this.doubleBackToExitPressedOnce = true
        val snackBarView = findViewById<View>(R.id.mainLayout)
        mySnackbar(snackBarView, getString(R.string.message_click_back_again))
        Handler().postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        cancelEverything()
        super.onDestroy()
    }

    private fun cancelEverything() {
        myWorkManager.cancelAllWork()
        myWorkManager.pruneWork()
        myNotification.cancelAllNotifications()
    }

    private fun checkIfPathsValid(
        mySourceFolder: Folder,
        myTargetFolder: Folder,
        showMessage: Boolean
    ): Int {
        var myCode = 0
        var mySourceFolderPath: DocumentFile? = null
        var myTargetFolderPath: DocumentFile? = null

        try {
            mySourceFolderPath = DocumentFile.fromTreeUri(this, mySourceFolder.myFolderPath)
        } catch (e: Exception) {
            myCode = 1
        }

        try {
            myTargetFolderPath = DocumentFile.fromTreeUri(this, myTargetFolder.myFolderPath)
        } catch (e: Exception) {
            myCode = if (myCode == 1) 3
            else 2
        }

        if (myCode == 0) {
            if (!mySourceFolderPath!!.exists() || !mySourceFolderPath.isDirectory) myCode = 1
            if (!myTargetFolderPath!!.exists() || !myTargetFolderPath.isDirectory) myCode = 2
            if (!myTargetFolderPath.isDirectory && !mySourceFolderPath.isDirectory) myCode = 3
            if (!myTargetFolderPath.exists() && !mySourceFolderPath.exists()) myCode = 3

            if (myCode == 0) {
                if (myTargetFolderPath.uri.lastPathSegment.toString()
                        .contains(mySourceFolderPath.uri.lastPathSegment.toString() + "/")
                ) myCode = 4
                if (mySourceFolderPath.uri.lastPathSegment.toString()
                        .contains(myTargetFolderPath.uri.lastPathSegment.toString() + "/")
                ) myCode = 5
                if (mySourceFolderPath.uri.lastPathSegment.toString() == (myTargetFolderPath.uri.lastPathSegment.toString())) myCode =
                    6
            }
        }

        if (showMessage) {
            val snackBarView = findViewById<View>(R.id.mainLayout)

            when (myCode) {
                1 -> mySnackbar(snackBarView, getString(R.string.message_source_is_not_exist))
                2 -> mySnackbar(snackBarView, getString(R.string.message_target_is_not_exist))
                3 -> mySnackbar(
                    snackBarView,
                    getString(R.string.message_source_and_target_is_not_exist)
                )
                4 -> mySnackbar(snackBarView, getString(R.string.message_source_is_parent))
                5 -> mySnackbar(snackBarView, getString(R.string.message_target_is_parent))
                6 -> mySnackbar(snackBarView, getString(R.string.message_source_and_target_same))
            }
        }
        return myCode
    }

    private fun mySnackbar(snackBarView: View, message: String = "") {
        val mySnackbar: Snackbar =
            Snackbar.make(snackBarView, message, Snackbar.LENGTH_SHORT).setAnimationMode(
                ANIMATION_MODE_SLIDE
            )
        val tv =
            mySnackbar.view.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
        tv.setTextColor(ResourcesCompat.getColor(resources, R.color.colorTextSnackbar, null))
        mySnackbar.show()
    }

    private fun shouldAskPermissions(): Boolean {
        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return permission != PackageManager.PERMISSION_GRANTED
    }

    private fun askPermissions() {
        val permissions = arrayOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.USB_PERMISSION",
            "android.permission.MANAGE_DOCUMENTS",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                this, permissions, 1
            )
        }
    }


}
