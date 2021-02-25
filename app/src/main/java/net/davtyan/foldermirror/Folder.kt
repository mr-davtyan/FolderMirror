package net.davtyan.foldermirror

import android.net.Uri

class Folder(myFolderPathFromPrefs: Uri) {
    var myFolderPath = myFolderPathFromPrefs
    var myFileList = mutableMapOf<String, FileParameters>()
}