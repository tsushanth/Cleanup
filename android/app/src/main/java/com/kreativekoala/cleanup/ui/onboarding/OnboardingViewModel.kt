package com.kreativekoala.cleanup.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.kreativekoala.cleanup.domain.service.MediaAccessHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val mediaAccessHelper: MediaAccessHelper
) : ViewModel() {

    fun hasFolderAccess(): Boolean = mediaAccessHelper.hasFolderAccess()

    fun saveFolderUri(uri: Uri) {
        mediaAccessHelper.saveFolderUri(uri)
    }
}
