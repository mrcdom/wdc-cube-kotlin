package br.com.wdc.framework.cube

interface CubeView {

    fun instanceId(): String

    fun update()

    fun release()
}
