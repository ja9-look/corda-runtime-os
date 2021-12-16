package net.corda.libs.permissions.storage.writer.impl

import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.v5.base.util.contextLogger

class PermissionStorageWriterProcessorImpl(
    private val permissionStorageReader: PermissionStorageReader,
    private val userWriter: UserWriter,
    private val roleWriter: RoleWriter
) : PermissionStorageWriterProcessor {

    private companion object {
        val log = contextLogger()
    }

    override fun onNext(request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>) {
        try {
            val response = when (val permissionRequest = request.request) {
                is CreateUserRequest -> {
                    val avroUser = userWriter.createUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewUser(avroUser)
                    avroUser
                }
                is CreateRoleRequest -> {
                    val avroRole = roleWriter.createRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewRole(avroRole)
                    avroRole
                }
                else -> throw IllegalArgumentException("Received invalid permission request type")
            }
            respFuture.complete(PermissionManagementResponse(response))
        } catch (e: Exception) {
            log.warn(e.message)
            respFuture.completeExceptionally(e)
        }
    }
}