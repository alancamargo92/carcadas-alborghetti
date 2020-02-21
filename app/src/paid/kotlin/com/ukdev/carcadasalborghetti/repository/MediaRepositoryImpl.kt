package com.ukdev.carcadasalborghetti.repository

import com.ukdev.carcadasalborghetti.api.DropboxApi
import com.ukdev.carcadasalborghetti.api.provider.ApiProvider
import com.ukdev.carcadasalborghetti.api.requests.MediaRequest
import com.ukdev.carcadasalborghetti.model.*
import com.ukdev.carcadasalborghetti.utils.CrashReportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class MediaRepositoryImpl(
        crashReportManager: CrashReportManager,
        private val apiProvider: ApiProvider
) : MediaRepository(crashReportManager) {

    private val api by lazy { apiProvider.getDropboxService() }

    override suspend fun getMedia(mediaType: MediaType): Result<List<Media>> {
        val dir = if (mediaType == MediaType.AUDIO)
            DropboxApi.DIR_AUDIO
        else
            DropboxApi.DIR_VIDEO
        val request = MediaRequest(dir)

        return try {
            val media = withContext(Dispatchers.IO) {
                api.listMedia(request).entries.sort()
            }
            Success(media)
        } catch (httpException: HttpException) {
            crashReportManager.logException(httpException)
            GenericError
        } catch (ioException: IOException) {
            crashReportManager.logException(ioException)
            NetworkError
        } catch (t: Throwable) {
            crashReportManager.logException(t)
            GenericError
        }
    }

}