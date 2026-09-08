package com.vexcompany.samvira.data.network

/**
 * Networking abstraction.
 *
 * The foundation does not yet talk to a backend; this interface exists so that
 * the transport (OkHttp today, potentially a protocol client later) can evolve
 * without coupling the rest of the app to a specific backend implementation.
 *
 * Implementations must:
 *  - never log raw authorization headers or request bodies, and
 *  - fail closed (return [NetworkResult.Failure]) on unexpected input.
 */
interface NetworkClient {
    suspend fun execute(request: NetworkRequest): NetworkResult
}
