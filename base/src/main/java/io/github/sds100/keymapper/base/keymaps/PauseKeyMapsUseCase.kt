package io.github.sds100.keymapper.base.keymaps

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.sds100.keymapper.data.Keys
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import io.github.sds100.keymapper.system.media.MediaAdapter
import io.github.sds100.keymapper.system.ringtones.RingtoneAdapter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class PauseKeyMapsUseCaseImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceRepository: PreferenceRepository,
    private val mediaAdapter: MediaAdapter,
    private val ringtoneAdapter: RingtoneAdapter,
) : PauseKeyMapsUseCase {

    private val mainHandler = Handler(Looper.getMainLooper())

    override val isPaused: Flow<Boolean> =
        preferenceRepository.get(Keys.mappingsPaused).map { it ?: false }

    private fun showToast(text: String) {
        mainHandler.post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun pause() {
        preferenceRepository.set(Keys.mappingsPaused, true)
        mediaAdapter.stopFileMedia()
        ringtoneAdapter.stopPlaying()
        showToast("Botones PAUSADOS (normales)")
        Timber.d("Pause mappings")
    }

    override fun resume() {
        preferenceRepository.set(Keys.mappingsPaused, false)
        showToast("Botones ACTIVADOS (tocan pantalla)")
        Timber.d("Resume mappings")
    }
}

interface PauseKeyMapsUseCase {
    val isPaused: Flow<Boolean>
    fun pause()
    fun resume()
}
