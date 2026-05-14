package it.homebudget.app.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM category")
    suspend fun getAllCategoriesSnapshot(): List<Category>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getCategoryById(id: String): Category?

    @Upsert
    suspend fun insertCategory(category: Category)

    @Upsert
    suspend fun insertCategories(categories: List<Category>)

    @Query(
        """
        UPDATE category
        SET name = :name, icon = :icon, color = :color, categoryType = :categoryType
        WHERE id = :id
        """
    )
    suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String
    )

    @Query("UPDATE category SET isArchived = :isArchived WHERE id = :id")
    suspend fun setCategoryArchived(id: String, isArchived: Long)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("DELETE FROM category")
    suspend fun deleteAllCategories()

    @Query("SELECT count(*) FROM category")
    suspend fun countCategories(): Long
}
