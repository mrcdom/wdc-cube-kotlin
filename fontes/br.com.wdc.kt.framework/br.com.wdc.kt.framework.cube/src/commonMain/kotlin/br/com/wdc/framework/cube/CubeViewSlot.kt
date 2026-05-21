package br.com.wdc.framework.cube

fun interface CubeViewSlot {

    suspend fun setView(view: CubeView)
}
