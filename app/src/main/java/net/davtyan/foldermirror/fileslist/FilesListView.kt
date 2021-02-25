package net.davtyan.foldermirror.fileslist

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.files_list.*
import net.davtyan.foldermirror.R
import net.davtyan.foldermirror.myFilesToCopy
import net.davtyan.foldermirror.myFilesToDelete


class FilesListView : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.files_list)

        val fileListTitle: TextView = findViewById(R.id.listViewTitle)
        val buttonClose: Button = findViewById(R.id.buttonCloseFileList)

        val myFileList = if (intent.getBooleanExtra("delete", false)) {
            myFilesToDelete
        } else {
            myFilesToCopy
        }

        var currentShowState = ShowState.NAME

        fileListTitle.text = myFileList.myTitle
        listView.adapter = FilesListAdapter(this, myFileList.myFilesNames, myFileList.myFilesSizes)

        buttonClose.setOnClickListener {
            finish()
        }

        val myToast: Toast = Toast.makeText(
            this, "",
            Toast.LENGTH_SHORT
        )

        listView.setOnItemClickListener { adapterView, _, position, _ ->
            when (currentShowState) {
                ShowState.NAME -> {
                    currentShowState = ShowState.NAME_SHORT
                    listView.adapter = FilesListAdapter(
                        this, myFileList.myFilesNamesShort, myFileList.myFilesSizes
                    )
                }
                ShowState.NAME_SHORT -> {
                    currentShowState = ShowState.NAME_FULL
                    listView.adapter =
                        FilesListAdapter(this, myFileList.myFilesNamesFull, myFileList.myFilesSizes)
                }
                ShowState.NAME_FULL -> {
                    currentShowState = ShowState.NAME
                    listView.adapter =
                        FilesListAdapter(this, myFileList.myFilesNames, myFileList.myFilesSizes)
                }
            }

            myToast.setText(
                when (currentShowState) {
                    ShowState.NAME -> "Relative Names"
                    ShowState.NAME_SHORT -> "Short Names"
                    ShowState.NAME_FULL -> "Full Path Names"
                }
            )
            myToast.show()

        }
    }

    enum class ShowState {
        NAME, NAME_SHORT, NAME_FULL
    }

    override fun onDestroy() {
        finish()
        super.onDestroy()
    }

}

