package net.davtyan.foldermirror.fileslist

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import net.davtyan.foldermirror.R

class FilesListAdapter(
    private val context: Activity,
    private val fileName: MutableList<String>,
    private val fileSize: MutableList<String>
) : ArrayAdapter<String>(context, R.layout.file_info, fileName) {

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        var rowView = view
        if (rowView == null) {
            val inflater = context.layoutInflater
            rowView = inflater.inflate(R.layout.file_info, parent, false)
        }

        val fileNameText = rowView?.findViewById(R.id.file_name) as TextView
        val fileSizeText = rowView.findViewById(R.id.file_size) as TextView

        fileNameText.text = fileName[position]
        fileSizeText.text = fileSize[position]

        return rowView
    }
}