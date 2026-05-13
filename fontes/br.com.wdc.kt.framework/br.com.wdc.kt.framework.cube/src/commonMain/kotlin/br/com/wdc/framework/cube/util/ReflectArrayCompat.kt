package br.com.wdc.framework.cube.util

/**
 * Emulação dos métodos de `java.lang.reflect.Array` sem uso de reflection.
 * Compatível com TeaVM e outros AOT compilers que não suportam reflection.
 */
object ReflectArrayCompat {

    fun getLength(array: Any): Int = when (array) {
        is Array<*> -> array.size
        is IntArray -> array.size
        is LongArray -> array.size
        is DoubleArray -> array.size
        is FloatArray -> array.size
        is ByteArray -> array.size
        is ShortArray -> array.size
        is CharArray -> array.size
        is BooleanArray -> array.size
        else -> throw IllegalArgumentException("Not an array: ${array::class.simpleName}")
    }

    fun get(array: Any, index: Int): Any? = when (array) {
        is Array<*> -> array[index]
        is IntArray -> array[index]
        is LongArray -> array[index]
        is DoubleArray -> array[index]
        is FloatArray -> array[index]
        is ByteArray -> array[index]
        is ShortArray -> array[index]
        is CharArray -> array[index]
        is BooleanArray -> array[index]
        else -> throw IllegalArgumentException("Not an array: ${array::class.simpleName}")
    }

    fun set(array: Any, index: Int, value: Any?) {
        when (array) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                (array as Array<Any?>)[index] = value
            }
            is IntArray -> array[index] = value as Int
            is LongArray -> array[index] = value as Long
            is DoubleArray -> array[index] = value as Double
            is FloatArray -> array[index] = value as Float
            is ByteArray -> array[index] = value as Byte
            is ShortArray -> array[index] = value as Short
            is CharArray -> array[index] = value as Char
            is BooleanArray -> array[index] = value as Boolean
            else -> throw IllegalArgumentException("Not an array: ${array::class.simpleName}")
        }
    }
}
