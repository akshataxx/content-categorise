package com.app.categorise.data.repository

import com.app.categorise.data.entity.RefreshTokenEntity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*


interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity?, UUID?> {
    fun findByToken(token: String?): Optional<RefreshTokenEntity?>?
    fun deleteByUserId(userId: UUID?)
}