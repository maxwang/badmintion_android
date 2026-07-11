package com.badmintonledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Member(val id: String, val name: String, val isGuest: Boolean)

@Serializable
data class Config(val defaultRate: Cents, val defaultPaid: Cents, val defaultCredit: Cents)

@Serializable
data class Contribution(val memberId: String, val amount: Cents)

@Serializable
data class Refill(
    val id: String,
    val date: String,
    val paid: Cents,
    val credit: Cents,
    val contributions: List<Contribution>,
)

@Serializable
data class Payment(val id: String, val memberId: String, val amount: Cents, val date: String)

@Serializable
data class Session(
    val id: String,
    val date: String,
    val hours: Double,
    val rate: Cents,
    val factor: Double,
    val playerIds: List<String>,
)

@Serializable
data class LedgerData(
    val version: Int = 1,
    val members: List<Member> = emptyList(),
    val config: Config =
        Config(
            defaultRate = Cents(2400),
            defaultPaid = Cents(200000),
            defaultCredit = Cents(250000),
        ),
    val refills: List<Refill> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val sessions: List<Session> = emptyList(),
)
