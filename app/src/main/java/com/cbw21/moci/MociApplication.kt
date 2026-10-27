package com.cbw21.moci

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MociApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}
