package com.dopashift.data.remote

/**
 * Provides access to the auth-specific API without going through
 * the AuthInterceptor, breaking the circular dependency between
 * the interceptor and the token refresh call.
 */
interface AuthApiProvider {
    fun getAuthApi(): DopaShiftApi
}
