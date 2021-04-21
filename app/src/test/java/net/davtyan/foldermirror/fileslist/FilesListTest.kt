package net.davtyan.foldermirror.fileslist

import net.davtyan.foldermirror.FileParameters
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesListTest {

    private val t = FilesList("title")

    @Test
    fun getMyFiles() {
        assertEquals(mutableListOf<FileParameters>(), t.myFiles)
    }

    @Test
    fun getMyTitle() {
        assertEquals("title", t.myTitle)
    }

    @Test
    fun convertFileSize() {
        assertEquals("12.0 MB", t.convertFileSize(12548324))
    }
}