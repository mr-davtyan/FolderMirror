package net.davtyan.foldermirror.fileslist

import net.davtyan.foldermirror.FileParameters

class FilesList(title: String) {
    var myFiles: MutableList<FileParameters> = mutableListOf()
    var myFilesNames: MutableList<String> = mutableListOf()
    var myFilesNamesShort: MutableList<String> = mutableListOf()
    var myFilesNamesFull: MutableList<String> = mutableListOf()
    var myFilesSizes: MutableList<String> = mutableListOf()
    var myTotalFilesCount: Int = 0
    var myTotalFilesSize: Long = 0
    var myTitle: String = title

    fun convertFileSize(fileSize: Long): String {
        return when {
            fileSize > 630000000 -> {
                String.format("%.1f", fileSize.toDouble() / 1024 / 1024 / 1024) + " GB"
            }
            fileSize > 615000 -> {
                String.format("%.1f", fileSize.toDouble() / 1024 / 1024) + " MB"
            }
            fileSize > 600 -> {
                String.format("%.1f", fileSize.toDouble() / 1024) + " KB"
            }
            else -> "$fileSize Bytes"
        }
    }

}

