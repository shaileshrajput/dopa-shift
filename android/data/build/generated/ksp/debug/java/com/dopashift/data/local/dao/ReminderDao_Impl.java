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
import com.dopashift.data.local.entity.LocalReminder;
import java.lang.Class;
import java.lang.Exception;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ReminderDao_Impl implements ReminderDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalReminder> __insertionAdapterOfLocalReminder;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public ReminderDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalReminder = new EntityInsertionAdapter<LocalReminder>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `reminders` (`id`,`userId`,`entityType`,`entityId`,`scheduledTime`,`scheduledDate`,`recurrenceJson`,`conditionType`,`escalationIntervalMinutes`,`maxEscalations`,`currentEscalationCount`,`isActive`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalReminder entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getUserId());
        statement.bindString(3, entity.getEntityType());
        statement.bindString(4, entity.getEntityId());
        statement.bindString(5, entity.getScheduledTime());
        if (entity.getScheduledDate() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getScheduledDate());
        }
        if (entity.getRecurrenceJson() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getRecurrenceJson());
        }
        if (entity.getConditionType() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getConditionType());
        }
        statement.bindLong(9, entity.getEscalationIntervalMinutes());
        statement.bindLong(10, entity.getMaxEscalations());
        statement.bindLong(11, entity.getCurrentEscalationCount());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindLong(13, entity.getCreatedAt());
        statement.bindLong(14, entity.getUpdatedAt());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM reminders WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final LocalReminder reminder, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalReminder.insert(reminder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object findById(final String id, final Continuation<? super LocalReminder> $completion) {
    final String _sql = "SELECT * FROM reminders WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalReminder>() {
      @Override
      @Nullable
      public LocalReminder call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfScheduledTime = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledTime");
          final int _cursorIndexOfScheduledDate = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledDate");
          final int _cursorIndexOfRecurrenceJson = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrenceJson");
          final int _cursorIndexOfConditionType = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionType");
          final int _cursorIndexOfEscalationIntervalMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "escalationIntervalMinutes");
          final int _cursorIndexOfMaxEscalations = CursorUtil.getColumnIndexOrThrow(_cursor, "maxEscalations");
          final int _cursorIndexOfCurrentEscalationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "currentEscalationCount");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final LocalReminder _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpScheduledTime;
            _tmpScheduledTime = _cursor.getString(_cursorIndexOfScheduledTime);
            final String _tmpScheduledDate;
            if (_cursor.isNull(_cursorIndexOfScheduledDate)) {
              _tmpScheduledDate = null;
            } else {
              _tmpScheduledDate = _cursor.getString(_cursorIndexOfScheduledDate);
            }
            final String _tmpRecurrenceJson;
            if (_cursor.isNull(_cursorIndexOfRecurrenceJson)) {
              _tmpRecurrenceJson = null;
            } else {
              _tmpRecurrenceJson = _cursor.getString(_cursorIndexOfRecurrenceJson);
            }
            final String _tmpConditionType;
            if (_cursor.isNull(_cursorIndexOfConditionType)) {
              _tmpConditionType = null;
            } else {
              _tmpConditionType = _cursor.getString(_cursorIndexOfConditionType);
            }
            final int _tmpEscalationIntervalMinutes;
            _tmpEscalationIntervalMinutes = _cursor.getInt(_cursorIndexOfEscalationIntervalMinutes);
            final int _tmpMaxEscalations;
            _tmpMaxEscalations = _cursor.getInt(_cursorIndexOfMaxEscalations);
            final int _tmpCurrentEscalationCount;
            _tmpCurrentEscalationCount = _cursor.getInt(_cursorIndexOfCurrentEscalationCount);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new LocalReminder(_tmpId,_tmpUserId,_tmpEntityType,_tmpEntityId,_tmpScheduledTime,_tmpScheduledDate,_tmpRecurrenceJson,_tmpConditionType,_tmpEscalationIntervalMinutes,_tmpMaxEscalations,_tmpCurrentEscalationCount,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt);
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

  @Override
  public Object findActiveByUserId(final String userId,
      final Continuation<? super List<LocalReminder>> $completion) {
    final String _sql = "SELECT * FROM reminders WHERE userId = ? AND isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalReminder>>() {
      @Override
      @NonNull
      public List<LocalReminder> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfScheduledTime = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledTime");
          final int _cursorIndexOfScheduledDate = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledDate");
          final int _cursorIndexOfRecurrenceJson = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrenceJson");
          final int _cursorIndexOfConditionType = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionType");
          final int _cursorIndexOfEscalationIntervalMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "escalationIntervalMinutes");
          final int _cursorIndexOfMaxEscalations = CursorUtil.getColumnIndexOrThrow(_cursor, "maxEscalations");
          final int _cursorIndexOfCurrentEscalationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "currentEscalationCount");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalReminder> _result = new ArrayList<LocalReminder>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalReminder _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpScheduledTime;
            _tmpScheduledTime = _cursor.getString(_cursorIndexOfScheduledTime);
            final String _tmpScheduledDate;
            if (_cursor.isNull(_cursorIndexOfScheduledDate)) {
              _tmpScheduledDate = null;
            } else {
              _tmpScheduledDate = _cursor.getString(_cursorIndexOfScheduledDate);
            }
            final String _tmpRecurrenceJson;
            if (_cursor.isNull(_cursorIndexOfRecurrenceJson)) {
              _tmpRecurrenceJson = null;
            } else {
              _tmpRecurrenceJson = _cursor.getString(_cursorIndexOfRecurrenceJson);
            }
            final String _tmpConditionType;
            if (_cursor.isNull(_cursorIndexOfConditionType)) {
              _tmpConditionType = null;
            } else {
              _tmpConditionType = _cursor.getString(_cursorIndexOfConditionType);
            }
            final int _tmpEscalationIntervalMinutes;
            _tmpEscalationIntervalMinutes = _cursor.getInt(_cursorIndexOfEscalationIntervalMinutes);
            final int _tmpMaxEscalations;
            _tmpMaxEscalations = _cursor.getInt(_cursorIndexOfMaxEscalations);
            final int _tmpCurrentEscalationCount;
            _tmpCurrentEscalationCount = _cursor.getInt(_cursorIndexOfCurrentEscalationCount);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalReminder(_tmpId,_tmpUserId,_tmpEntityType,_tmpEntityId,_tmpScheduledTime,_tmpScheduledDate,_tmpRecurrenceJson,_tmpConditionType,_tmpEscalationIntervalMinutes,_tmpMaxEscalations,_tmpCurrentEscalationCount,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object findByEntityId(final String entityId,
      final Continuation<? super List<LocalReminder>> $completion) {
    final String _sql = "SELECT * FROM reminders WHERE entityId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, entityId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalReminder>>() {
      @Override
      @NonNull
      public List<LocalReminder> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfScheduledTime = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledTime");
          final int _cursorIndexOfScheduledDate = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledDate");
          final int _cursorIndexOfRecurrenceJson = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrenceJson");
          final int _cursorIndexOfConditionType = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionType");
          final int _cursorIndexOfEscalationIntervalMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "escalationIntervalMinutes");
          final int _cursorIndexOfMaxEscalations = CursorUtil.getColumnIndexOrThrow(_cursor, "maxEscalations");
          final int _cursorIndexOfCurrentEscalationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "currentEscalationCount");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalReminder> _result = new ArrayList<LocalReminder>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalReminder _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpScheduledTime;
            _tmpScheduledTime = _cursor.getString(_cursorIndexOfScheduledTime);
            final String _tmpScheduledDate;
            if (_cursor.isNull(_cursorIndexOfScheduledDate)) {
              _tmpScheduledDate = null;
            } else {
              _tmpScheduledDate = _cursor.getString(_cursorIndexOfScheduledDate);
            }
            final String _tmpRecurrenceJson;
            if (_cursor.isNull(_cursorIndexOfRecurrenceJson)) {
              _tmpRecurrenceJson = null;
            } else {
              _tmpRecurrenceJson = _cursor.getString(_cursorIndexOfRecurrenceJson);
            }
            final String _tmpConditionType;
            if (_cursor.isNull(_cursorIndexOfConditionType)) {
              _tmpConditionType = null;
            } else {
              _tmpConditionType = _cursor.getString(_cursorIndexOfConditionType);
            }
            final int _tmpEscalationIntervalMinutes;
            _tmpEscalationIntervalMinutes = _cursor.getInt(_cursorIndexOfEscalationIntervalMinutes);
            final int _tmpMaxEscalations;
            _tmpMaxEscalations = _cursor.getInt(_cursorIndexOfMaxEscalations);
            final int _tmpCurrentEscalationCount;
            _tmpCurrentEscalationCount = _cursor.getInt(_cursorIndexOfCurrentEscalationCount);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalReminder(_tmpId,_tmpUserId,_tmpEntityType,_tmpEntityId,_tmpScheduledTime,_tmpScheduledDate,_tmpRecurrenceJson,_tmpConditionType,_tmpEscalationIntervalMinutes,_tmpMaxEscalations,_tmpCurrentEscalationCount,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Flow<List<LocalReminder>> observeActiveByUserId(final String userId) {
    final String _sql = "SELECT * FROM reminders WHERE userId = ? AND isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"reminders"}, new Callable<List<LocalReminder>>() {
      @Override
      @NonNull
      public List<LocalReminder> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfScheduledTime = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledTime");
          final int _cursorIndexOfScheduledDate = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledDate");
          final int _cursorIndexOfRecurrenceJson = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrenceJson");
          final int _cursorIndexOfConditionType = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionType");
          final int _cursorIndexOfEscalationIntervalMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "escalationIntervalMinutes");
          final int _cursorIndexOfMaxEscalations = CursorUtil.getColumnIndexOrThrow(_cursor, "maxEscalations");
          final int _cursorIndexOfCurrentEscalationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "currentEscalationCount");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalReminder> _result = new ArrayList<LocalReminder>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalReminder _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpScheduledTime;
            _tmpScheduledTime = _cursor.getString(_cursorIndexOfScheduledTime);
            final String _tmpScheduledDate;
            if (_cursor.isNull(_cursorIndexOfScheduledDate)) {
              _tmpScheduledDate = null;
            } else {
              _tmpScheduledDate = _cursor.getString(_cursorIndexOfScheduledDate);
            }
            final String _tmpRecurrenceJson;
            if (_cursor.isNull(_cursorIndexOfRecurrenceJson)) {
              _tmpRecurrenceJson = null;
            } else {
              _tmpRecurrenceJson = _cursor.getString(_cursorIndexOfRecurrenceJson);
            }
            final String _tmpConditionType;
            if (_cursor.isNull(_cursorIndexOfConditionType)) {
              _tmpConditionType = null;
            } else {
              _tmpConditionType = _cursor.getString(_cursorIndexOfConditionType);
            }
            final int _tmpEscalationIntervalMinutes;
            _tmpEscalationIntervalMinutes = _cursor.getInt(_cursorIndexOfEscalationIntervalMinutes);
            final int _tmpMaxEscalations;
            _tmpMaxEscalations = _cursor.getInt(_cursorIndexOfMaxEscalations);
            final int _tmpCurrentEscalationCount;
            _tmpCurrentEscalationCount = _cursor.getInt(_cursorIndexOfCurrentEscalationCount);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalReminder(_tmpId,_tmpUserId,_tmpEntityType,_tmpEntityId,_tmpScheduledTime,_tmpScheduledDate,_tmpRecurrenceJson,_tmpConditionType,_tmpEscalationIntervalMinutes,_tmpMaxEscalations,_tmpCurrentEscalationCount,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
