package io.infra.structure.schedule.admin.persistence

import com.mybatisflex.kotlin.extensions.condition.and
import com.mybatisflex.kotlin.extensions.db.deleteWith
import com.mybatisflex.kotlin.extensions.db.update
import com.mybatisflex.kotlin.extensions.kproperty.column
import com.mybatisflex.kotlin.extensions.kproperty.eq
import com.mybatisflex.kotlin.extensions.kproperty.ge
import com.mybatisflex.kotlin.extensions.kproperty.lt
import com.mybatisflex.kotlin.extensions.mapper.query
import io.infra.structure.schedule.core.ExecutorAddresses
import io.infra.structure.schedule.model.ExecutorAddressMode
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.admin.persistence.entity.ScheduleExecutorEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleExecutorRegistryEntity
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleExecutorMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleExecutorRegistryMapper
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository

/** 基于 MyBatis-Flex 的执行器心跳与多地址注册仓储。 */
class FlexExecutorHeartbeatRepository(
    private val executorMapper: ScheduleExecutorMapper,
    private val registryMapper: ScheduleExecutorRegistryMapper,
    private val heartbeatTimeoutMillis: Long
) : ExecutorHeartbeatRepository {
    override fun heartbeat(heartbeat: ExecutorHeartbeat) {
        val now = heartbeat.lastHeartbeatTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        executorMapper.upsertHeartbeat(heartbeat.executorGroup, heartbeat.executorName, now)
        val existing = findExecutorEntityByGroup(heartbeat.executorGroup) ?: return
        val executorId = existing.id ?: return
        if (existing.addressMode == ExecutorAddressMode.AUTO_REGISTER.name) {
            val address = heartbeat.address?.trim()?.takeIf { it.isNotBlank() }
            if (address != null) upsertRegistry(executorId, address, now)
            refreshAutoAddresses(executorId, now)
        }
    }

    override fun save(executor: ExecutorHeartbeat): ExecutorHeartbeat {
        val existing = executor.id.takeIf { it > 0 }?.let(executorMapper::selectOneById)
            ?: findExecutorEntityByGroup(executor.executorGroup)
        val now = System.currentTimeMillis()
        val normalizedAddress = when (executor.addressMode) {
            ExecutorAddressMode.MANUAL -> ExecutorAddresses.format(ExecutorAddresses.parse(executor.address))
            ExecutorAddressMode.AUTO_REGISTER -> existing?.address
        }
        val entity = ScheduleExecutorEntity(
            id = existing?.id,
            executorGroup = executor.executorGroup,
            executorName = executor.executorName,
            address = normalizedAddress,
            addressMode = executor.addressMode.name,
            status = executor.status.name,
            lastHeartbeatTime = executor.lastHeartbeatTime.takeIf { it > 0 } ?: existing?.lastHeartbeatTime ?: now,
            createTime = existing?.createTime ?: now,
            updateTime = now
        )
        if (existing == null) {
            executorMapper.insert(entity)
        } else {
            executorMapper.update(entity)
        }
        val savedId = entity.id ?: findExecutorEntityByGroup(executor.executorGroup)?.id
            ?: error("执行器保存后不存在: ${executor.executorGroup}")
        if (executor.addressMode == ExecutorAddressMode.MANUAL) {
            clearRegistry(savedId)
        } else if (existing?.addressMode == ExecutorAddressMode.MANUAL.name) {
            // 从手动切到自动时清空旧配置地址展示，等待心跳回填。
            update<ScheduleExecutorEntity> {
                ScheduleExecutorEntity::address set null
                where(ScheduleExecutorEntity::id eq savedId)
            }
        }
        return requireNotNull(findById(savedId)) { "执行器保存后不存在: $savedId" }
    }

    override fun findById(id: Long): ExecutorHeartbeat? = executorMapper.selectOneById(id)?.toModel()

    override fun findByGroup(executorGroup: String): ExecutorHeartbeat? =
        findExecutorEntityByGroup(executorGroup)?.toModel()

    override fun list(executorGroup: String, now: Long, timeoutMillis: Long): List<ExecutorHeartbeat> =
        executorMapper.query {
            where(
                (ScheduleExecutorEntity::executorGroup eq executorGroup) and
                    (ScheduleExecutorEntity::status eq ExecutorStatus.ENABLED.name) and
                    ((ScheduleExecutorEntity::addressMode eq ExecutorAddressMode.MANUAL.name)
                        .or(ScheduleExecutorEntity::lastHeartbeatTime ge now - timeoutMillis))
            )
        }.map(ScheduleExecutorEntity::toModel)

    override fun listRegistered(executorGroup: String): List<ExecutorHeartbeat> = executorMapper.query {
        where(ScheduleExecutorEntity::executorGroup eq executorGroup)
        orderBy(ScheduleExecutorEntity::id.column, true)
    }.map(ScheduleExecutorEntity::toModel)

    override fun listRegistered(): List<ExecutorHeartbeat> = executorMapper.query {
        orderBy(ScheduleExecutorEntity::id.column, true)
    }.map(ScheduleExecutorEntity::toModel)

    override fun updateStatus(id: Long, status: ExecutorStatus): Boolean = update<ScheduleExecutorEntity> {
        ScheduleExecutorEntity::status set status.name
        where(ScheduleExecutorEntity::id eq id)
    } > 0

    override fun markOffline(executorGroup: String, address: String?): Boolean {
        val existing = findExecutorEntityByGroup(executorGroup) ?: return false
        val executorId = existing.id ?: return false
        val now = System.currentTimeMillis()
        val normalized = address?.trim()?.takeIf { it.isNotBlank() }
        if (existing.addressMode == ExecutorAddressMode.AUTO_REGISTER.name && normalized != null) {
            deleteWith<ScheduleExecutorRegistryEntity> {
                (ScheduleExecutorRegistryEntity::executorId eq executorId) and
                    (ScheduleExecutorRegistryEntity::address eq normalized)
            }
            refreshAutoAddresses(executorId, now)
            return true
        }
        clearRegistry(executorId)
        update<ScheduleExecutorEntity> {
            ScheduleExecutorEntity::lastHeartbeatTime set 0L
            ScheduleExecutorEntity::updateTime set now
            if (existing.addressMode == ExecutorAddressMode.AUTO_REGISTER.name) {
                ScheduleExecutorEntity::address set null
            }
            where(ScheduleExecutorEntity::id eq executorId)
        }
        return true
    }

    override fun listRoutableAddresses(executorId: Long, now: Long, timeoutMillis: Long): List<String> {
        val executor = executorMapper.selectOneById(executorId) ?: return emptyList()
        return when (executor.addressMode) {
            ExecutorAddressMode.MANUAL.name -> ExecutorAddresses.parse(executor.address)
            else -> {
                refreshAutoAddresses(executorId, now, timeoutMillis)
                listAliveRegistryAddresses(executorId, now, timeoutMillis)
            }
        }
    }

    override fun deleteIfUnreferenced(id: Long): Boolean {
        val deleted = executorMapper.deleteIfUnreferenced(id) > 0
        if (deleted) clearRegistry(id)
        return deleted
    }

    private fun upsertRegistry(executorId: Long, address: String, now: Long) {
        registryMapper.upsertRegistry(executorId, address, now)
    }

    private fun refreshAutoAddresses(
        executorId: Long,
        now: Long,
        timeoutMillis: Long = heartbeatTimeoutMillis
    ) {
        deleteWith<ScheduleExecutorRegistryEntity> {
            (ScheduleExecutorRegistryEntity::executorId eq executorId) and
                (ScheduleExecutorRegistryEntity::lastHeartbeatTime lt now - timeoutMillis)
        }
        val alive = listAliveRegistryAddresses(executorId, now, timeoutMillis)
        val latest = registryMapper.query {
            where(ScheduleExecutorRegistryEntity::executorId eq executorId)
            orderBy(ScheduleExecutorRegistryEntity::lastHeartbeatTime.column, false)
            limit(1)
        }.firstOrNull()?.lastHeartbeatTime ?: 0L
        update<ScheduleExecutorEntity> {
            ScheduleExecutorEntity::address set ExecutorAddresses.format(alive)
            ScheduleExecutorEntity::lastHeartbeatTime set latest
            ScheduleExecutorEntity::updateTime set now
            where(ScheduleExecutorEntity::id eq executorId)
        }
    }

    private fun listAliveRegistryAddresses(executorId: Long, now: Long, timeoutMillis: Long): List<String> =
        registryMapper.query {
            where(
                (ScheduleExecutorRegistryEntity::executorId eq executorId) and
                    (ScheduleExecutorRegistryEntity::lastHeartbeatTime ge now - timeoutMillis)
            )
            orderBy(ScheduleExecutorRegistryEntity::address.column, true)
        }.map { it.address }

    private fun clearRegistry(executorId: Long) {
        deleteWith<ScheduleExecutorRegistryEntity> {
            ScheduleExecutorRegistryEntity::executorId eq executorId
        }
    }

    private fun findExecutorEntityByGroup(executorGroup: String): ScheduleExecutorEntity? =
        executorMapper.query {
            where(ScheduleExecutorEntity::executorGroup eq executorGroup)
        }.firstOrNull()
}

private fun ScheduleExecutorEntity.toModel() = ExecutorHeartbeat(
    id = id ?: 0,
    executorGroup = executorGroup,
    executorName = executorName,
    address = address,
    addressMode = ExecutorAddressMode.valueOf(addressMode),
    status = ExecutorStatus.valueOf(status),
    lastHeartbeatTime = lastHeartbeatTime
)
