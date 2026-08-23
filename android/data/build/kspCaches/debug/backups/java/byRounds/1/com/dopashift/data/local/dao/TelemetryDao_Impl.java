package com.dopashift.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dopashift.data.local.entity.LocalTelemetryEvent;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TelemetryDao_Impl implements TelemetryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalTelemetryEvent> __insertionAdapterOfLocalTelemetryEvent;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public TelemetryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalTelemetryEvent = new EntityInsertionAdapter<LocalTelemetryEvent>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `telemetry_events` (`id`,`appPackageName`,`foregroundSeconds`,`date`,`recordedAt`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalTelemetryEvent entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAppPackageName());
        statement.bindLong(3, entity.getForegroundSeconds());
        statement.bindString(4, entity.getDate());
        statement.bindLong(5, entity.getRecordedAt());
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM telemetry_events WHERE date < ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final LocalTelemetryEvent event,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalTelemetryEvent.insert(event);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final String cutoffDate,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, cutoffDate);
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
  public Object findByDate(final String date,
      final Continuation<? super List<LocalTelemetryEvent>> $completion) {
    final String _sql = "SELECT * FROM telemetry_events WHERE date = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, date);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalTelemetryEvent>>() {
      @Override
      @NonNull
      public List<LocalTelemetryEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAppPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "appPackageName");
          final int _cursorIndexOfForegroundSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "foregroundSeconds");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfRecordedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "recordedAt");
          final List<LocalTelemetryEvent> _result = new ArrayList<LocalTelemetryEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalTelemetryEvent _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAppPackageName;
            _tmpAppPackageName = _cursor.getString(_cursorIndexOfAppPackageName);
            final long _tmpForegroundSeconds;
            _tmpForegroundSeconds = _cursor.getLong(_cursorIndexOfForegroundSeconds);
            final String _tmpDate;
            _tmpDate = _cursor.getString(_cursorIndexOfDate);
            final long _tmpRecordedAt;
            _tmpRecordedAt = _cursor.getLong(_cursorIndexOfRecordedAt);
            _item = new LocalTelemetryEvent(_tmpId,_tmpAppPackageName,_tmpForegroundSeconds,_tmpDate,_tmpRecordedAt);
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
  public Object getTotalForegroundSeconds(final String date, final String packageName,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT SUM(foregroundSeconds) FROM telemetry_events WHERE date = ? AND appPackageName = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, date);
    _argIndex = 2;
    _statement.bindString(_argIndex, packageName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final Long _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(0);
            }
            _result = _tmp;
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
