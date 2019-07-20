package com.ukdev.carcadasalborghetti.repository

import android.content.Context
import android.content.res.Resources
import com.ukdev.carcadasalborghetti.R
import com.ukdev.carcadasalborghetti.model.Carcada

class CarcadaRepositoryImpl(context: Context) : CarcadaRepository(context) {

    override fun getCarcadas(): List<Carcada> {
        with(context.resources) {
            val titles = getStringArray(R.array.titles)
            val lengths = getStringArray(R.array.lengths)
            val audios = getAudios(this)

            val rawList = arrayListOf<Carcada>().apply {
                titles.forEachIndexed { index, title ->
                    add(Carcada(title, lengths[index], index + 1, audios[index]))
                }
            }

            return rawList.sortedBy { it.title.split(".").last().trim() }
                    .apply {
                        forEachIndexed { index, carcada ->
                            carcada.position = index + 1
                        }
                    }
        }
    }

    private fun getAudios(resources: Resources): IntArray {
        val typedArray = resources.obtainTypedArray(R.array.audios)
        val audios = IntArray(typedArray.length()).also {
            it.forEachIndexed { index, _ ->
                it[index] = typedArray.getResourceId(index, 0)
            }
        }
        typedArray.recycle()

        return audios
    }

}