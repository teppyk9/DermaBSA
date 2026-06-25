package com.uninsubria.derma_bsa.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pazienti")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val cognome: String,
    val etaAnni: Long = 0L,
    val dataCreazione: Long = System.currentTimeMillis()
)
