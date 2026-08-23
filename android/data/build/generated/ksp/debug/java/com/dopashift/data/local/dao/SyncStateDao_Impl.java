package com.dopashift.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dopashift.data.local.entity.LocalSyncState;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SyncStateDao_Impl implements SyncStateDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalSyncState> __insertionAdapterOfLocalSyncState;

  public SyncStateDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalSyncState = new EntityInsertionAdapter<LocalSyncState>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sync_state` (`key`,`lastSyncTimestamp`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalSyncState entity) {
        statement.bindString(1, entity.getKey());
        statement.bindLong(2, entity.getLastSyncTimestamp());
      }
    };
  }

  @Override
  public Object upsert(final LocalSyncState state, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalSyncState.insert(state);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByKey(final String key, final Continuation<? super LocalSyncState> $completion) {
    final String _sql = "SELECT * FROM sync_state WHERE `key` = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalSyncState>() {
      @Override
      @Nullable
      public LocalSyncState call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
          final int _cursorIndexOfLastSyncTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncTimestamp");
          final LocalSyncState _result;
          if (_cursor.moveToFirst()) {
            final String _tmpKey;
            _tmpKey = _cursor.getString(_cursorIndexOfKey);
            final long _tmpLastSyncTimestamp;
            _tmpLastSyncTimestamp = _cursor.getLong(_cursorIndexOfLastSyncTimestamp);
            _result = new LocalSyncState(_tmpKey,_tmpLastSyncTimestamp);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
