package br.com.wdc.framework.cube.util

internal object ArrayUtils {

    private val arrayFactoryMap: Map<Class<*>, (Int) -> Any> = mapOf(
        String::class.java to { len -> arrayOfNulls<String>(len) },
        Byte::class.javaPrimitiveType!! to { len -> ByteArray(len) },
        Short::class.javaPrimitiveType!! to { len -> ShortArray(len) },
        Char::class.javaPrimitiveType!! to { len -> CharArray(len) },
        Int::class.javaPrimitiveType!! to { len -> IntArray(len) },
        Long::class.javaPrimitiveType!! to { len -> LongArray(len) },
        Float::class.javaPrimitiveType!! to { len -> FloatArray(len) },
        Double::class.javaPrimitiveType!! to { len -> DoubleArray(len) },
    )

    fun newInstance(componentType: Class<*>, length: Int): Any {
        val factory = arrayFactoryMap[componentType]
        return if (factory != null) factory(length) else arrayOfNulls<Any>(length)
    }
}
