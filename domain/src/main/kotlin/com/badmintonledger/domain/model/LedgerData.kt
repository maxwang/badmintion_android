package com.badmintonledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Member(val id: String, val name: String, val isGuest: Boolean)

@Serializable
data class Config(val defaultPaid: Cents, val defaultCredit: Cents)

@Serializable
data class RateChange(val id: String, val date: String, val rate: Cents)

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
    val version: Int = 2,
    val members: List<Member> = emptyList(),
    val config: Config =
        Config(
            defaultPaid = Cents(200000),
            defaultCredit = Cents(250000),
        ),
    val rates: List<RateChange> = listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))),
    val refills: List<Refill> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val sessions: List<Session> = emptyList(),
)
