package com.stellarelite.grouphub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BusinessEntity(
    val id: String,
    val name: String,
    @SerialName("business_industry") val businessIndustry: String? = null,
)

@Serializable
data class CashflowRow(
    @SerialName("flow_type") val flowType: String? = null,
    val amount: Double = 0.0,
)

@Serializable
data class OwnerDrawingRow(
    @SerialName("amount_myr") val amountMyr: Double = 0.0,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
)
