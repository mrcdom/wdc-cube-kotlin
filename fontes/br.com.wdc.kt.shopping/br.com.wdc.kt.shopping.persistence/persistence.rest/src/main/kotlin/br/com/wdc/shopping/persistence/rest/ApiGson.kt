package br.com.wdc.shopping.persistence.rest

import com.google.gson.Gson

/** Gson dos endpoints de autenticação. As entidades trafegam pelos codecs do domínio, e não por aqui. */
object ApiGson {
    val instance: Gson = Gson()
}
