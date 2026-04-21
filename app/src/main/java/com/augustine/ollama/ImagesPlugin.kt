package com.augustine.ollama

import android.content.Context
import android.graphics.drawable.Drawable
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.ImageProps
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.Target

class ImagesPlugin(private val context: Context) : AbstractMarkwonPlugin() {

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.asyncDrawableLoader(CoilAsyncLoader(context))
    }

    private class CoilAsyncLoader(val context: Context) : AsyncDrawableLoader() {
        private val imageLoader = ImageLoader(context)

        override fun load(drawable: AsyncDrawable) {
            val request = ImageRequest.Builder(context)
                .data(drawable.destination)
                .target(object : Target {
                    override fun onSuccess(result: Drawable) {
                        drawable.result = result
                    }
                })
                .build()
            imageLoader.enqueue(request)
        }

        override fun cancel(drawable: AsyncDrawable) {
            // Coil handles cancellation internally via the target
        }

        override fun placeholder(drawable: AsyncDrawable): Drawable? = null
    }

    companion object {
        fun create(context: Context) = ImagesPlugin(context)
    }
}