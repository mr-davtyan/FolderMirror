package net.davtyan.foldermirror.fileslist

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import net.davtyan.foldermirror.databinding.FilesListBinding
import net.davtyan.foldermirror.myFilesToCopy
import net.davtyan.foldermirror.myFilesToDelete


class FilesListView : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = FilesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fileListTitle: TextView = binding.listViewTitle
        val buttonClose: Button = binding.buttonCloseFileList

        val myFileList = if (intent.getBooleanExtra("delete", false)) {
            myFilesToDelete
        } else {
            myFilesToCopy
        }

        var currentShowState = ShowState.NAME

        binding.listView.adapter =
            FilesListAdapter(this, myFileList.myFilesNames, myFileList.myFilesSizes)

        fileListTitle.text = myFileList.myTitle
        buttonClose.setOnClickListener {
            finish()
        }

        val myToast: Toast = Toast.makeText(
            this, "",
            Toast.LENGTH_SHORT
        )

        binding.listView.setOnItemClickListener { _, _, _, _ ->
            when (currentShowState) {
                ShowState.NAME -> {
                    currentShowState = ShowState.NAME_SHORT
                    binding.listView.adapter = FilesListAdapter(
                        this, myFileList.myFilesNamesShort, myFileList.myFilesSizes
                    )
                }
                ShowState.NAME_SHORT -> {
                    currentShowState = ShowState.NAME_FULL
                    binding.listView.adapter =
                        FilesListAdapter(this, myFileList.myFilesNamesFull, myFileList.myFilesSizes)
                }
                ShowState.NAME_FULL -> {
                    currentShowState = ShowState.NAME
                    binding.listView.adapter =
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

