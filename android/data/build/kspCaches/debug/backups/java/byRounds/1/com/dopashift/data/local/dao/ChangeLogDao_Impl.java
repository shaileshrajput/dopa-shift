package com.dopashift.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dopashift.data.local.entity.LocalChangeLogEntry;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ChangeLogDao_Impl implements ChangeLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalChangeLogEntry> __insertionAdapterOfLocalChangeLogEntry;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public ChangeLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalChangeLogEntry = new EntityInsertionAdapter<LocalChangeLogEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `change_log` (`id`,`entityId`,`entityType`,`field`,`value`,`timestamp`,`deviceId`,`userId`,`isSynced`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalChangeLogEntry entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getEntityId());
        statement.bindString(3, entity.getEntityType());
        statement.bindString(4, entity.getField());
        if (entity.getValue() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getValue());
        }
        statement.bindLong(6, entity.getTimestamp());
        statement.bindString(7, entity.getDeviceId());
        statement.bindString(8, entity.getUserId());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(9, _tmp);
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM change_log WHERE timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final LocalChangeLogEntry entry,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalChangeLogEntry.insert(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final long cutoff, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, cutoff);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object findSince(final String userId, final long since,
      final Continuation<? super List<LocalChangeLogEntry>> $completion) {
    final String _sql = "SELECT * FROM change_log WHERE userId = ? AND timestamp > ? ORDER BY timestamp";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, since);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalChangeLogEntry>>() {
      @Override
      @NonNull
      public List<LocalChangeLogEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfField = CursorUtil.getColumnIndexOrThrow(_cursor, "field");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<LocalChangeLogEntry> _result = new ArrayList<LocalChangeLogEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalChangeLogEntry _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpField;
            _tmpField = _cursor.getString(_cursorIndexOfField);
            final String _tmpValue;
            if (_cursor.isNull(_cursorIndexOfValue)) {
              _tmpValue = null;
            } else {
              _tmpValue = _cursor.getString(_cursorIndexOfValue);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new LocalChangeLogEntry(_tmpId,_tmpEntityId,_tmpEntityType,_tmpField,_tmpValue,_tmpTimestamp,_tmpDeviceId,_tmpUserId,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findUnsyncedByUserId(final String userId,
      final Continuation<? super List<LocalChangeLogEntry>> $completion) {
    final String _sql = "SELECT * FROM change_log WHERE userId = ? AND isSynced = 0 ORDER BY timestamp";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalChangeLogEntry>>() {
      @Override
      @NonNull
      public List<LocalChangeLogEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfField = CursorUtil.getColumnIndexOrThrow(_cursor, "field");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<LocalChangeLogEntry> _result = new ArrayList<LocalChangeLogEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalChangeLogEntry _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpField;
            _tmpField = _cursor.getString(_cursorIndexOfField);
            final String _tmpValue;
            if (_cursor.isNull(_cursorIndexOfValue)) {
              _tmpValue = null;
            } else {
              _tmpValue = _cursor.getString(_cursorIndexOfValue);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new LocalChangeLogEntry(_tmpId,_tmpEntityId,_tmpEntityType,_tmpField,_tmpValue,_tmpTimestamp,_tmpDeviceId,_tmpUserId,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LocalChangeLogEntry>> observeUnsyncedByUserId(final String userId) {
    final String _sql = "SELECT * FROM change_log WHERE userId = ? AND isSynced = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"change_log"}, new Callable<List<LocalChangeLogEntry>>() {
      @Override
      @NonNull
      public List<LocalChangeLogEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfField = CursorUtil.getColumnIndexOrThrow(_cursor, "field");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<LocalChangeLogEntry> _result = new ArrayList<LocalChangeLogEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalChangeLogEntry _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpField;
            _tmpField = _cursor.getString(_cursorIndexOfField);
            final String _tmpValue;
            if (_cursor.isNull(_cursorIndexOfValue)) {
              _tmpValue = null;
            } else {
              _tmpValue = _cursor.getString(_cursorIndexOfValue);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new LocalChangeLogEntry(_tmpId,_tmpEntityId,_tmpEntityType,_tmpField,_tmpValue,_tmpTimestamp,_tmpDeviceId,_tmpUserId,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object countByUserId(final String userId, final Continuation<? super Long> $completion) {
    final String _sql = "SELECT COUNT(*) FROM change_log WHERE userId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp;
            _tmp = _cursor.getLong(0);
            _result = _tmp;
          } else {
            _result = 0L;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LocalChangeLogEntry>> observeRecent(final String userId, final int limit,
      final int offset) {
    final String _sql = "SELECT * FROM change_log WHERE userId = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 3;
    _statement.bindLong(_argIndex, offset);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"change_log"}, new Callable<List<LocalChangeLogEntry>>() {
      @Override
      @NonNull
      public List<LocalChangeLogEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfField = CursorUtil.getColumnIndexOrThrow(_cursor, "field");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<LocalChangeLogEntry> _result = new ArrayList<LocalChangeLogEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalChangeLogEntry _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpField;
            _tmpField = _cursor.getString(_cursorIndexOfField);
            final String _tmpValue;
            if (_cursor.isNull(_cursorIndexOfValue)) {
              _tmpValue = null;
            } else {
              _tmpValue = _cursor.getString(_cursorIndexOfValue);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new LocalChangeLogEntry(_tmpId,_tmpEntityId,_tmpEntityType,_tmpField,_tmpValue,_tmpTimestamp,_tmpDeviceId,_tmpUserId,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object markSynced(final List<String> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE change_log SET isSynced = 1 WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : ids) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
