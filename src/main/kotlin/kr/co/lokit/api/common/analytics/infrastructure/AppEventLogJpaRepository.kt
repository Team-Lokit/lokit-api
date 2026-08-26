package kr.co.lokit.api.common.analytics.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface AppEventLogJpaRepository : JpaRepository<AppEventLogEntity, Long>
