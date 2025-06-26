package com.miu.meditationapp.helper

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.miu.meditationapp.databases.SongEntity

object DialogHelper {
    fun showEditSongDialog(
        context: Context,
        song: SongEntity,
        onSave: (String) -> Unit,
        rootView: View
    ) {
        val editText = EditText(context).apply {
            setText(song.title)
            selectAll()
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Edit Song Title")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != song.title) {
                    onSave(newTitle)
                    Snackbar.make(rootView, "Title updated", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showDeleteConfirmationDialog(
        context: Context,
        song: SongEntity,
        onDelete: () -> Unit,
        rootView: View
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete '${song.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                onDelete()
                Snackbar.make(rootView, "Deleted: ${song.title}", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showUploadProgressDialog(
        context: Context,
        title: String = "Uploading Admin Song",
        message: String = "Starting upload..."
    ): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
    }

    fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
} 