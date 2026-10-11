package com.aistudio.escala

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
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

        inicializarFirebaseAppCheck()
    }

    private fun inicializarFirebaseAppCheck() {
        try {
            FirebaseApp.initializeApp(this)
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            val providerFactory = getAppCheckProviderFactory()
            firebaseAppCheck.installAppCheckProviderFactory(providerFactory)
            Log.d("EscalaApplication", "Firebase App Check inicializado com sucesso via ${providerFactory.javaClass.simpleName}")
        } catch (e: Throwable) {
            Log.w("EscalaApplication", "Não foi possível inicializar o Firebase App Check (Google Services pode estar ausente ou não configurado): ${e.message}")
        }
    }
}


