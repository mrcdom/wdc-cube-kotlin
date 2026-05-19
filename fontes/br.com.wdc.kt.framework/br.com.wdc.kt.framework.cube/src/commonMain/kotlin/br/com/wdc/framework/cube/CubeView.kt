package br.com.wdc.framework.cube

interface CubeView {

    val instanceId: String

    fun update()

    fun release()
}
