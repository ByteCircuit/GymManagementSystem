package org.example.t3gymmanagementsystem

import androidx.compose.ui.graphics.ImageBitmap

/** A photo the user picked: the cleaned-up PNG bytes to store, plus a bitmap to preview. */
class PickedPhoto(val bytes: ByteArray, val preview: ImageBitmap)