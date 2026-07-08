package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantException
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

internal class HomeAssistantAuthInterceptor @Inject constructor(
    private val provider: HomeAssistantConfigProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val config = provider.current() ?: throw HomeAssistantException.NotConfigured
        return chain.proceed(authorize(chain.request(), config))
    }
}
