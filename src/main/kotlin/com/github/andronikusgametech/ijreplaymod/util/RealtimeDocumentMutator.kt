package com.github.andronikusgametech.ijreplaymod.util

import com.github.andronikusgametech.ijreplaymod.stopper.ReplayStopFlag
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Date

class RealtimeDocumentMutator(
    private val currentDocument: Document,
    private val project: Project,
    private val primaryCaret: Caret,
    private val currentVirtualFile: VirtualFile,
    private val scrollingModel: ScrollingModel,
    delayType: String,
    private val delay: Int
): IDocumentMutator, ReplayStopFlag() {

    private val delayType: DelayType

    init {
        this.delayType = DelayType.values().first { type -> type.typeLabel.equals(delayType, true) }
    }

    override fun setText(completeText: String) {
        if (isStopped()) {
            return
        }

        var semaphore = 1

        WriteCommandAction.runWriteCommandAction(project) {
            currentDocument.setText(completeText)
            semaphore = 0
        }

        while (semaphore != 0 && !isStopped()) {}
    }

    override fun deleteSegment(minimumPosition: Int, maximumPosition: Int) {
        var currentPosition = maximumPosition

        // Determine line and column
        val currentText = currentDocument.text
        var lineIncrement = currentText.substring(0, currentPosition).count { character -> character == '\n' }
        var columnIncrement = currentPosition - 1
        if (lineIncrement != 0) {
            columnIncrement -= currentText.substring(0, currentPosition).lastIndexOf('\n')
        }

        var lastWriteTime = Date()
        var semaphore = 0

        while (currentPosition > minimumPosition && !isStopped()) {
            if (semaphore == 0 && delayType.converter.apply(Date().time - lastWriteTime.time) >= delay) {
                semaphore = 1

                val indexOfDelete = currentPosition - 1
                if (currentText[indexOfDelete] == '\n') {
                    lineIncrement--
                    columnIncrement = currentPosition
                    if (lineIncrement != 0) {
                        columnIncrement -= 1
                        columnIncrement -= currentText.substring(0, indexOfDelete).lastIndexOf('\n')
                    }
                }

                WriteCommandAction.runWriteCommandAction(project) {
                    currentDocument.deleteString(indexOfDelete, currentPosition)
                    primaryCaret.moveToLogicalPosition(
                        LogicalPosition(lineIncrement, columnIncrement)
                    )
                    primaryCaret.moveToVisualPosition(
                        VisualPosition(lineIncrement, columnIncrement)
                    )
                    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    semaphore = 0
                    lastWriteTime = Date()
                    currentVirtualFile.refresh(false, false)
                }

                if (currentText[indexOfDelete] != '\n') {
                    columnIncrement--
                }
                currentPosition--
            }
        }
    }

    override fun writeSegment(text: String, startingPosition: Int) {
        var textIncrement = 0
        var currentPosition = startingPosition

        // Determine line and column
        val currentText = currentDocument.text
        var lineIncrement = currentText.substring(0, startingPosition).count { character -> character == '\n' }
        var columnIncrement = startingPosition
        if (lineIncrement != 0) {
            columnIncrement -= 1
            columnIncrement -= currentText.substring(0, startingPosition).lastIndexOf('\n')
        }

        var lastWriteTime = Date()
        var semaphore = 0

        while (textIncrement < text.length && !isStopped()) {
            if (semaphore == 0 && delayType.converter.apply(Date().time - lastWriteTime.time) >= delay) {
                semaphore = 1

                if (text[textIncrement] == '\n') {
                    columnIncrement = 0
                    lineIncrement++
                }
                WriteCommandAction.runWriteCommandAction(project) {
                    currentDocument.insertString(currentPosition, "${text[textIncrement]}")
                    var additionalColumnOffset = if (text[textIncrement] != '\n') 1 else 0
                    primaryCaret.moveToLogicalPosition(
                        LogicalPosition(lineIncrement, columnIncrement + additionalColumnOffset)
                    )
                    primaryCaret.moveToVisualPosition(
                        VisualPosition(lineIncrement, columnIncrement + additionalColumnOffset)
                    )
                    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    semaphore = 0
                    lastWriteTime = Date()
                    currentVirtualFile.refresh(false, false)
                }

                if (text[textIncrement] != '\n') {
                    columnIncrement++
                }
                currentPosition++
                textIncrement++
            }
        }
    }
}