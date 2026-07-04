package com.onhand.llm

import android.app.Application
import android.content.Context

class OnHandApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as OnHandApp).container
    }
}
