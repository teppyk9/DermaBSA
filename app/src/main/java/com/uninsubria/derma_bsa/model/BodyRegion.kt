package com.uninsubria.derma_bsa.model
data class BodyRegion(
    val id: String,
    val label: String,
    val bsaPercent: Float
)

val ALL_REGIONS = listOf(
    BodyRegion("head",           "Testa",             9f),
    BodyRegion("arm_right",      "Braccio destro",    9f),
    BodyRegion("arm_left",       "Braccio sinistro",  9f),
    BodyRegion("trunk_front",    "Tronco anteriore",  18f),
    BodyRegion("trunk_back",     "Tronco posteriore", 18f),
    BodyRegion("leg_right",      "Gamba destra",      18f),
    BodyRegion("leg_left",       "Gamba sinistra",    18f),
    BodyRegion("genitals",       "Genitali",          1f)
)