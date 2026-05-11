package br.com.wdc.framework.cube

interface CubePlace {

    val id: Int

    val placeName: String

    fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter
}
