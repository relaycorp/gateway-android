package tech.relaycorp.gateway.data.repos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tech.relaycorp.gateway.data.model.MessageId
import tech.relaycorp.gateway.data.model.PrivateMessageAddress
import tech.relaycorp.gateway.data.model.StoredParcel

@Dao
interface ParcelRepository {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: StoredParcel)

    @Delete
    suspend fun delete(message: StoredParcel)

    @Query("SELECT * FROM Parcel")
    fun observeAll(): Flow<List<StoredParcel>>

    @Query("SELECT * FROM Parcel WHERE senderAddress = :senderAddress AND messageId = :messageId LIMIT 1")
    fun get(senderAddress: PrivateMessageAddress, messageId: MessageId): StoredParcel
}
