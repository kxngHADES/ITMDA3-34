package com.swiftq.staff

import com.swiftq.staff.data.remote.ServicePointsRepository
import com.swiftq.staff.data.remote.SwiftQApiService
import java.lang.reflect.Proxy

// An API stand-in for tests that never touch the network.
object FakeApi {

    val unused: SwiftQApiService = Proxy.newProxyInstance(
        SwiftQApiService::class.java.classLoader,
        arrayOf(SwiftQApiService::class.java)
    ) { _, method, _ ->
        throw UnsupportedOperationException("${method.name} is not used in this test")
    } as SwiftQApiService

    val unusedServicePoints = ServicePointsRepository(unused)
}
