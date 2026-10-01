package com.aistudio.escala

import android.app.Application
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase

class EscalaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            SQLiteDatabase.loadLibs(this)
            Log.d("EscalaApplication", "SQLCipher inicializado com sucesso.")
        } catch (e: Throwable) {
            Log.e("EscalaApplication", "Falha ao inicializar bibliotecas SQLCipher", e)
        }
    }
}
