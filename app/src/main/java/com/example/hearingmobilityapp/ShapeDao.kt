package com.example.hearingmobilityapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShapeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShapes(shapes: List<ShapeEntity>)

    @Query("SELECT * FROM shapes")
    suspend fun getAllShapes(): List<ShapeEntity>

    @Query("SELECT * FROM shapes WHERE shape_id = :shapeId ORDER BY shape_pt_sequence")
    suspend fun getShapePoints(shapeId: String): List<ShapeEntity>
}
